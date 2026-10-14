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
 * GroupCallMode.java
 *
 * 群通话媒体架构模式
 *
 * @author gogym
 */
public enum GroupCallMode {

    /**
     * Mesh 模式：成员间 P2P 直连，服务端只做信令协调（适合小群，默认上限 meshMaxMembers）
     */
    MESH,

    /**
     * SFU 模式：媒体流由外部 SFU（如 LiveKit）承载，SDK 负责房间协调与 token 签发（适合大群，20+ 人）
     */
    SFU
}
