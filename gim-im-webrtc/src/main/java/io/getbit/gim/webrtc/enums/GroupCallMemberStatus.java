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
 * GroupCallMemberStatus.java
 *
 * 群通话成员状态机：INVITED → JOINED → LEFT（或 INVITED → REJECTED）
 *
 * @author gogym
 */
public enum GroupCallMemberStatus {

    /**
     * 已邀请，未响应
     */
    INVITED,

    /**
     * 已加入通话
     */
    JOINED,

    /**
     * 已退出（主动退出或掉线）
     */
    LEFT,

    /**
     * 已拒绝邀请
     */
    REJECTED
}
