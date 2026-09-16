package io.getbit.gim.webrtc.groupcall;

import io.getbit.gim.webrtc.enums.GroupCallMode;
import io.getbit.gim.webrtc.enums.GroupCallRoomStatus;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * GroupCallRoom.java
 * <p>
 * 群通话房间
 * 由 GroupCallSessionManager 创建和管理，承载房间元信息与成员状态
 *
 * @author gogym
 */
@Getter
public class GroupCallRoom {

    private final String roomId;

    private final String callId;

    private final String groupId;

    private final String initiatorId;

    private final GroupCallMode mode;

    private final String callType;

    private volatile GroupCallRoomStatus status;

    /**
     * userId -> 成员（包含所有被邀请过的成员，状态见 GroupCallMemberStatus）
     */
    private final Map<String, GroupCallMember> members = new ConcurrentHashMap<>();

    private final long createTime;

    /**
     * 进入通话中的时间（毫秒）
     */
    private volatile long talkTime;

    public GroupCallRoom(String roomId, String callId, String groupId, String initiatorId,
                         GroupCallMode mode, String callType) {
        this.roomId = roomId;
        this.callId = callId;
        this.groupId = groupId;
        this.initiatorId = initiatorId;
        this.mode = mode;
        this.callType = callType;
        this.status = GroupCallRoomStatus.CREATED;
        this.createTime = System.currentTimeMillis();
    }

    void setStatus(GroupCallRoomStatus status) {
        this.status = status;
    }

    void setTalkTime(long talkTime) {
        this.talkTime = talkTime;
    }

    public GroupCallMember getMember(String userId) {
        return userId == null ? null : members.get(userId);
    }

    public void addMember(GroupCallMember member) {
        members.put(member.getUserId(), member);
    }

    /**
     * 获取所有已加入通话的成员
     */
    public List<GroupCallMember> getJoinedMembers() {
        return members.values().stream()
                .filter(GroupCallMember::isJoined)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有已加入通话的成员 userId
     */
    public List<String> getJoinedMemberIds() {
        return getJoinedMembers().stream()
                .map(GroupCallMember::getUserId)
                .collect(Collectors.toList());
    }

    /**
     * 是否还有成员在通话中
     */
    public boolean hasJoinedMember() {
        return members.values().stream().anyMatch(GroupCallMember::isJoined);
    }

    public boolean isParticipant(String userId) {
        return userId != null && members.containsKey(userId);
    }

    public long getDurationSeconds() {
        if (talkTime <= 0) {
            return 0;
        }
        long end = status == GroupCallRoomStatus.ENDED ? 0 : System.currentTimeMillis();
        return (end > 0 ? end - talkTime : 0) / 1000;
    }
}
