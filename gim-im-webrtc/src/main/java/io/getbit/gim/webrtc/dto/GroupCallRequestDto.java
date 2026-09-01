package io.getbit.gim.webrtc.dto;

import lombok.Data;

import java.util.List;

/**
 * GroupCallRequestDto.java
 *
 * WebRTC 群通话发起请求 DTO
 * 用于 groupCallRequest 信令（signalType=9，客户端 → 服务端）
 *
 * @author gogym
 */
@Data
public class GroupCallRequestDto {

    /**
     * 通话类型：audio-音频通话, video-视频通话
     */
    private String callType;

    /**
     * 受邀成员 userId 列表（可选，为空时邀请群内全部成员）
     */
    private List<String> inviteeIds;
}
