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

package io.getbit.gim.webrtc.enums;

import lombok.Getter;

/**
 * 1:1 通话会话状态机
 * 仅含生命周期状态；结束原因（拒绝/取消/超时/忙线等）由 {@link CallEndReason} 承载，二者不重叠
 *
 * @author gogym
 */
@Getter
public enum SingleCallSessionStatus {
    CALLING(1, "呼叫中"), CONNECTING(2, "连接中"),
    TALKING(3, "通话中"), ENDED(4, "已结束");

    private final int code;
    private final String desc;
    SingleCallSessionStatus(int code, String desc) { this.code = code; this.desc = desc; }

}
