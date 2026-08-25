package io.getbit.gim.core.connection.auth;

import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.connection.channel.ChannelManager;
import io.getbit.gim.core.routing.UserRouteService;
import io.getbit.gim.core.spi.ImTokenVerifier;
import io.getbit.gim.protocol.codec.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ConnectionAuthHandler.java
 * <p>
 * 首包认证处理器
 * 客户端连接后必须在 AUTH_TIMEOUT 秒内发送 BIND_REQ 完成认证，否则断开连接。
 * 认证成功后将用户信息与通道绑定。
 * <p>
 * 认证流程：
 * 1. 客户端连接 → 等待 BIND_REQ
 * 2. 解析 userId + token + device
 * 3. 校验 Token（通过 ImTokenVerifier SPI）
 * 4. 绑定 ChannelManager（多设备）
 * 5. 回复 BIND_RESP
 *
 * @author gogym
 */
public class ConnectionAuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionAuthHandler.class);

    /**
     * 认证超时时间（秒）
     */
    public static final int AUTH_TIMEOUT = 10;

    /**
     * Channel 属性：是否已认证
     */
    public static final AttributeKey<Boolean> AUTH_KEY = AttributeKey.valueOf("authenticated");

    /**
     * Channel 属性：绑定的用户ID
     */
    public static final AttributeKey<String> USER_ID_KEY = AttributeKey.valueOf("userId");

    /**
     * Channel 属性：设备类型
     */
    public static final AttributeKey<DeviceType> DEVICE_KEY = AttributeKey.valueOf("deviceType");

    /**
     * Channel 属性：设备唯一标识（客户端持久化 UUID）
     * 互踢时用于区分"同一台设备重连"（同 deviceId，静默替换）与"另一台设备顶号"（异 deviceId，立即踢）
     */
    public static final AttributeKey<String> DEVICE_ID_KEY = AttributeKey.valueOf("deviceId");

    /**
     * Channel 属性：被新连接替换标记（仅用于日志区分）
     */
    public static final AttributeKey<Boolean> KICKED_BY_NEW_KEY = AttributeKey.valueOf("kickedByNew");

    private final ImTokenVerifier tokenVerifier;
    private final ChannelManager channelManager;
    private final GimProperties config;
    private final UserRouteService userRouteService;

    public ConnectionAuthHandler(ImTokenVerifier tokenVerifier, ChannelManager channelManager, GimProperties config,
                                 UserRouteService userRouteService) {
        this.tokenVerifier = tokenVerifier;
        this.channelManager = channelManager;
        this.config = config;
        this.userRouteService = userRouteService;
    }

    /**
     * 处理绑定请求（首包认证）
     *
     * @param packet  收到的 Packet（cmd = BIND_REQ）
     * @param channel Netty 通道
     * @return 是否认证成功
     */
    public boolean handleBind(ImProto.Packet packet, Channel channel) {
        try {
            ImProto.BindRequest bindReq = PacketCodec.parseBindRequest(packet);

            String userId = bindReq.getUserId();
            String token = bindReq.getToken();
            String deviceStr = bindReq.getDevice();

            // 1. 参数校验
            if (userId.isEmpty() || token.isEmpty()) {
                logger.warn("绑定失败: userId 或 token 为空, channelId={}", channel.id().asShortText());
                sendBindFail(channel, packet.getSequence(), 401, "userId and token required");
                return false;
            }

            // 2. Token 校验（SPI）
            String tokenUserId = tokenVerifier.verifyAndExtractUserId(token);
            if (tokenUserId == null) {
                logger.warn("绑定失败: token 无效, userId={}", userId);
                sendBindFail(channel, packet.getSequence(), 401, "invalid token");
                return false;
            }

            // 3. userId 比对（防止 token 盗用）
            if (!userId.equals(tokenUserId)) {
                logger.warn("绑定失败: userId 不匹配, reqUserId={}, tokenUserId={}", userId, tokenUserId);
                sendBindFail(channel, packet.getSequence(), 403, "userId mismatch");
                return false;
            }

            // 4. 解析设备类型
            DeviceType device = DeviceType.fromCode(deviceStr);

            // 5. 绑定通道（同设备互踢）
            Channel oldChannel = channelManager.bind(userId, device, channel);

            // 6. 设置 Channel 属性
            channel.attr(AUTH_KEY).set(true);
            channel.attr(USER_ID_KEY).set(userId);
            channel.attr(DEVICE_KEY).set(device);
            channel.attr(DEVICE_ID_KEY).set(bindReq.getDeviceId());

            // 7. 处理旧连接：同设备重连静默替换，异设备顶号立即踢下线
            if (oldChannel != null && oldChannel.isActive()) {
                kickOldChannel(userId, device, bindReq.getDeviceId(), oldChannel);
            }

            // 8. 回复绑定成功
            ImProto.Packet resp = PacketCodec.buildBindResp(packet.getSequence(), config.getServerId());
            channel.writeAndFlush(resp);

            // 9. 注册用户路由
            userRouteService.register(userId);

            logger.info("绑定成功, userId={}, device={}, channelId={}", userId, device, channel.id().asShortText());
            return true;

        } catch (Exception e) {
            logger.error("绑定处理异常, channelId={}", channel.id().asShortText(), e);
            sendBindFail(channel, packet.getSequence(), 500, "internal error");
            return false;
        }
    }

    /**
     * 处理已认证连接上的重复绑定请求（客户端断线重连/原地重绑）
     * <p>
     * 客户端在已认证连接上重发 BIND_REQ，通常是重连流程的一部分：
     * 1. 回复 BIND_RESP 确认连接仍有效，避免客户端因等待响应超时而新建连接触发互踢；
     * 2. 续期用户路由，避免路由过期导致消息无法投递。
     *
     * @param packet  重复的 BIND_REQ 包
     * @param channel 已认证通道
     */
    public void handleRebind(ImProto.Packet packet, Channel channel) {
        String userId = getUserId(channel);
        if (userId == null) {
            return;
        }

        // 防御：重复绑定携带的 userId 必须与当前连接一致
        try {
            ImProto.BindRequest bindReq = PacketCodec.parseBindRequest(packet);
            if (!userId.equals(bindReq.getUserId())) {
                logger.warn("已认证连接重复绑定 userId 不一致, 忽略: channelId={}, current={}, req={}",
                        channel.id().asShortText(), userId, bindReq.getUserId());
                return;
            }
        } catch (Exception e) {
            logger.warn("已认证连接重复绑定解析失败, 忽略: channelId={}", channel.id().asShortText(), e);
            return;
        }

        // 续期用户路由，防止路由过期导致消息无法投递
        userRouteService.register(userId);

        // 回复 BIND_RESP，确认连接仍有效
        ImProto.Packet resp = PacketCodec.buildBindResp(packet.getSequence(), config.getServerId());
        channel.writeAndFlush(resp);

        logger.info("已认证连接重复绑定, 返回 BIND_RESP: userId={}, channelId={}",
                userId, channel.id().asShortText());
    }

    /**
     * 处理同设备互踢（新连接替换旧连接）
     * <p>
     * 客户端断线重连时，旧连接往往是"半开连接"：客户端已失联但 TCP 未被感知断开。
     * 若此时仍向旧连接发送 KickNotify，客户端 SDK 会误判为"被踢/账号在别处登录"，
     * 从而停止重连，导致重连无法完成。
     * <p>
     * 判定完全基于客户端携带的 deviceId（设备唯一标识，客户端必传）：
     * - 新旧连接 deviceId 相同 → 同一台设备重连，静默替换，不发送 KickNotify；
     * - 否则（不同或缺失）→ 另一台设备顶号，立即发送 KickNotify(409) 并关闭。
     *
     * @param userId      用户ID
     * @param device      设备类型
     * @param newDeviceId 新连接携带的设备唯一标识
     * @param oldChannel  旧连接
     */
    private void kickOldChannel(String userId, DeviceType device, String newDeviceId, Channel oldChannel) {
        oldChannel.attr(KICKED_BY_NEW_KEY).set(true);

        String oldDeviceId = oldChannel.attr(DEVICE_ID_KEY).get();

        // 同一台设备重连 → 静默替换，不发送 KickNotify
        if (newDeviceId != null && newDeviceId.equals(oldDeviceId)) {
            logger.info("同设备互踢(同一设备重连, 静默替换), userId={}, device={}, oldChannel={}",
                    userId, device, oldChannel.id().asShortText());
            oldChannel.close();
            return;
        }

        // 另一台设备顶号 → 立即踢下线
        logger.info("同设备互踢(异设备顶号), userId={}, device={}, oldChannel={}, oldDeviceId={}, newDeviceId={}",
                userId, device, oldChannel.id().asShortText(), oldDeviceId, newDeviceId);
        ImProto.Packet kickPacket = PacketCodec.buildKickNotify(409, "kicked by same device login");
        oldChannel.writeAndFlush(kickPacket).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * 检查通道是否已认证
     */
    public boolean isAuthenticated(Channel channel) {
        Boolean auth = channel.attr(AUTH_KEY).get();
        return auth != null && auth;
    }

    /**
     * 获取通道绑定的用户ID
     */
    public String getUserId(Channel channel) {
        return channel.attr(USER_ID_KEY).get();
    }

    /**
     * 获取通道绑定的设备类型
     */
    public DeviceType getDevice(Channel channel) {
        return channel.attr(DEVICE_KEY).get();
    }

    /**
     * 发送绑定失败响应
     */
    private void sendBindFail(Channel channel, long sequence, int code, String message) {
        ImProto.Packet resp = PacketCodec.buildBindFailResp(sequence, code, message);
        channel.writeAndFlush(resp);
    }
}
