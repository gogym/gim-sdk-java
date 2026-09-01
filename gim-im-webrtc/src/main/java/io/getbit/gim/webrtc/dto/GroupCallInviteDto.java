package io.getbit.gim.webrtc.dto;

import lombok.Data;

/**
 * GroupCallInviteDto.java
 *
 * WebRTC 群通话邀请 DTO
 * 用于 groupCallInvite 信令（signalType=10，服务端 → 被邀成员）
 *
 * @author gogym
 */
@Data
public class GroupCallInviteDto {

    /**
     * 通话类型：audio / video
     */
    private String callType;

    /**
     * 群组ID
     */
    private String groupId;

    /**
     * 发起人 userId
     */
    private String initiatorId;

    /**
     * 媒体模式：mesh / sfu
     */
    private String mode;
}
