package io.getbit.gim.webrtc.dto;

import lombok.Data;

import java.util.List;

/**
 * GroupCallParticipantDto.java
 *
 * WebRTC 群通话成员变更通知 DTO
 * 用于 participantNotify 信令（signalType=15，服务端 → 成员）
 *
 * @author gogym
 */
@Data
public class GroupCallParticipantDto {

    /**
     * 变更动作：join-加入 leave-退出 reject-拒绝 ended-通话结束
     */
    private String action;

    /**
     * 触发变更的成员 userId
     */
    private String userId;

    /**
     * 变更原因（leave/reject 时可携带，如 timeout、busy）
     */
    private String reason;

    /**
     * 当前仍在通话中的成员数
     */
    private int memberCount;

    /**
     * 仍在通话中的成员快照
     */
    private List<GroupMemberInfoDto> members;
}
