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
