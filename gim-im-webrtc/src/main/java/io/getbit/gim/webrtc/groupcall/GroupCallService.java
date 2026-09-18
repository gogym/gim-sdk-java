package io.getbit.gim.webrtc.groupcall;

import com.google.gson.Gson;
import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.message.handler.BaseHandler;
import io.getbit.gim.core.spi.ImGroupMemberProvider;
import io.getbit.gim.protocol.codec.Cmd;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.protocol.codec.PacketCodec;
import io.getbit.gim.webrtc.enums.GroupCallMemberStatus;
import io.getbit.gim.webrtc.enums.GroupCallMode;
import io.getbit.gim.webrtc.enums.GroupCallRoomStatus;
import io.getbit.gim.webrtc.enums.GroupSignalType;
import io.getbit.gim.webrtc.groupcall.listener.GroupCallListener;
import io.getbit.gim.webrtc.groupcall.listener.ImGroupCallListener;
import io.getbit.gim.webrtc.groupcall.model.GroupCallMember;
import io.getbit.gim.webrtc.groupcall.model.GroupCallRoom;
import io.getbit.gim.webrtc.sfu.SfuAdapter;
import io.getbit.gim.webrtc.sfu.SfuToken;
import io.getbit.gim.webrtc.sfu.TurnCredentialService;
import io.getbit.gim.webrtc.dto.GroupCallInviteDto;
import io.getbit.gim.webrtc.dto.GroupCallParticipantDto;
import io.getbit.gim.webrtc.dto.GroupCallRequestDto;
import io.getbit.gim.webrtc.dto.GroupMemberInfoDto;
import io.getbit.gim.webrtc.dto.GroupRoomStateDto;
import io.getbit.gim.webrtc.dto.GroupMediaStateDto;
import io.getbit.gim.webrtc.dto.SingleCallRejectDto;
import io.getbit.gim.webrtc.util.RtcSignalValidator;
import io.netty.channel.Channel;

import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GroupCallService.java
 *
 * WebRTC 群通话生命周期服务（signalType 20~27 与 mediaState=100）
 * 由 RtcGroupHandler 在收到 cmd=51 且 signalType 属于群通话信令（GroupSignalType 已定义类型）时委派调用（继承 BaseHandler 仅为复用路由能力，
 * 自身不注册进 MessageDispatcher，避免与 RtcGroupHandler 的 cmd 冲突）
 *
 * 职责：
 * 1. groupCallRequest(20)：创建房间 → 下发 roomState(27) 给发起人 → 逐成员下发邀请(21)
 * 2. groupCallJoin(22)：成员入房 → 下发房间快照+SFU token/TURN 凭据 → 广播成员变更(26)
 * 3. groupCallReject(23)/groupCallLeave(24)：更新成员状态 → 广播成员变更(26)
 * 4. groupCallEnd(25)：仅发起人可结束 → 广播通话结束 → 销毁 SFU 房间
 * 5. mediaState(100)：成员摄像头/麦克风开关状态更新 → 广播成员变更(26)
 * 6. 掉线清理/邀请超时/空房回收：由 GroupCallListener 回调转化为成员变更广播
 * 7. 仅剩一人兜底：TALKING 房间成员退出/掉线后仅剩一人时自动结束并广播 ended(26)
 *
 * 同时经 ImGroupCallListener 向业务侧发出通话开始/结束（含时长与原因）、成员进出事件
 *
 * Mesh 模式下的媒体信令（offer/answer/ICE，signalType 1~8）仍由 RtcGroupHandler 扇出转发
 *
 * @author gogym
 */
@Slf4j
public class GroupCallService extends BaseHandler implements GroupCallListener {

    private static final Gson GSON = new Gson();

    private final GroupCallSessionManager sessionManager;
    private final ImGroupMemberProvider groupMemberProvider;

    /**
     * TURN 凭据服务（Mesh 模式下随 roomState 下发，可为 null）
     */
    private final TurnCredentialService turnCredentialService;

    /**
     * 业务事件监听器（话单/统计），可为空列表
     */
    private final List<ImGroupCallListener> businessListeners;

