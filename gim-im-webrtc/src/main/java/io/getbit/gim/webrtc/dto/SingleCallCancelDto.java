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
 * SingleCallCancelDto.java
 *
 * 1:1 通话取消呼叫 DTO
 * 用于 callCancel 信令（signalType=7）
 *
 * @author gogym
 */
@Data
public class SingleCallCancelDto {

    /**
     * 取消原因（如 cancel）
     */
    private String reason;
}
