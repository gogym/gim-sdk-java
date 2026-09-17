package io.getbit.gim.webrtc.dto;

import lombok.Data;

/**
 * RtcIceCandidateDto.java
 *
 * WebRTC ICE 候选者 DTO（1:1 与群 Mesh 共用的媒体信令）
 * 用于 iceCandidate 信令（signalType=3）
 *
 * @author gogym
 */
@Data
public class RtcIceCandidateDto {

    /**
     * ICE Candidate 内容
     */
    private String candidate;

    /**
     * ICE Candidate 的 SDP Mid
     */
    private String sdpMid;

    /**
     * ICE Candidate 的 SDP MLine Index
     */
    private Integer sdpMLineIndex;
}