    public GroupCallService(IMServerFacade facade,
                            GroupCallSessionManager sessionManager,
                            ImGroupMemberProvider groupMemberProvider,
                            TurnCredentialService turnCredentialService) {
        this(facade, sessionManager, groupMemberProvider, turnCredentialService, Collections.emptyList());
    }

    public GroupCallService(IMServerFacade facade,
                            GroupCallSessionManager sessionManager,
                            ImGroupMemberProvider groupMemberProvider,
                            TurnCredentialService turnCredentialService,
                            List<ImGroupCallListener> businessListeners) {
        super(facade);
        this.sessionManager = sessionManager;
        this.groupMemberProvider = groupMemberProvider;
        this.turnCredentialService = turnCredentialService;
        this.businessListeners = businessListeners != null ? businessListeners : Collections.emptyList();
        // 掉线清理/邀请超时/空房回收事件 → 成员变更广播与业务事件
        sessionManager.setListener(this);
    }

    @Override
    public int cmd() {
        return Cmd.RTC_GROUP;
    }

    @Override
    public void handle(ImProto.Packet packet, Channel channel, String userId) {
        try {
            ImProto.RtcGroup signal = PacketCodec.parseRtcGroup(packet);
            handle(packet, channel, userId, signal);
        } catch (Exception e) {
            log.error("群通话信令解析失败, userId={}", userId, e);
        }
    }

    /**
     * 处理群通话生命周期信令（由 RtcGroupHandler 委派）
     */
    public void handle(ImProto.Packet packet, Channel channel, String userId, ImProto.RtcGroup signal) {
        try {
            int signalType = signal.getSignalType();
            GroupSignalType type = GroupSignalType.fromCode(signalType);
            if (type == null) {
                log.warn("群通话未知信令类型: signalType={}, userId={}", signalType, userId);
                return;
            }
            switch (type) {
                case GROUP_CALL_REQUEST:
                    handleGroupCallRequest(signal, channel, userId);
                    break;
                case GROUP_CALL_JOIN:
                    handleJoin(signal, channel, userId);
                    break;
                case GROUP_CALL_REJECT:
                    handleReject(signal, userId);
                    break;
                case GROUP_CALL_LEAVE:
                    handleLeave(signal, userId);
                    break;
                case GROUP_CALL_END:
                    handleEnd(signal, userId);
                    break;
                case MEDIA_STATE:
                    handleMediaState(signal, userId);
                    break;
                case GROUP_CALL_INVITE:
                case PARTICIPANT_NOTIFY:
                case ROOM_STATE:
                    log.warn("群通话信令 {} 为服务端下发信令，忽略客户端上行, userId={}", signalType, userId);
                    break;
                default:
                    log.warn("群通话未知信令类型: signalType={}, userId={}", signalType, userId);
                    break;
            }
        } catch (Exception e) {
            log.error("群通话信令处理失败, signalType={}, userId={}", signal.getSignalType(), userId, e);
        }
    }

    // ====================== 信令处理 ======================

