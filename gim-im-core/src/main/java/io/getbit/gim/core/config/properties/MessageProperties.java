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

package io.getbit.gim.core.config.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * MessageProperties.java
 *
 * 消息发送配置
 *
 * @author gogym
 */
@Getter
@Setter
public class MessageProperties {

    /** ACK 超时时间（秒） */
    private int ackTimeoutSeconds = 10;

    /**
     * 是否开启已读回执（对应 yml: gim.msg.read-receipt-enabled）
     * <p>
     * true（默认）：注册 ReadReceiptHandler，单聊回执实时转发给对方，群聊回执触发 onReadReceipt 回调
     * false：不注册该 Handler，客户端上报的已读回执由 Dispatcher 记日志后丢弃（回调同样不触发）
     */
    private boolean readReceiptEnabled = true;
}
