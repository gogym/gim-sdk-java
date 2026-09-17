package io.getbit.gim.webrtc.dto;

import lombok.Data;

import java.util.List;

/**
 * GroupRoomStateDto.java
 *
 * WebRTC 群通话房间状态 DTO
 * 用于 roomState 信令（signalType=27，服务端 → 发起人/加入者）
 * 加入者据此初始化客户端房间视图：Mesh 模式下对成员逐一建连，SFU 模式下用 token 连接 SFU
 *
 * @author gogym
 */
@Data
public class GroupRoomStateDto {

    /**
     * 群通话房间唯一ID
     */
    private String roomId;

    /**
     * 通话唯一ID
     */
    private String callId;

    /**
     * 群组ID
     */
    private String groupId;

    /**
     * 媒体模式：mesh / sfu
     */
    private String mode;

    /**
     * 通话类型：audio / video
     */
    private String callType;

    /**
     * 发起人 userId
     */
    private String initiatorId;

    /**
     * 房间状态：ringing / talking
     */
    private String status;

    /**
     * 成员快照（含状态）
     */
    private List<GroupMemberInfoDto> members;

    /**
     * SFU 接入 token（仅 SFU 模式）
     */
    private String sfuToken;

    /**
     * SFU 连接地址（仅 SFU 模式）
     */
    private String sfuUrl;

    /**
     * TURN/STUN 凭据（仅 Mesh 模式）
     */
    private TurnCredentialsDto turnInfo;
}