    /**
     * groupCallRequest(20)：发起群通话
     */
    private void handleGroupCallRequest(ImProto.RtcGroup signal, Channel channel, String userId) {
        String groupId = signal.getGroupId();
        if (groupId.isEmpty()) {
            log.warn("群通话发起缺少群组ID: userId={}", userId);
            return;
        }
        if (!RtcSignalValidator.validateGroupLifecyclePayload(GroupSignalType.GROUP_CALL_REQUEST.getCode(), signal.getPayload(), userId)) {
            return;
        }

        // 发起人占用检查（含 1:1 通话互斥）
        if (sessionManager.isUserBusy(userId)) {
            log.warn("群通话发起失败: 用户通话占用中, userId={}", userId);
            notifyUser(userId, buildParticipantSignal(null, "busy", userId, "in call", 0));
            return;
        }

        GroupCallRequestDto request = GSON.fromJson(signal.getPayload(), GroupCallRequestDto.class);
        List<String> inviteeIds = resolveInviteeIds(groupId, userId, request);
        if (inviteeIds.isEmpty()) {
            log.warn("群通话发起失败: 群 {} 无可邀请成员, userId={}", groupId, userId);
            notifyUser(userId, buildParticipantSignal(null, "busy", userId, "no invitee", 0));
            return;
        }

        // 人数上限：发起人 + 受邀成员总数不超过 maxMembers，超出部分不再邀请
        int maxMembers = sessionManager.getMaxMembers();
        if (inviteeIds.size() + 1 > maxMembers) {
            int allowed = Math.max(0, maxMembers - 1);
            log.warn("群通话受邀成员数 {} 超过人数上限 {}，仅邀请前 {} 人, groupId={}, userId={}",
                    inviteeIds.size(), maxMembers, allowed, groupId, userId);
            inviteeIds = new ArrayList<>(inviteeIds.subList(0, allowed));
        }

        GroupCallMode mode = sessionManager.selectMode(inviteeIds.size() + 1);
        GroupCallRoom room = sessionManager.createRoom(groupId, signal.getCallId(), userId,
                request.getCallType(), inviteeIds, mode, channel);
        if (room == null) {
            log.warn("群通话发起失败: 该群已有进行中的通话, groupId={}, userId={}", groupId, userId);
            notifyUser(userId, buildParticipantSignal(null, "busy", userId, "group call in progress", 0));
            return;
        }

        // SFU 模式：预创建媒体房间（LiveKit 首次入会也会自动建房，失败不阻断信令流程）
        if (mode == GroupCallMode.SFU && sessionManager.getSfuAdapter() != null) {
            try {
                sessionManager.getSfuAdapter().createRoom(room.getRoomId());
            } catch (Exception e) {
                log.error("SFU 房间创建失败, roomId={}", room.getRoomId(), e);
            }
        }

        // 业务事件：通话发起（话单起点）
        fireCallStart(room);

        // 向发起人下发房间快照
        sendRoomState(room, userId);

        // 逐成员下发邀请（离线触发离线回调供业务推送）
        GroupCallInviteDto invite = new GroupCallInviteDto();
        invite.setCallType(room.getCallType());
        invite.setGroupId(groupId);
        invite.setInitiatorId(userId);
        invite.setMode(mode == GroupCallMode.SFU ? "sfu" : "mesh");
        ImProto.RtcGroup inviteSignal = buildServerSignal(GroupSignalType.GROUP_CALL_INVITE.getCode(), room, GSON.toJson(invite));

        int offlineCount = 0;
        for (GroupCallMember member : room.getMembers().values()) {
            if (member.getUserId().equals(userId)) {
                continue;
            }
            if (!notifyUser(member.getUserId(), inviteSignal)) {
                fireOfflineMessage(PacketCodec.create(Cmd.RTC_GROUP, 0, inviteSignal), member.getUserId(), "OFFLINE");
                offlineCount++;
            }
        }

        log.info("群通话已发起: roomId={}, group={}, mode={}, initiator={}, invitees={}, offline={}",
                room.getRoomId(), groupId, mode, userId, room.getMembers().size() - 1, offlineCount);
    }

    /**
     * groupCallJoin(22)：成员加入群通话
     */
    private void handleJoin(ImProto.RtcGroup signal, Channel channel, String userId) {
        GroupCallRoom room = sessionManager.getRoom(signal.getRoomId());
        if (room == null) {
            log.warn("加入群通话失败: 房间不存在, roomId={}, userId={}", signal.getRoomId(), userId);
            return;
        }

        GroupCallMember joining = room.getMember(userId);
        boolean alreadyJoined = joining != null && joining.isJoined();

        // 人数上限：非重复加入且已达上限时拒绝，并告知发起端（管理器内加锁二次校验）
        if (!alreadyJoined && room.getJoinedMemberIds().size() >= sessionManager.getMaxMembers()) {
            log.warn("加入群通话失败: 房间人数已达上限 {}, roomId={}, userId={}",
                    sessionManager.getMaxMembers(), room.getRoomId(), userId);
            notifyUser(userId, buildParticipantSignal(room, "full", userId, "room full", 0));
            return;
        }

        GroupCallRoom joined = sessionManager.joinRoom(room.getRoomId(), userId, channel);
        if (joined == null) {
            log.warn("加入群通话失败: roomId={}, userId={}", room.getRoomId(), userId);
            return;
        }

        // 向加入者下发房间快照（SFU 模式含接入 token，Mesh 模式含 TURN 凭据）
        sendRoomState(joined, userId);

        // 广播成员加入通知给其他在通话中的成员
        broadcastParticipant(joined, "join", userId, null, userId);

        // 幂等重连（重复 join）不重复触发业务加入事件
        if (!alreadyJoined) {
            fireMemberJoin(joined.getCallId(), joined.getRoomId(), userId);
        }

        log.info("群通话成员加入: roomId={}, userId={}, joined={}",
                joined.getRoomId(), userId, joined.getJoinedMemberIds());
    }

