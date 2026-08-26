package io.getbit.gim.core.connection.channel;

import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.protocol.codec.DeviceType;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChannelManager.java
 *
 * 多设备通道管理器
 * 支持同一用户在多种设备类型上同时在线（分组共存策略）
 * 同一设备类型互踢：新连接替换旧连接
 * 映射生命周期完全由连接事件（bind/unbind/channelInactive）驱动，不使用带时间驱逐的缓存：
 * 自动驱逐会与真实连接状态脱节（幽灵连接、离线事件丢失），半开连接由服务端读超时兜底清理。
 *
 * @author gogym
 */
public class ChannelManager {

    private static final Logger logger = LoggerFactory.getLogger(ChannelManager.class);

    private final String serverId;

    public ChannelManager(GimProperties config) {
        this.serverId = config.getServerId();
    }

    /**
     * channelId -> ConnectionInfo（反向映射，用于断连时快速查找）
     */
    private final Map<String, ConnectionInfo> connections = new ConcurrentHashMap<>();

    /**
     * userId -> Map<DeviceType, Channel>（多设备支持）
     */
    private final Map<String, Map<DeviceType, Channel>> userChannels = new ConcurrentHashMap<>();

    // ====================== 绑定与解绑 ======================

    /**
     * 绑定结果：旧连接及其连接信息（无旧连接时返回 null）
     *
     * @param oldChannel 被替换的旧连接（同设备互踢），无旧连接时为 null
     * @param oldInfo    旧连接的 ConnectionInfo（含 deviceId，供互踢判定），无旧连接时为 null
     */
    public record BindResult(Channel oldChannel, ConnectionInfo oldInfo) {
    }

    public BindResult bind(String userId, DeviceType device, String deviceId, Channel channel) {
        String channelId = channel.id().asLongText();

        Map<DeviceType, Channel> deviceMap = userChannels.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());

        // 同设备类型互踢
        Channel oldChannel = deviceMap.put(device, channel);

        ConnectionInfo oldInfo = null;
        if (oldChannel != null && oldChannel != channel) {
            oldInfo = connections.remove(oldChannel.id().asLongText());
            logger.debug("同设备互踢, userId: {}, device: {}, oldChannel: {}", userId, device, oldChannel.id().asShortText());
        }

        connections.put(channelId, ConnectionInfo.of(serverId, userId, device, deviceId));

        logger.debug("绑定通道, userId: {}, device: {}, channelId: {}", userId, device, channelId);
        // 防御自踢：同一连接重复绑定（oldChannel == channel）时返回 null，
        // 避免上层将其当作旧连接执行 kickOldChannel，把自己的新连接关闭形成重连死循环
        return oldChannel != null && oldChannel != channel ? new BindResult(oldChannel, oldInfo) : null;
    }

    /**
     * 按 userId + 设备类型解绑（服务端主动踢人）
     */
    public void unbind(String userId, DeviceType device) {
        Map<DeviceType, Channel> deviceMap = userChannels.get(userId);
        if (deviceMap == null) {
            return;
        }

        Channel removed = deviceMap.remove(device);
        if (removed != null) {
            connections.remove(removed.id().asLongText());
        }

        if (deviceMap.isEmpty()) {
            // 条件移除：仅当映射仍是当前 deviceMap 时才删除，避免并发 bind 创建的新映射被误删
            userChannels.remove(userId, deviceMap);
        }

        logger.debug("解绑通道, userId: {}, device: {}", userId, device);
    }

    public ConnectionInfo unbindByChannelId(String channelId) {
        // 原子移除并返回旧值
        ConnectionInfo info = connections.remove(channelId);
        if (info == null) {
            return null;
        }

        String userId = info.userId();
        DeviceType device = info.device();

        if (userId != null) {
            Map<DeviceType, Channel> deviceMap = userChannels.get(userId);
            if (deviceMap != null) {
                Channel current = deviceMap.get(device);
                if (current != null && current.id().asLongText().equals(channelId)) {
                    deviceMap.remove(device);
                    if (deviceMap.isEmpty()) {
                        userChannels.remove(userId, deviceMap);
                    }
                }
            }
            logger.debug("断连解绑, userId: {}, device: {}, channelId: {}", userId, device, channelId);
        }

        return info;
    }

    // ====================== 查询 ======================

    /**
     * 按 channelId 查询连接信息（连接未登记或已解绑时返回 null）
     */
    public ConnectionInfo getConnectionInfo(String channelId) {
        return connections.get(channelId);
    }

    public Map<DeviceType, Channel> getChannels(String userId) {
        Map<DeviceType, Channel> deviceMap = userChannels.get(userId);
        if (deviceMap == null || deviceMap.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(deviceMap);
    }

    public int getOnlineUserCount() {
        return userChannels.size();
    }

    public int getTotalConnectionCount() {
        return connections.size();
    }
}
