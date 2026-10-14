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
