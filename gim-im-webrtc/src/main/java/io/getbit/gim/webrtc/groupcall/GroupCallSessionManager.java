package io.getbit.gim.webrtc.groupcall;

import io.getbit.gim.webrtc.enums.GroupCallMemberStatus;
import io.getbit.gim.webrtc.enums.GroupCallMode;
import io.getbit.gim.webrtc.enums.GroupCallRoomStatus;
import io.getbit.gim.webrtc.session.WebRtcSessionManager;
import io.getbit.gim.webrtc.sfu.SfuAdapter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * GroupCallSessionManager.java
 *
 * 群通话房间生命周期管理器（线程安全）
 * 职责：
 * 1. 房间状态机维护：CREATED → RINGING → TALKING → ENDED
 * 2. 成员状态机维护：INVITED → JOINED → LEFT / REJECTED
 * 3. 占用判定：单用户同时只允许在一个通话（1:1 或群）中，群房间与群房间可并存
 * 4. 超时策略：单成员无响应只标记 REJECTED 不销毁房间；
 *    RINGING 房间超过 inviteTimeoutSeconds 自动结束；
 *    全员离开后延迟 emptyRoomTtlSeconds 回收房间
 * 5. 掉线清理：onDisconnect 等价于被动 leave，事件通过 GroupCallListener 通知上层广播
 *
 * @author gogym
 */
@Slf4j
public class GroupCallSessionManager {

    /**
     * roomId -> 房间
     */
    private final Map<String, GroupCallRoom> rooms = new ConcurrentHashMap<>();

    /**
     * userId -> roomId（占用判定 + 掉线快速定位）
     */
    private final Map<String, String> userRoomMap = new ConcurrentHashMap<>();

    /**
     * 房间ID -> 空房间回收任务
     */
    private final Map<String, ScheduledFuture<?>> emptyRoomTasks = new ConcurrentHashMap<>();

    private final GroupCallConfig config;

    /**
     * SFU 适配器（未配置 SFU 时为 null，此时仅支持 Mesh 模式）
     */
    @Getter
    private final SfuAdapter sfuAdapter;

