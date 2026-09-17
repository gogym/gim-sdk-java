package io.getbit.gim.webrtc.dto;

import lombok.Data;

/**
 * SingleCallRequestDto.java
 *
 * 1:1 通话呼叫请求 DTO
 * 用于 callRequest 信令（signalType=4）
 *
 * @author gogym
 */
@Data
public class SingleCallRequestDto {

    /**
     * 通话类型：audio-音频通话, video-视频通话
     */
    private String callType;
}
