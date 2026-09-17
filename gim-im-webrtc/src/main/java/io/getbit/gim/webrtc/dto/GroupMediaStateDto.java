package io.getbit.gim.webrtc.dto;

import lombok.Data;

/**
 * GroupMediaStateDto.java
 *
 * 群通话媒体开关状态 DTO
 * 用于 mediaState 信令（signalType=100，成员切换摄像头/麦克风时上报，服务端广播给其他在通话成员）
 * camera/mic 至少一项非 null
 *
 * @author gogym
 */
@Data
public class GroupMediaStateDto {

    /**
     * 摄像头开关状态（null 表示该项未变化）
     */
    private Boolean camera;

    /**
     * 麦克风开关状态（null 表示该项未变化）
     */
    private Boolean mic;
}
