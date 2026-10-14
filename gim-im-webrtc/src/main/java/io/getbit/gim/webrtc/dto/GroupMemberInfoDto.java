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
 * GroupMemberInfoDto.java
 *
 * 群通话成员快照信息
 *
 * @author gogym
 */
@Data
public class GroupMemberInfoDto {

    private String userId;

    /**
     * 成员状态：invited / joined / left / rejected
     */
    private String status;

    /**
     * 摄像头开关状态（null 表示未上报）
     */
    private Boolean camera;

    /**
     * 麦克风开关状态（null 表示未上报）
     */
    private Boolean mic;

    public GroupMemberInfoDto() {
    }

    public GroupMemberInfoDto(String userId, String status) {
        this.userId = userId;
        this.status = status;
    }
}