    /**
     * groupCallReject(23)：成员拒绝邀请
     */
    private void handleReject(ImProto.RtcGroup signal, String userId) {
        GroupCallRoom room = sessionManager.getRoom(signal.getRoomId());
        if (room == null) {
            return;
        }
        GroupCallMember member = sessionManager.rejectInvite(room.getRoomId(), userId);
        if (member == null) {
            log.debug("拒绝群通话邀请无效: roomId={}, userId={}", room.getRoomId(), userId);
            return;
        }

        String reason = parseReason(signal.getPayload());
        broadcastParticipant(room, "reject", userId, reason, userId);

        log.info("群通话成员拒绝邀请: roomId={}, userId={}, reason={}", room.getRoomId(), userId, reason);
    }

    /**
     * groupCallLeave(24)：成员退出群通话
     */
    private void handleLeave(ImProto.RtcGroup signal, String userId) {
        GroupCallRoom room = resolveRoom(signal, userId);
        if (room == null) {
            return;
        }
        GroupCallMember member = sessionManager.leaveRoom(room.getRoomId(), userId);
        if (member == null) {
            log.debug("退出群通话无效: roomId={}, userId={}", room.getRoomId(), userId);
            return;
        }

        String reason = parseReason(signal.getPayload());
        broadcastParticipant(room, "leave", userId, reason, userId);
        fireMemberLeave(room.getCallId(), room.getRoomId(), userId, "leave");
        // 成员退出后仅剩一人时自动结束通话，避免最后一人永远停留在通话页
        maybeEndWhenLoneRemaining(room, userId);

        log.info("群通话成员退出: roomId={}, userId={}, joinedLeft={}",
                room.getRoomId(), userId, room.getJoinedMemberIds());
    }

    /**
     * groupCallEnd(25)：发起人结束全员通话
     */
    private void handleEnd(ImProto.RtcGroup signal, String userId) {
        GroupCallRoom room = resolveRoom(signal, userId);
        if (room == null) {
            return;
        }
        if (!userId.equals(room.getInitiatorId())) {
            log.warn("仅发起人可结束群通话: roomId={}, operator={}, initiator={}",
                    room.getRoomId(), userId, room.getInitiatorId());
            return;
        }

        GroupCallRoom ended = sessionManager.endRoom(room.getRoomId());
        if (ended == null) {
            return;
        }

        // 销毁 SFU 媒体房间
        destroySfuRoom(ended);

        // 业务事件：发起人结束（时长已在 endRoom 时定格）
        fireCallEnd(ended, "ended");

        // 广播通话结束给除发起人外的所有成员（含未响应/已退出的成员，便于客户端清理界面）
        broadcastParticipant(ended, "ended", userId, null, userId);

        log.info("群通话已结束: roomId={}, group={}, initiator={}", ended.getRoomId(), ended.getGroupId(), userId);
    }

    /**
     * 解析待操作房间：优先按信令 roomId 定位；roomId 为空时回退按用户当前占用房间解析。
     * 覆盖发起人在 ROOM_STATE 回传到达前即取消/退出的竞态（createRoom 时已建立 userId→roomId 映射），
     * 避免占用残留导致后续发起被误判忙线。roomId 非空但房间不存在时维持原语义（视为已结束，不回退）
     */
    private GroupCallRoom resolveRoom(ImProto.RtcGroup signal, String userId) {
        String roomId = signal.getRoomId();
        if (roomId != null && !roomId.isEmpty()) {
            return sessionManager.getRoom(roomId);
        }
        return sessionManager.getRoomByUser(userId);
    }

