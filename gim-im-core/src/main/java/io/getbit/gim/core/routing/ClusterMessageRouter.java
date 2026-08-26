package io.getbit.gim.core.routing;

import io.getbit.gim.core.connection.channel.ChannelManager;
import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.spi.ImRedisAdapter;
import io.getbit.gim.core.spi.ImRedisSubscriber;
import io.getbit.gim.core.spi.ImEventListener;
import io.getbit.gim.protocol.codec.Cmd;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.protocol.codec.PacketCodec;
import com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ClusterMessageRouter.java
 *
 * 集群消息路由器（Redis Pub/Sub 方案）
 *
 * 工作原理：
 * 1. 每个 IM 节点启动时订阅自己的 Redis channel: gim_node:{serverId}
 * 2. 发送消息到远程节点时，PUBLISH 到目标节点的 channel
 * 3. 收到订阅消息后，在本地投递给目标用户
 *
 * @author gogym
 */
public class ClusterMessageRouter {

    private static final Logger logger = LoggerFactory.getLogger(ClusterMessageRouter.class);

    private static final String NODE_CHANNEL_PREFIX = "gim_node:";

    private final GimProperties config;
    private final ChannelManager channelManager;
    private final ImRedisAdapter redisAdapter;
    private final ImRedisSubscriber redisSubscriber;
    private final List<ImEventListener> eventListeners;

    public ClusterMessageRouter(GimProperties config,
                                ChannelManager channelManager,
                                ImRedisAdapter redisAdapter,
                                ImRedisSubscriber redisSubscriber,
                                List<ImEventListener> eventListeners) {
        this.config = config;
        this.channelManager = channelManager;
        this.redisAdapter = redisAdapter;
        this.redisSubscriber = redisSubscriber;
        this.eventListeners = eventListeners;
    }

    // ====================== 生命周期 ======================

    /**
     * 启动：订阅本节点的 Redis channel
     * 非集群模式下跳过
     */
    public void start() {
        if (!config.isEnableCluster()) {
            logger.info("非集群模式, 跳过集群消息路由启动");
            return;
        }

        String serverId = config.getServerId();
        String channel = NODE_CHANNEL_PREFIX + serverId;

        Thread subscribeThread = new Thread(() -> {
            try {
                redisSubscriber.subscribe(channel, this::onClusterMessage);
            } catch (Exception e) {
                logger.error("集群消息路由订阅失败", e);
            }
        }, "cluster-route-subscriber");
        subscribeThread.setDaemon(true);
        subscribeThread.start();

        logger.info("集群消息路由启动, 订阅 channel: {}", channel);
    }

    /**
     * 停止：取消订阅
     * 非集群模式下跳过
     */
    public void stop() {
        if (!config.isEnableCluster()) {
            return;
        }
        redisSubscriber.unsubscribe();
        logger.info("集群消息路由已停止");
    }

    // ====================== 消息发送 ======================

    /**
     * 发送消息到远程节点
     */
    public void routeToRemote(String targetServerId, ImProto.Packet packet, String receiverId) {
        if (!redisSubscriber.isSubscribed()) {
            logger.warn("集群路由未就绪, 无法投递到节点: {}", targetServerId);
            return;
        }

        try {
            String channel = NODE_CHANNEL_PREFIX + targetServerId;
            String json = JsonFormat.printer().print(packet);
            redisAdapter.publish(channel, json);
            logger.debug("消息已路由到远程节点: {} -> receiver={}", targetServerId, receiverId);
        } catch (Exception e) {
            logger.error("消息路由失败, targetServer={}, receiver={}", targetServerId, receiverId, e);
        }
    }

    // ====================== 内部方法 ======================

    /**
     * 集群消息回调：收到其他节点发来的消息，在本地投递
     */
    private void onClusterMessage(String message) {
        try {
            ImProto.Packet packet = PacketCodec.packetFromJson(message);
            deliverLocally(packet);
        } catch (Exception e) {
            logger.error("集群消息投递失败", e);
        }
    }