    /**
     * 1:1 通话会话管理器（用于群通话与 1:1 通话的互斥占用判定，可为 null）
     */
    private final WebRtcSessionManager oneToOneSessions;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "gim-group-call");
        t.setDaemon(true);
        return t;
    });

    @Setter
    private volatile GroupCallListener listener;

    public GroupCallSessionManager(GroupCallConfig config) {
        this(config, null, null);
    }

    public GroupCallSessionManager(GroupCallConfig config, SfuAdapter sfuAdapter) {
        this(config, sfuAdapter, null);
    }

    public GroupCallSessionManager(GroupCallConfig config, SfuAdapter sfuAdapter,
                                   WebRtcSessionManager oneToOneSessions) {
        this.config = config != null ? config : new GroupCallConfig();
        this.sfuAdapter = sfuAdapter;
        this.oneToOneSessions = oneToOneSessions;
    }

    // ====================== 房间生命周期 ======================

    /**
     * 创建群通话房间
     * 发起人立即以 JOINED 状态入房，受邀成员以 INVITED 状态登记
     *
     * @param groupId          群组ID
     * @param callId           通话唯一ID（客户端可携带，为空时服务端生成）
     * @param initiatorId      发起人 userId
     * @param callType         通话类型 audio / video
     * @param inviteeIds       受邀成员列表（不含发起人）
     * @param mode             媒体模式
     * @param initiatorChannel 发起人当前连接
     * @return 房间；发起人占用中或该群已有进行中的通话时返回 null
     */
    public synchronized GroupCallRoom createRoom(String groupId, String callId, String initiatorId,
                                                 String callType, List<String> inviteeIds,
                                                 GroupCallMode mode, io.netty.channel.Channel initiatorChannel) {
        if (isUserBusy(initiatorId)) {
            log.warn("创建群通话失败: 发起人通话占用中, userId={}", initiatorId);
            return null;
        }
        if (getRoomByGroup(groupId) != null) {
            log.warn("创建群通话失败: 群已有进行中的通话, groupId={}", groupId);
            return null;
        }

        String roomId = UUID.randomUUID().toString();
        String finalCallId = callId != null && !callId.isEmpty() ? callId : UUID.randomUUID().toString();
        GroupCallRoom room = new GroupCallRoom(roomId, finalCallId, groupId, initiatorId, mode, callType);

        GroupCallMember initiator = new GroupCallMember(initiatorId, GroupCallMemberStatus.JOINED);
        initiator.setJoinTime(System.currentTimeMillis());
        initiator.setChannel(initiatorChannel);
        room.addMember(initiator);

        if (inviteeIds != null) {
            for (String inviteeId : inviteeIds) {
                if (inviteeId == null || inviteeId.isEmpty() || inviteeId.equals(initiatorId)
                        || room.getMember(inviteeId) != null) {
                    continue;
                }
                room.addMember(new GroupCallMember(inviteeId, GroupCallMemberStatus.INVITED));
            }
        }

        room.setStatus(GroupCallRoomStatus.RINGING);
        rooms.put(roomId, room);
        userRoomMap.put(initiatorId, roomId);
        for (GroupCallMember member : room.getMembers().values()) {
            if (member != initiator) {
                userRoomMap.put(member.getUserId(), roomId);
            }
        }
        scheduleInviteTimeout(roomId);

        log.info("群通话房间已创建: roomId={}, group={}, mode={}, initiator={}, invitees={}",
                roomId, groupId, mode, initiatorId, room.getMembers().size() - 1);
        return room;
    }

    /**
     * 成员加入房间（接受邀请）
     *
     * @return 加入后的房间；房间不存在、未受邀或已拒绝时返回 null
     */
    public synchronized GroupCallRoom joinRoom(String roomId, String userId, io.netty.channel.Channel channel) {
        GroupCallRoom room = rooms.get(roomId);
        if (room == null || room.getStatus() == GroupCallRoomStatus.ENDED) {
            log.warn("加入群通话失败: 房间不存在或已结束, roomId={}, userId={}", roomId, userId);
            return null;
        }
        GroupCallMember member = room.getMember(userId);
        if (member == null) {
            log.warn("加入群通话失败: 用户不在成员列表, roomId={}, userId={}", roomId, userId);
            return null;
        }
        if (member.getStatus() == GroupCallMemberStatus.REJECTED
                || member.getStatus() == GroupCallMemberStatus.LEFT) {
            log.warn("加入群通话失败: 成员已拒绝或退出, roomId={}, userId={}, status={}",
                    roomId, userId, member.getStatus());
            return null;
        }
        if (member.getStatus() == GroupCallMemberStatus.JOINED) {
            // 幂等：重复 join 直接返回房间（刷新连接）
            member.setChannel(channel);
            return room;
        }

        member.setStatus(GroupCallMemberStatus.JOINED);
        member.setJoinTime(System.currentTimeMillis());
        member.setChannel(channel);
        userRoomMap.put(userId, roomId);

        // 第一名非发起人加入后进入通话中状态，取消邀请超时
        if (room.getStatus() == GroupCallRoomStatus.RINGING) {
            room.setStatus(GroupCallRoomStatus.TALKING);
            room.setTalkTime(System.currentTimeMillis());
            cancelInviteTimeout(roomId);
        }

        log.info("成员加入群通话: roomId={}, userId={}, joined={}",
                roomId, userId, room.getJoinedMemberIds().size());
        return room;
    }

    /**
     * 成员主动退出房间
     *
     * @return 被移除的成员；房间不存在或成员不在房中返回 null
     */
    public synchronized GroupCallMember leaveRoom(String roomId, String userId) {
        GroupCallRoom room = rooms.get(roomId);
        if (room == null) {
            return null;
        }
        GroupCallMember member = room.getMember(userId);
        if (member == null || member.getStatus() == GroupCallMemberStatus.LEFT
                || member.getStatus() == GroupCallMemberStatus.REJECTED) {
            return null;
        }

        member.setStatus(GroupCallMemberStatus.LEFT);
        member.setChannel(null);
        userRoomMap.remove(userId, roomId);
        scheduleEmptyRoomCleanup(room);

        log.info("成员退出群通话: roomId={}, userId={}, joinedLeft={}", roomId, userId, room.getJoinedMemberIds().size());
        return member;
    }

    /**
     * 成员拒绝邀请
     *
     * @return 被更新的成员；房间不存在或成员不在房中返回 null
     */
    public synchronized GroupCallMember rejectInvite(String roomId, String userId) {
        GroupCallRoom room = rooms.get(roomId);
        if (room == null) {
            return null;
        }
        GroupCallMember member = room.getMember(userId);
        if (member == null || member.getStatus() != GroupCallMemberStatus.INVITED) {
            return null;
        }

        member.setStatus(GroupCallMemberStatus.REJECTED);
        userRoomMap.remove(userId, roomId);

        log.info("成员拒绝群通话邀请: roomId={}, userId={}", roomId, userId);
        return member;
    }

    /**
     * 结束房间（发起人结束全员通话或超时自动结束）
     *
     * @return 已结束的房间；房间不存在返回 null
     */
    public synchronized GroupCallRoom endRoom(String roomId) {
        GroupCallRoom room = rooms.remove(roomId);
        if (room == null) {
            return null;
        }

        cancelInviteTimeout(roomId);
        cancelEmptyRoomCleanup(roomId);

        room.setStatus(GroupCallRoomStatus.ENDED);
        for (GroupCallMember member : room.getMembers().values()) {
            userRoomMap.remove(member.getUserId(), roomId);
        }

        log.info("群通话房间已结束: roomId={}, group={}, status={}", roomId, room.getGroupId(), room.getStatus());
        return room;
    }

    /**
     * 用户连接断开：清理其所在房间中的成员状态（等价于被动 leave）
     * 在锁外触发 GroupCallListener.onMemberDisconnected 供上层广播
     */
    public void onDisconnect(String userId) {
        GroupCallRoom room;
        GroupCallMember member;
        synchronized (this) {
            String roomId = userRoomMap.remove(userId);
            if (roomId == null) {
                return;
            }
            room = rooms.get(roomId);
            if (room == null) {
                return;
            }
            member = room.getMember(userId);
            if (member == null || member.getStatus() == GroupCallMemberStatus.LEFT
                    || member.getStatus() == GroupCallMemberStatus.REJECTED) {
                return;
            }
            member.setStatus(GroupCallMemberStatus.LEFT);
            member.setChannel(null);
            scheduleEmptyRoomCleanup(room);
        }

        log.info("群通话成员掉线清理: roomId={}, userId={}", room.getRoomId(), userId);
        GroupCallListener l = listener;
        if (l != null) {
            try {
                l.onMemberDisconnected(room, member);
            } catch (Exception e) {
                log.error("群通话掉线事件回调异常", e);
            }
        }
    }

    // ====================== 查询 ======================

    public GroupCallRoom getRoom(String roomId) {
        return roomId == null ? null : rooms.get(roomId);
    }

    public GroupCallRoom getRoomByUser(String userId) {
        String roomId = userRoomMap.get(userId);
        return roomId == null ? null : rooms.get(roomId);
    }

    /**
     * 查询群组当前进行中的通话房间（最多一个）
     */
    public GroupCallRoom getRoomByGroup(String groupId) {
        for (GroupCallRoom room : rooms.values()) {
            if (room.getGroupId().equals(groupId) && room.getStatus() != GroupCallRoomStatus.ENDED) {
                return room;
            }
        }
        return null;
    }

    /**
     * 占用判定：用户是否在任一通话（群通话或 1:1 通话）中
     */
    public boolean isUserBusy(String userId) {
        if (userRoomMap.containsKey(userId)) {
            return true;
        }
        return oneToOneSessions != null && oneToOneSessions.isInCall(userId);
    }

    public int getRoomCount() {
        return rooms.size();
    }

    // ====================== 模式选择 ======================

    /**
     * 按配置策略选择媒体模式
     * auto：人数 ≤ meshMaxMembers 用 Mesh，否则用 SFU（未配置 SFU 时回退 Mesh 并告警）
     */
    public GroupCallMode selectMode(int memberCount) {
        String mode = config.getMode();
        if ("sfu".equalsIgnoreCase(mode)) {
            if (sfuAdapter == null) {
                log.warn("群通话配置为 SFU 模式但未配置 SfuAdapter，回退 Mesh 模式");
                return GroupCallMode.MESH;
            }
            return GroupCallMode.SFU;
        }
        if ("mesh".equalsIgnoreCase(mode)) {
            return GroupCallMode.MESH;
        }
        // auto
        if (memberCount > config.getMeshMaxMembers()) {
            if (sfuAdapter != null) {
                return GroupCallMode.SFU;
            }
            log.warn("群通话人数 {} 超过 Mesh 上线 {} 且未配置 SfuAdapter，仍使用 Mesh 模式",
                    memberCount, config.getMeshMaxMembers());
        }
        return GroupCallMode.MESH;
    }

    // ====================== 超时任务 ======================

    private void scheduleInviteTimeout(String roomId) {
        int timeout = Math.max(1, config.getInviteTimeoutSeconds());
        scheduler.schedule(() -> {
            GroupCallRoom room = null;
            synchronized (this) {
                GroupCallRoom current = rooms.get(roomId);
                if (current != null && current.getStatus() == GroupCallRoomStatus.RINGING) {
                    room = endRoom(roomId);
                }
            }
            if (room != null) {
                log.info("群通话邀请超时自动结束: roomId={}", roomId);
                GroupCallListener l = listener;
                if (l != null) {
                    try {
                        l.onInviteTimeout(room);
                    } catch (Exception e) {
                        log.error("群通话邀请超时事件回调异常", e);
                    }
                }
            }
        }, timeout, TimeUnit.SECONDS);
    }

    private void scheduleEmptyRoomCleanup(GroupCallRoom room) {
        if (room.hasJoinedMember()) {
            return;
        }
        cancelEmptyRoomCleanup(room.getRoomId());
        int ttl = Math.max(1, config.getEmptyRoomTtlSeconds());
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            synchronized (this) {
                GroupCallRoom current = rooms.get(room.getRoomId());
                // 期间有成员重新加入或房间已结束则跳过回收
                if (current != null && !current.hasJoinedMember()) {
                    rooms.remove(room.getRoomId());
                    emptyRoomTasks.remove(room.getRoomId());
                    log.info("空群通话房间已回收: roomId={}", room.getRoomId());
                }
            }
        }, ttl, TimeUnit.SECONDS);
        emptyRoomTasks.put(room.getRoomId(), future);
    }

    private void cancelInviteTimeout(String roomId) {
        // 邀请超时任务通过房间状态判定失效，无需显式取消
    }

    private void cancelEmptyRoomCleanup(String roomId) {
        ScheduledFuture<?> future = emptyRoomTasks.remove(roomId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 关闭会话管理器，释放资源（应用下线时调用）
     */
    public List<GroupCallRoom> shutdown() {
        scheduler.shutdownNow();
        List<GroupCallRoom> all = new ArrayList<>(rooms.values());
        rooms.clear();
        userRoomMap.clear();
        emptyRoomTasks.clear();
        return all;
    }
}