    /**
     * mediaState(100)：成员摄像头/麦克风开关状态上报
     * 更新成员媒体状态后广播给其他在通话中的成员（复用 PARTICIPANT_NOTIFY，action=media）
     */
    private void handleMediaState(ImProto.RtcGroup signal, String userId) {
        GroupCallRoom room = sessionManager.getRoom(signal.getRoomId());
        if (room == null) {
            log.warn("媒体开关上报失败: 房间不存在, roomId={}, userId={}", signal.getRoomId(), userId);
            return;
        }
        GroupMediaStateDto media = parseMediaState(signal.getPayload());
        if (media == null) {
            log.warn("媒体开关上报无效: payload 缺少 camera/mic, roomId={}, userId={}", room.getRoomId(), userId);
            return;
        }
        GroupCallMember member = room.getMember(userId);
        if (member == null || !member.isJoined()) {
            log.warn("媒体开关上报失败: 成员未在通话中, roomId={}, userId={}", room.getRoomId(), userId);
            return;
        }
        if (media.getCamera() != null) {
            member.setCamera(media.getCamera());
        }
        if (media.getMic() != null) {
            member.setMic(media.getMic());
        }
        // 成员快照携带最新媒体开关，客户端据此刷新对端 UI
        broadcastParticipant(room, "media", userId, null, userId);

        log.debug("群通话成员媒体开关已更新: roomId={}, userId={}, camera={}, mic={}",
                room.getRoomId(), userId, member.getCamera(), member.getMic());
    }

    // ====================== GroupCallListener 回调（管理器内部事件 → 广播） ======================

    @Override
    public void onMemberDisconnected(GroupCallRoom room, GroupCallMember member) {
        // 成员掉线 → 向仍在通话中的成员广播退出通知
        broadcastParticipant(room, "leave", member.getUserId(), "timeout", member.getUserId());
        fireMemberLeave(room.getCallId(), room.getRoomId(), member.getUserId(), "disconnect");
        // 掉线导致仅剩一人时同样自动结束通话
        maybeEndWhenLoneRemaining(room, member.getUserId());
    }

    @Override
    public void onInviteTimeout(GroupCallRoom room) {
        // 邀请超时 → 广播通话结束给除发起人外的所有成员
        broadcastParticipant(room, "ended", room.getInitiatorId(), "timeout", room.getInitiatorId());
        fireCallEnd(room, "timeout");
    }

    @Override
    public void onRoomRecycled(GroupCallRoom room) {
        // 空房间回收（全员离开后无人在房，未走 endRoom 流程）→ 补发业务结束事件
        fireCallEnd(room, "empty");
    }

    // ====================== 仅剩一人兜底结束 ======================

    /**
     * 成员离开后兜底：房间已进入通话中且仅剩一名成员时自动结束通话
     *
     * 最后一人已无对端可通话（服务端不感知媒体层，生命周期信令是唯一通知渠道），
     * 不自动结束会导致其永远停留在通话页且持续占用该群通话配额。
     * 结束后向全部成员广播 ended（含已离开成员，便于客户端清理界面），
     * 并触发业务结束回调补齐话单；SFU 模式同步销毁媒体房间。
     *
     * @param room       成员离开后的房间（可能已结束）
     * @param leftUserId 触发离开的成员（广播时排除）
     */
    private void maybeEndWhenLoneRemaining(GroupCallRoom room, String leftUserId) {
        GroupCallRoom current = sessionManager.getRoom(room.getRoomId());
        // 仅 TALKING 且只剩一人时兜底；RINGING 阶段仅发起人一人为正常状态
        if (current == null
                || current.getStatus() != GroupCallRoomStatus.TALKING
                || current.getJoinedMemberIds().size() != 1) {
            return;
        }
        GroupCallRoom ended = sessionManager.endRoom(current.getRoomId());
        if (ended == null) {
            return;
        }
        destroySfuRoom(ended);

        String remainingId = ended.getJoinedMemberIds().isEmpty()
                ? ended.getInitiatorId()
                : ended.getJoinedMemberIds().iterator().next();
        fireCallEnd(ended, "ended");
        broadcastParticipant(ended, "ended", remainingId, null, leftUserId);

        log.info("群通话仅剩一人自动结束: roomId={}, group={}, remaining={}",
                ended.getRoomId(), ended.getGroupId(), remainingId);
    }

