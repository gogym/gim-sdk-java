package io.getbit.gim.core.notify.group;

import io.getbit.gim.core.connection.channel.ChannelManager;
import io.getbit.gim.core.notify.BaseNotifyService;
import io.getbit.gim.core.routing.ClusterMessageRouter;
import io.getbit.gim.core.routing.UserRouteService;
import io.getbit.gim.core.spi.ImEventListener;
import io.getbit.gim.core.spi.ImGroupMemberProvider;
import io.getbit.gim.protocol.codec.Cmd;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.protocol.codec.PacketCodec;

import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * GroupNotifyService.java
 * <p>
 * 群组通知推送服务
 * 通过 IM 长连接向群成员推送群组变更通知
 * <p>
 * 通知类型：
 * 1. GROUP_MEMBER_NOTIFY (cmd=40) - 群成员变更（加入/退出/被踢/被邀请）
 * 2. GROUP_NOTIFY        (cmd=41) - 群信息/事件变更（信息/公告/禁言/角色/转让群主）
 * 3. GROUP_JOIN_REQUEST_NOTIFY (cmd=42) - 入群申请通知
 *
 * @author gogym
 */
@Slf4j
public class GroupNotifyService extends BaseNotifyService {

    private final ImGroupMemberProvider groupMemberProvider;

    public GroupNotifyService(ChannelManager channelManager,
                              UserRouteService userRouteService,
                              ClusterMessageRouter clusterMessageRouter,
                              ImGroupMemberProvider groupMemberProvider,
                              List<ImEventListener> eventListeners) {
        super(channelManager, userRouteService, clusterMessageRouter, eventListeners);
        this.groupMemberProvider = groupMemberProvider;
    }

    // ====================== 群成员变更通知 ======================

    /**
     * 通知群成员变更（加入/退出/被踢/被邀请）
     *
     * @param groupId    群组ID
     * @param action     1=加入 2=退出 3=被踢 4=被邀请
     * @param userId     变更的用户ID
     * @param operatorId 操作者ID
     */
    public void notifyMemberChange(String groupId, int action, String userId, String operatorId) {
        ImProto.Packet packet = PacketCodec.buildGroupMemberNotifyPacket(groupId, action, userId, operatorId);
        broadcastToGroup(groupId, packet, userId);
        log.debug("群成员变更通知: group={}, action={}, userId={}", groupId, action, userId);
    }

    /**
     * 新成员加入群通知
     */
    public void notifyMemberJoined(String groupId, String userId) {
        notifyMemberChange(groupId, 1, userId, null);
    }

    /**
     * 成员退出群通知
     */
    public void notifyMemberQuit(String groupId, String userId) {
        notifyMemberChange(groupId, 2, userId, null);
    }

    /**
     * 成员被踢出群通知
     */
    public void notifyMemberKicked(String groupId, String userId, String operatorId) {
        notifyMemberChange(groupId, 3, userId, operatorId);
    }

    /**
     * 成员被邀请入群通知
     */
    public void notifyMemberInvited(String groupId, String userId, String inviterId) {
        notifyMemberChange(groupId, 4, userId, inviterId);
    }

    // ====================== 群信息/事件通知 ======================

    /**
     * 群信息变更通知（名称/头像）
     */
    public void notifyGroupInfoChanged(String groupId, String operatorId, String content) {
        ImProto.Packet packet = PacketCodec.buildGroupNotifyPacket(
                groupId, 1, operatorId, null, content);
        broadcastToGroup(groupId, packet, null);
        log.debug("群信息变更通知: group={}, operator={}", groupId, operatorId);
    }

    /**
     * 群公告更新通知
     */
    public void notifyAnnouncementUpdated(String groupId, String operatorId, String announcement) {
        ImProto.Packet packet = PacketCodec.buildGroupNotifyPacket(
                groupId, 2, operatorId, null, announcement);
        broadcastToGroup(groupId, packet, null);
        log.debug("群公告更新通知: group={}, operator={}", groupId, operatorId);
    }

