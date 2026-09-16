package io.getbit.gim.core.notify;

import io.getbit.gim.core.connection.channel.ChannelManager;
import io.getbit.gim.core.routing.ClusterMessageRouter;
import io.getbit.gim.core.routing.UserRouteService;
import io.getbit.gim.core.spi.ImEventListener;
import io.getbit.gim.protocol.codec.ImProto;
import io.netty.channel.Channel;

import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;

/**
 * BaseNotifyService.java
 * <p>
 * 通知推送服务基类
 * 提供通用的用户投递能力：本地投递 → 集群路由 → 离线回调
 * <p>
 * 子类：FriendNotifyService、GroupNotifyService
 *
 * @author gogym
 */
@Slf4j
public abstract class BaseNotifyService {

    protected final ChannelManager channelManager;
    protected final UserRouteService userRouteService;
    protected final ClusterMessageRouter clusterMessageRouter;
    protected final List<ImEventListener> eventListeners;

    protected BaseNotifyService(ChannelManager channelManager,
                                UserRouteService userRouteService,
                                ClusterMessageRouter clusterMessageRouter,
                                List<ImEventListener> eventListeners) {
        this.channelManager = channelManager;
        this.userRouteService = userRouteService;
        this.clusterMessageRouter = clusterMessageRouter;
        this.eventListeners = eventListeners;
    }

    /**
     * 投递通知给目标用户
     * 优先本地投递，不在线则走集群路由，路由不可达且开启离线消息时触发离线回调
     *
     * @param targetUserId 目标用户ID
     * @param packet       通知包
     */
    protected void deliverToUser(String targetUserId, ImProto.Packet packet) {
        // 1. 本地投递
        var deviceChannels = channelManager.getChannels(targetUserId);
        if (!deviceChannels.isEmpty()) {
            for (Map.Entry<?, Channel> entry : deviceChannels.entrySet()) {
                Channel ch = entry.getValue();
                if (ch != null && ch.isActive()) {
                    ch.writeAndFlush(packet);
                }
            }
            return;
        }

        // 2. 集群路由
        if (userRouteService.isRemote(targetUserId)) {
            String targetServerId = userRouteService.getServerId(targetUserId);
            clusterMessageRouter.routeToRemote(targetServerId, packet, targetUserId);
            return;
        }

        // 3. 离线回调
        log.debug("通知目标用户离线且路由不可达: cmd={}, receiver={}", packet.getCmd(), targetUserId);
        for (ImEventListener listener : eventListeners) {
            try {
                listener.onOfflineMessage(packet, targetUserId, "OFFLINE");
            } catch (Exception e) {
                log.error("离线通知回调异常: receiver={}", targetUserId, e);
            }
        }
    }
}
