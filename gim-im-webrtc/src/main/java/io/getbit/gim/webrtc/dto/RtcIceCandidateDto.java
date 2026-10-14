/*
 * Copyright 2026 gogym
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
