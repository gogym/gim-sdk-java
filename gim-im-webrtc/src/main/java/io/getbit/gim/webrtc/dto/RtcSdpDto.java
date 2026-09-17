package io.getbit.gim.webrtc.dto;

import lombok.Data;

/**
 * RtcSdpDto.java
 *
 * WebRTC SDP DTO（1:1 与群 Mesh 共用的媒体信令）
 * 用于 offer/answer 信令（signalType=1,2）
 *
 * @author gogym
 */
@Data
public class RtcSdpDto {

    /**
     * SDP 内容
     */
    private String sdp;
}
