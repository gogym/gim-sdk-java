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

package io.getbit.gim.core.message.handler;

import io.getbit.gim.protocol.codec.ImProto;
import io.netty.channel.Channel;

/**
 * MessageDispatcher.java
 *
 * 消息分发器接口
 * ChatServerHandler 将已认证的消息按 cmd 分发到具体业务处理器
 *
 * @author gogym
 */
public interface MessageDispatcher {

    /**
     * 按 packet.cmd 路由到对应 Handler
     */
    void dispatch(ImProto.Packet packet, Channel channel, String userId);

    /**
     * 动态注册 Handler（用于外部模块扩展）
     *
     * @param handler 消息处理器
     */
    void registerHandler(BaseHandler handler);
}
