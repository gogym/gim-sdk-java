package io.getbit.gim.webrtc.dto;

import lombok.Data;

/**
 * WebRTC 媒体开关状态 DTO
 * 用于 mediaState 信令（signalType=17，对端切换摄像头/麦克风时通知）
 * camera/mic 至少一项非 null
 *
 * @author gogym
 */
@Data
public class WebRtcMediaStateDto {

    /**
     * 摄像头开关状态（null 表示该项未变化）
     */
    private Boolean camera;

    /**
     * 麦克风开关状态（null 表示该项未变化）
     */
    private Boolean mic;
}
