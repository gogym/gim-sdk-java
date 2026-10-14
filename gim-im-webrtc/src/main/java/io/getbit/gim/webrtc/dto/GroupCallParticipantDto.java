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
 * GroupCallParticipantDto.java
 *
 * WebRTC 群通话成员变更通知 DTO
 * 用于 participantNotify 信令（signalType=26，服务端 → 成员）
 *
 * @author gogym
 */
@Data
public class GroupCallParticipantDto {

    /**
     * 变更动作：join-加入 leave-退出 reject-拒绝 ended-通话结束
     */
    private String action;

    /**
     * 触发变更的成员 userId
     */
    private String userId;

    /**
     * 变更原因（leave/reject 时可携带，如 timeout、busy）
     */
    private String reason;

    /**
     * 当前仍在通话中的成员数
     */
    private int memberCount;

    /**
     * 仍在通话中的成员快照
     */
    private List<GroupMemberInfoDto> members;
}
