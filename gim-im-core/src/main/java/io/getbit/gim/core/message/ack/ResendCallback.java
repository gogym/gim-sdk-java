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
 * ResendCallback.java
 *
 * 消息重发回调 SPI
 * ACK 超时且开启自动重发时，由 MessageAckTracker 调用，实现方负责将消息重新投递给接收者
 *
 * @author gogym
 */
@FunctionalInterface
public interface ResendCallback {

    /**
     * 重发消息
     *
     * @param receiverId 接收者 userId
     * @param packet     原始消息包
     */
    void resend(String receiverId, ImProto.Packet packet);
}
