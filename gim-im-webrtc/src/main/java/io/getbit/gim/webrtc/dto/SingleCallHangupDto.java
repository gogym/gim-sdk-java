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
 * SingleCallHangupDto.java
 *
 * 1:1 通话挂断 DTO
 * 用于 callHangup 信令（signalType=8）
 *
 * @author gogym
 */
@Data
public class SingleCallHangupDto {

    /**
     * 挂断原因
     * normal-正常挂断, timeout-超时, error-错误
     */
    private String reason;
}
