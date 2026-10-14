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

/**
 * GroupCallRoomStatus.java
 *
 * 群通话房间状态机：CREATED → RINGING → TALKING → ENDED
 *
 * @author gogym
 */
public enum GroupCallRoomStatus {

    /**
     * 已创建（瞬时态，创建后立即进入 RINGING）
     */
    CREATED,

    /**
     * 邀请中（等待成员响应）
     */
    RINGING,

    /**
     * 通话中（至少两名成员已加入）
     */
    TALKING,

    /**
     * 已结束
     */
    ENDED
}