    /**
     * 销毁 SFU 媒体房间（SFU 模式且配置了适配器时，失败不阻断后续广播流程）
     */
    private void destroySfuRoom(GroupCallRoom room) {
        if (room.getMode() != GroupCallMode.SFU || sessionManager.getSfuAdapter() == null) {
            return;
        }
        try {
            sessionManager.getSfuAdapter().destroyRoom(room.getRoomId());
        } catch (Exception e) {
            log.error("SFU 房间销毁失败, roomId={}", room.getRoomId(), e);
        }
    }

    // ====================== 业务事件 ======================

    private void fireCallStart(GroupCallRoom room) {
        for (ImGroupCallListener businessListener : businessListeners) {
            try {
                businessListener.onCallStart(room.getCallId(), room.getGroupId(), room.getRoomId(),
                        room.getInitiatorId(), room.getCallType(), room.getMode());
            } catch (Exception e) {
                log.error("群通话开始回调异常, roomId={}", room.getRoomId(), e);
            }
        }
    }

    private void fireCallEnd(GroupCallRoom room, String endReason) {
        for (ImGroupCallListener businessListener : businessListeners) {
            try {
                businessListener.onCallEnd(room.getCallId(), room.getGroupId(), room.getRoomId(),
                        room.getInitiatorId(), room.getCallType(), room.getMode(),
                        room.getDurationSeconds(), endReason);
            } catch (Exception e) {
                log.error("群通话结束回调异常, roomId={}, endReason={}", room.getRoomId(), endReason, e);
            }
        }
    }

    private void fireMemberJoin(String callId, String roomId, String userId) {
        for (ImGroupCallListener businessListener : businessListeners) {
            try {
                businessListener.onMemberJoin(callId, roomId, userId);
            } catch (Exception e) {
                log.error("群通话成员加入回调异常, roomId={}, userId={}", roomId, userId, e);
            }
        }
    }

    private void fireMemberLeave(String callId, String roomId, String userId, String reason) {
        for (ImGroupCallListener businessListener : businessListeners) {
            try {
                businessListener.onMemberLeave(callId, roomId, userId, reason);
            } catch (Exception e) {
                log.error("群通话成员离开回调异常, roomId={}, userId={}, reason={}", roomId, userId, reason, e);
            }
        }
    }

    // ====================== 信令构建与投递 ======================

    /**
     * 向单个用户投递信令
     *
     * @return true=投递成功, false=用户离线
     */
    private boolean notifyUser(String toUserId, ImProto.RtcGroup signal) {
        ImProto.Packet packet = PacketCodec.create(Cmd.RTC_GROUP, 0, signal);
        return routeToUser(toUserId, packet);
    }

    /**
     * 构建服务端下发的 RtcGroup 信令
     */
    private ImProto.RtcGroup buildServerSignal(int signalType, GroupCallRoom room, String payload) {
        ImProto.RtcGroup.Builder builder = ImProto.RtcGroup.newBuilder()
                .setSignalType(signalType)
                .setPayload(payload != null ? payload : "");
        if (room != null) {
            builder.setGroupId(room.getGroupId())
                    .setCallId(room.getCallId())
                    .setRoomId(room.getRoomId())
                    .setMode(room.getMode() == GroupCallMode.SFU ? 1 : 0);
        }
        return builder.build();
    }

    /**
     * 构建成员变更通知信令（无房间上下文时使用，如发起人占用拒绝）
     */
    private ImProto.RtcGroup buildParticipantSignal(GroupCallRoom room, String action, String targetUserId,
                                                    String reason, int memberCount) {
        GroupCallParticipantDto dto = new GroupCallParticipantDto();
        dto.setAction(action);
        dto.setUserId(targetUserId);
        dto.setReason(reason);
        dto.setMemberCount(memberCount);
        if (room != null) {
            dto.setMemberCount(room.getJoinedMemberIds().size());
            dto.setMembers(toMemberInfos(room));
        }
        return buildServerSignal(GroupSignalType.PARTICIPANT_NOTIFY.getCode(), room, GSON.toJson(dto));
    }