    /**
     * 在本地投递从远程节点收到的消息
     */
    private void deliverLocally(ImProto.Packet packet) {
        int cmd = packet.getCmd();

        if (cmd == Cmd.SINGLE_CHAT_MSG || cmd == Cmd.GROUP_CHAT_MSG) {
            try {
                ImProto.ChatMessage chatMsg = PacketCodec.parseChatMessage(packet);
                String receiverId = chatMsg.getReceiverId();

                var targetChannels = channelManager.getChannels(receiverId);
                if (!targetChannels.isEmpty()) {
                    ImProto.Packet fwdPacket = PacketCodec.create(cmd, 0, chatMsg);
                    targetChannels.values().forEach(ch -> {
                        if (ch.isActive()) {
                            ch.writeAndFlush(fwdPacket);
                        }
                    });
                    logger.debug("集群消息本地投递成功: receiver={}, devices={}", receiverId, targetChannels.size());
                } else {
                    logger.debug("集群消息本地投递: 用户不在线, receiver={}", receiverId);
                    fireDeliveryFailed(packet, receiverId, "OFFLINE");
                }
            } catch (Exception e) {
                logger.error("集群聊天消息本地投递失败", e);
            }

        } else if (cmd == Cmd.RTC_SIGNAL) {
            try {
                ImProto.RtcSignal signal = PacketCodec.parseRtcSignal(packet);
                String targetId = signal.getReceiverId();

                var targetChannels = channelManager.getChannels(targetId);
                if (!targetChannels.isEmpty()) {
                    ImProto.Packet fwdPacket = PacketCodec.create(Cmd.RTC_SIGNAL, 0, signal);
                    targetChannels.values().forEach(ch -> {
                        if (ch.isActive()) {
                            ch.writeAndFlush(fwdPacket);
                        }
                    });
                }
            } catch (Exception e) {
                logger.error("集群RTC信令本地投递失败", e);
            }

        } else {
            // 通知类型（好友通知/群通知/在线状态/撤回通知等）：
            // 根据消息体中的目标用户进行本地投递
            deliverNotifyLocally(packet, cmd);
        }
    }

    /**
     * 投递通知类型消息到本地用户
     * 不同通知类型的目标用户字段不同，需分别解析
     */
    private void deliverNotifyLocally(ImProto.Packet packet, int cmd) {
        try {
            String targetUserId = null;

            // 根据 cmd 解析目标用户
            if (cmd == Cmd.FRIEND_REQUEST_NOTIFY) {
                ImProto.FriendRequestNotify notify = PacketCodec.parseFriendRequestNotify(packet);
                targetUserId = notify.getReceiverId();
            } else if (cmd == Cmd.FRIEND_STATUS_NOTIFY) {
                ImProto.FriendStatusNotify notify = PacketCodec.parseFriendStatusNotify(packet);
                targetUserId = notify.getReceiverId();
            } else if (cmd == Cmd.ONLINE_STATUS_NOTIFY) {
                // 在线状态通知：投递给所有本地用户（由上层广播）
                // 这里无法确定具体目标，跳过
                logger.debug("集群在线状态通知: userId={}", cmd);
                return;
            } else if (cmd == Cmd.MSG_RECALL_NOTIFY) {
                // 撤回通知：需要广播给群成员或单聊对方
                // 由 MsgRecallHandler 自行处理，集群场景已在 handler 中逐个 routeToUser
                return;
            }

            if (targetUserId != null && !targetUserId.isEmpty()) {
                var targetChannels = channelManager.getChannels(targetUserId);
                if (!targetChannels.isEmpty()) {
                    targetChannels.values().forEach(ch -> {
                        if (ch.isActive()) {
                            ch.writeAndFlush(packet);
                        }
                    });
                    logger.debug("集群通知本地投递成功: cmd={}, receiver={}", cmd, targetUserId);
                } else {
                    // 用户不在线，触发离线通知回调
                    for (ImEventListener listener : eventListeners) {
                        try {
                            listener.onOfflineMessage(packet, targetUserId, "ROUTE_NOT_FOUND");
                        } catch (Exception e) {
                            logger.error("集群通知离线回调异常: receiver={}", targetUserId, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("集群通知本地投递失败: cmd={}", cmd, e);
        }
    }

    private void fireDeliveryFailed(ImProto.Packet packet, String receiverId, String reason) {
        if (eventListeners != null) {
            for (ImEventListener listener : eventListeners) {
                try {
                    listener.onMessageDeliveryFailed(packet, receiverId, reason);
                } catch (Exception e) {
                    logger.error("事件监听器回调异常", e);
                }
            }
        }
    }
}