    /**
     * 全员禁言通知
     */
    public void notifyMuteAll(String groupId, String operatorId, boolean muteAll) {
        ImProto.Packet packet = PacketCodec.buildGroupNotifyPacket(
                groupId, 3, operatorId, null, muteAll ? "1" : "0");
        broadcastToGroup(groupId, packet, null);
        log.debug("全员禁言通知: group={}, muteAll={}", groupId, muteAll);
    }

    /**
     * 成员禁言通知
     */
    public void notifyMemberMuted(String groupId, String operatorId, String targetUserId, boolean muted) {
        ImProto.Packet packet = PacketCodec.buildGroupNotifyPacket(
                groupId, 4, operatorId, targetUserId, muted ? "1" : "0");
        broadcastToGroup(groupId, packet, null);
        log.debug("成员禁言通知: group={}, target={}, muted={}", groupId, targetUserId, muted);
    }

    /**
     * 角色变更通知（设置/取消管理员）
     */
    public void notifyRoleChanged(String groupId, String operatorId, String targetUserId, boolean isAdmin) {
        ImProto.Packet packet = PacketCodec.buildGroupNotifyPacket(
                groupId, 5, operatorId, targetUserId, isAdmin ? "1" : "0");
        broadcastToGroup(groupId, packet, null);
        log.debug("角色变更通知: group={}, target={}, isAdmin={}", groupId, targetUserId, isAdmin);
    }

    /**
     * 转让群主通知
     */
    public void notifyOwnerTransferred(String groupId, String oldOwnerId, String newOwnerId) {
        ImProto.Packet packet = PacketCodec.buildGroupNotifyPacket(
                groupId, 6, oldOwnerId, newOwnerId, null);
        broadcastToGroup(groupId, packet, null);
        log.debug("转让群主通知: group={}, from={}, to={}", groupId, oldOwnerId, newOwnerId);
    }

    // ====================== 入群申请通知 ======================

    /**
     * 入群申请通知（推送给群主/管理员）
     *
     * @param groupId      群组ID
     * @param applicantId  申请人ID
     * @param message      申请留言
     * @param adminUserIds 群主/管理员ID列表
     */
    public void notifyJoinRequest(String groupId, String applicantId, String message, List<String> adminUserIds) {
        ImProto.Packet packet = PacketCodec.buildGroupJoinRequestNotifyPacket(
                groupId, applicantId, null, 0, message);

        if (adminUserIds != null) {
            for (String adminId : adminUserIds) {
                deliverToUser(adminId, packet);
            }
        }
        log.debug("入群申请通知: group={}, applicant={}", groupId, applicantId);
    }

    /**
     * 入群申请审批结果通知（推送给申请人）
     */
    public void notifyJoinRequestResult(String groupId, String applicantId,
                                        String operatorId, boolean approved) {
        ImProto.Packet packet = PacketCodec.buildGroupJoinRequestNotifyPacket(
                groupId, applicantId, operatorId,
                approved ? 1 : 2, null);
        deliverToUser(applicantId, packet);
        log.debug("入群申请结果通知: group={}, applicant={}, approved={}", groupId, applicantId, approved);
    }

    // ====================== 广播 ======================

    /**
     * 广播通知给群内所有成员
     *
     * @param groupId       群组ID
     * @param packet        通知包
     * @param excludeUserId 排除的用户ID（如事件当事人，可为 null）
     */
    public void broadcastToGroup(String groupId, ImProto.Packet packet, String excludeUserId) {
        List<String> memberUserIds = groupMemberProvider.getGroupMemberUserIds(groupId);
        if (memberUserIds == null || memberUserIds.isEmpty()) {
            log.warn("群通知广播: 群 {} 无成员", groupId);
            return;
        }
        for (String userId : memberUserIds) {
            if (userId.equals(excludeUserId)) {
                continue;
            }
            deliverToUser(userId, packet);
        }
    }
}
