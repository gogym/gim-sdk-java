package io.getbit.gim.core.message.handler;

import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.connection.channel.ChannelManager;
import io.getbit.gim.core.routing.ClusterMessageRouter;
import io.getbit.gim.core.routing.UserRouteService;
import io.getbit.gim.core.spi.ImEventListener;
import io.getbit.gim.protocol.codec.DeviceType;
import io.getbit.gim.protocol.codec.ImProto;
import io.netty.channel.Channel;

import lombok.extern.slf4j.Slf4j;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * BaseHandler.java
 * <p>
 * 消息处理器基类
 * <p>
 * 子类必须实现：
 * - cmd()：声明当前 Handler 处理的指令类型
 * - handle()：具体业务处理逻辑
 * <p>
 * 公共能力：路由 / 投递 / Channel 查询
 *
 * @author gogym
 */
@Slf4j
public abstract class BaseHandler {

    protected final ChannelManager channelManager;
    protected final UserRouteService userRouteService;
    protected final ClusterMessageRouter clusterMessageRouter;
    protected final List<ImEventListener> eventListeners;

    protected BaseHandler(IMServerFacade facade) {
        this.channelManager = facade.getChannelManager();
        this.userRouteService = facade.getUserRouteService();
        this.clusterMessageRouter = facade.getClusterRouter();
        this.eventListeners = facade.getEventListeners() != null
                ? facade.getEventListeners() : Collections.emptyList();
    }

    // ====================== 子类必须实现 ======================

    /**
     * 当前 Handler 处理的指令类型（对应 Cmd 常量）
     */
    public abstract int cmd();

    /**
     * 处理消息
     *
     * @param packet  客户端上行 Packet
     * @param channel 当前连接 Channel
     * @param userId  当前用户 ID
     */
    public abstract void handle(ImProto.Packet packet, Channel channel, String userId);

    // ====================== 公共路由能力 ======================

    /**
     * 路由消息到指定用户（本地/远程）
     *
     * @return true=投递成功, false=用户离线
     */
    protected boolean routeToUser(String receiverId, ImProto.Packet packet) {
        if (isLocal(receiverId)) {
            return deliverToLocal(receiverId, packet);
        }

        String targetServerId = getServerId(receiverId);
        if (targetServerId != null) {
            routeToRemote(targetServerId, packet, receiverId);
            return true;
        }

        return false;
    }

    /**
     * 本地投递消息（投递到接收者的所有在线设备）
     *
     * @return true=至少有一个设备投递成功
     */
    protected boolean deliverToLocal(String receiverId, ImProto.Packet packet) {
        var deviceChannels = getChannels(receiverId);
        if (deviceChannels.isEmpty()) {
            return false;
        }

        boolean delivered = false;
        for (Map.Entry<?, Channel> entry : deviceChannels.entrySet()) {
            Channel ch = entry.getValue();
            if (ch != null && ch.isActive()) {
                ch.writeAndFlush(packet);
                delivered = true;
            }
        }
        return delivered;
    }

    /**
     * 查询用户所有在线设备 Channel
     */
    protected Map<DeviceType, Channel> getChannels(String userId) {
        return channelManager.getChannels(userId);
    }

    /**
     * 跨节点路由消息到远程服务器
     */
    protected void routeToRemote(String serverId, ImProto.Packet packet, String targetUserId) {
        clusterMessageRouter.routeToRemote(serverId, packet, targetUserId);
    }

    /**
     * 查询用户所在节点ID
     */
    protected String getServerId(String userId) {
        return userRouteService.getServerId(userId);
    }

    /**
     * 判断用户是否在本节点
     */
    protected boolean isLocal(String userId) {
        return userRouteService.isLocal(userId);
    }

    // ====================== 会话解析工具 ======================

    /**
     * 从 conversationId 中解析对方 userId
     * 支持格式：minId_maxId（数字排序的会话ID）
     * 如果 userId 是其中一方，返回另一方；否则返回 null
     *
     * @param conversationId 会话ID
     * @param userId         当前用户ID
     * @return 对方userId，无法解析返回null
     */
    protected String parseReceiverFromConversation(String conversationId, String userId) {
        if (conversationId == null || conversationId.isEmpty()) {
            return null;
        }
        int idx = conversationId.indexOf('_');
        if (idx > 0 && idx < conversationId.length() - 1) {
            String a = conversationId.substring(0, idx);
            String b = conversationId.substring(idx + 1);
            if (a.equals(userId)) {
                return b;
            }
            if (b.equals(userId)) {
                return a;
            }
        }
        return null;
    }

    // ====================== 离线消息回调 ======================

    /**
     * 触发消息回调（所有正常投递的消息均触发，用于业务层持久化）
     */
    protected void fireReceivedMessage(ImProto.Packet packet) {
        for (ImEventListener listener : eventListeners) {
            try {
                listener.onReceivedMessage(packet);
            } catch (Exception e) {
                log.error("消息回调异常", e);
            }
        }
    }

    /**
     * 触发离线消息回调（接收方不在线时调用）
     */
    protected void fireOfflineMessage(ImProto.Packet packet, String receiverId, String reason) {
        for (ImEventListener listener : eventListeners) {
            try {
                listener.onOfflineMessage(packet, receiverId, reason);
            } catch (Exception e) {
                log.error("离线消息回调异常, receiver={}", receiverId, e);
            }
        }
    }
}
