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

package io.getbit.gim.core.message.ack;

import io.getbit.gim.protocol.codec.ImProto;

/**
 * AckInfo.java
 *
 * 待 ACK 消息追踪记录（包级私有，仅供 MessageAckTracker 使用）
 *
 * @author gogym
 */
class AckInfo {

    final String receiverId;
    final long sentAt;
    final ImProto.Packet packet;
    final int retryCount;

    AckInfo(String receiverId, long sentAt, ImProto.Packet packet, int retryCount) {
        this.receiverId = receiverId;
        this.sentAt = sentAt;
        this.packet = packet;
        this.retryCount = retryCount;
    }
}
