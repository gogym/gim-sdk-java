package io.getbit.gim.core.notify.friend;

import io.getbit.gim.core.connection.channel.ChannelManager;
import io.getbit.gim.core.notify.BaseNotifyService;
import io.getbit.gim.core.routing.ClusterMessageRouter;
import io.getbit.gim.core.routing.UserRouteService;
import io.getbit.gim.core.spi.ImEventListener;
import io.getbit.gim.core.spi.ImFriendProvider;
import io.getbit.gim.protocol.codec.PacketCodec;
import io.getbit.gim.protocol.codec.ImProto;

import java.util.List;

/**
 * FriendNotifyService.java
 * <p>
 * 好友通知推送服务
 * 通过 IM 长连接推送好友申请、好友状态变更、在线/离线状态等通知
 * <p>
 * 通知类型：
 * 1. FRIEND_REQUEST_NOTIFY - 好友申请通知（推送给被申请方）
 * 2. FRIEND_STATUS_NOTIFY  - 好友状态变更（上线/离线，推送给好友列表）
 *
 * @author gogym
 */
public class FriendNotifyService extends BaseNotifyService {

    private final ImFriendProvider friendProvider;

    public FriendNotifyService(ChannelManager channelManager,
                               UserRouteService userRouteService,
                               ClusterMessageRouter clusterMessageRouter,
                               ImFriendProvider friendProvider,
                               List<ImEventListener> eventListeners) {
        super(channelManager, userRouteService, clusterMessageRouter, eventListeners);
        this.friendProvider = friendProvider;
    }

    // ====================== 好友申请通知 ======================

    /**
     * 发送好友申请通知
     * 当 A 申请加 B 为好友时，推送通知给 B
     *
     * @param fromUserId 申请人ID
     * @param toUserId   被申请方ID
     * @param nickname   申请人昵称
     * @param avatar     申请人头像
     * @param message    申请留言
     * @param requestId  申请ID（客户端本地持久化后用于同意/拒绝）
     * @param logId      消息日志ID（服务端写入日志后回填，客户端用于本地日志去重）
     */
    public void notifyFriendRequest(String fromUserId, String toUserId,
                                    String nickname, String avatar, String message,
                                    String requestId, String logId) {
        ImProto.Packet packet = PacketCodec.buildFriendRequestNotifyPacket(
                fromUserId, toUserId, nickname, avatar, message, requestId, logId);
        deliverToUser(toUserId, packet);

        logger.debug("好友申请通知已发送: from={}, to={}, requestId={}", fromUserId, toUserId, requestId);
    }

    /**
     * 发送好友申请处理结果通知
     * 当 B 同意/拒绝 A 的申请时，推送结果给 A
     *
     * @param fromUserId 处理方ID（B）
     * @param toUserId   申请人ID（A）
     * @param accepted   是否同意
     */
    public void notifyFriendRequestResult(String fromUserId, String toUserId, boolean accepted) {
        ImProto.Packet packet = PacketCodec.buildFriendStatusNotifyPacket(
                fromUserId, toUserId, accepted ? 1 : 2);
        deliverToUser(toUserId, packet);

        logger.debug("好友申请结果通知已发送: from={}, to={}, accepted={}", fromUserId, toUserId, accepted);
    }

    /**
     * 通知对方：你已被对方删除好友
     *
     * @param deletedUserId 执行删除的用户ID
     * @param notifyUserId  被通知的用户ID（对方）
     */
    public void notifyFriendDeleted(String deletedUserId, String notifyUserId) {
        ImProto.Packet packet = PacketCodec.buildFriendStatusNotifyPacket(
                deletedUserId, notifyUserId, 3);
        deliverToUser(notifyUserId, packet);

        logger.debug("好友删除通知已发送: deletedBy={}, notify={}", deletedUserId, notifyUserId);
    }

    // ====================== 在线状态通知 ======================

    /**
     * 用户上线后，通知其好友列表
     *
     * @param userId 上线用户ID
     */
    public void notifyUserOnline(String userId) {
        try {
            List<String> friendIds = friendProvider.getOnlineNotifyFriendIds(userId);
            if (friendIds == null || friendIds.isEmpty()) {
                return;
            }

            ImProto.Packet packet = PacketCodec.buildOnlineStatusNotifyPacket(userId, 1);
            for (String friendId : friendIds) {
                deliverToUser(friendId, packet);
            }

            logger.debug("上线通知已发送: userId={}, friendCount={}", userId, friendIds.size());
        } catch (Exception e) {
            logger.error("上线通知处理失败: userId={}", userId, e);
        }
    }

    /**
     * 用户绑定成功后，推送其好友列表的当前在线状态给该用户
     * 使客户端能立即知道哪些好友在线
     *
     * @param userId 新绑定的用户ID
     */
    public void syncFriendsOnlineStatus(String userId) {
        try {
            List<String> friendIds = friendProvider.getFriendIds(userId);
            if (friendIds == null || friendIds.isEmpty()) {
                return;
            }

            for (String friendId : friendIds) {
                boolean online = !channelManager.getChannels(friendId).isEmpty();
                if (!online) {
                    online = userRouteService.getServerId(friendId) != null;
                }
                int status = online ? 1 : 0;
                ImProto.Packet packet = PacketCodec.buildOnlineStatusNotifyPacket(friendId, status);
                deliverToUser(userId, packet);
            }

            logger.debug("好友在线状态同步完成: userId={}, friendCount={}", userId, friendIds.size());
        } catch (Exception e) {
            logger.error("好友在线状态同步失败: userId={}", userId, e);
        }
    }

    /**
     * 同步单个好友的在线状态
     * 用于添加好友后，立即推送新好友的在线状态
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     */
    public void syncSingleFriendOnlineStatus(String userId, String friendId) {
        try {
            boolean online = !channelManager.getChannels(friendId).isEmpty();
            if (!online) {
                online = userRouteService.getServerId(friendId) != null;
            }
            int status = online ? 1 : 0;
            ImProto.Packet packet = PacketCodec.buildOnlineStatusNotifyPacket(friendId, status);
            deliverToUser(userId, packet);

            logger.debug("单个好友在线状态同步完成: userId={}, friendId={}, online={}", userId, friendId, online);
        } catch (Exception e) {
            logger.error("单个好友在线状态同步失败: userId={}, friendId={}", userId, friendId, e);
        }
    }

    /**
     * 用户断开连接时触发离线通知
     *
     * @param userId 断开连接的用户ID
     */
    public void notifyUserOffline(String userId) {
        try {
            List<String> friendIds = friendProvider.getOnlineNotifyFriendIds(userId);
            if (friendIds == null || friendIds.isEmpty()) {
                logger.debug("用户 {} 无好友，跳过离线通知", userId);
                return;
            }

            ImProto.Packet packet = PacketCodec.buildOnlineStatusNotifyPacket(userId, 0);
            for (String friendId : friendIds) {
                deliverToUser(friendId, packet);
            }

            logger.debug("离线通知已发送: userId={}, friendCount={}", userId, friendIds.size());
        } catch (Exception e) {
            logger.error("离线通知处理失败: userId={}", userId, e);
        }
    }
}