    /**
     * 广播成员变更通知
     *
     * @param action          join / leave / reject / ended
     * @param targetUserId    触发变更的成员
     * @param reason          变更原因（可空）
     * @param excludeUserId   不投递的用户（通常为触发者本身）
     */
    private void broadcastParticipant(GroupCallRoom room, String action, String targetUserId,
                                      String reason, String excludeUserId) {
        ImProto.RtcGroup signal = buildParticipantSignal(room, action, targetUserId, reason,
                room.getJoinedMemberIds().size());
        for (GroupCallMember member : room.getMembers().values()) {
            String memberId = member.getUserId();
            if (memberId.equals(excludeUserId)) {
                continue;
            }
            // "ended" 需通知所有成员便于清理界面；其余动作不通知已退出/已拒绝的成员
            if (!"ended".equals(action)
                    && (member.getStatus() == GroupCallMemberStatus.LEFT
                        || member.getStatus() == GroupCallMemberStatus.REJECTED)) {
                continue;
            }
            notifyUser(memberId, signal);
        }
    }

    /**
     * 下发房间状态快照（roomState=16）
     * SFU 模式附带接入 token，Mesh 模式附带 TURN/STUN 凭据
     */
    private void sendRoomState(GroupCallRoom room, String toUserId) {
        GroupRoomStateDto dto = new GroupRoomStateDto();
        dto.setRoomId(room.getRoomId());
        dto.setCallId(room.getCallId());
        dto.setGroupId(room.getGroupId());
        dto.setMode(room.getMode() == GroupCallMode.SFU ? "sfu" : "mesh");
        dto.setCallType(room.getCallType());
        dto.setInitiatorId(room.getInitiatorId());
        dto.setStatus(room.getStatus() == GroupCallRoomStatus.RINGING ? "ringing" : "talking");
        dto.setMembers(toMemberInfos(room));

        if (room.getMode() == GroupCallMode.SFU) {
            SfuAdapter sfu = sessionManager.getSfuAdapter();
            if (sfu != null) {
                SfuToken token = sfu.issueToken(room.getRoomId(), toUserId);
                dto.setSfuToken(token.getToken());
                dto.setSfuUrl(token.getUrl());
            }
        } else if (turnCredentialService != null) {
            dto.setTurnInfo(turnCredentialService.generateTurnInfo());
        }

        notifyUser(toUserId, buildServerSignal(GroupSignalType.ROOM_STATE.getCode(), room, GSON.toJson(dto)));
    }

    private List<GroupMemberInfoDto> toMemberInfos(GroupCallRoom room) {
        List<GroupMemberInfoDto> infos = new ArrayList<>();
        for (GroupCallMember member : room.getMembers().values()) {
            GroupMemberInfoDto info = new GroupMemberInfoDto(
                    member.getUserId(), member.getStatus().name().toLowerCase());
            info.setCamera(member.getCamera());
            info.setMic(member.getMic());
            infos.add(info);
        }
        return infos;
    }

    // ====================== 工具方法 ======================

    /**
     * 解析受邀成员列表：payload 指定 inviteeIds 优先，否则取群全部活跃成员（排除发起人）
     */
    private List<String> resolveInviteeIds(String groupId, String initiatorId, GroupCallRequestDto request) {
        List<String> result = new ArrayList<>();
        if (request.getInviteeIds() != null && !request.getInviteeIds().isEmpty()) {
            result.addAll(request.getInviteeIds());
            return result;
        }
        if (groupMemberProvider != null) {
            List<String> memberIds = groupMemberProvider.getGroupMemberUserIds(groupId);
            if (memberIds != null) {
                for (String memberId : memberIds) {
                    if (!memberId.equals(initiatorId) && !result.contains(memberId)) {
                        result.add(memberId);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 解析 mediaState payload：camera/mic 至少一项非 null，否则视为无效
     */
    private GroupMediaStateDto parseMediaState(String payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            GroupMediaStateDto dto = GSON.fromJson(payload, GroupMediaStateDto.class);
            return dto != null && (dto.getCamera() != null || dto.getMic() != null) ? dto : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 reject/leave payload 中解析原因（payload 可为空）
     */
    private String parseReason(String payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            SingleCallRejectDto dto = GSON.fromJson(payload, SingleCallRejectDto.class);
            return dto != null ? dto.getReason() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
