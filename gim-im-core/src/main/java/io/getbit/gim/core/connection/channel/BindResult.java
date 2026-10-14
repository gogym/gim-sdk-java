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

package io.getbit.gim.core.connection.channel;

import io.netty.channel.Channel;
import lombok.Getter;

/**
 * BindResult.java
 *
 * 通道绑定结果：旧连接及其连接信息（无旧连接时 ChannelManager.bind 返回 null）
 *
 * @author gogym
 */
@Getter
public class BindResult {

    /**
     * 被替换的旧连接（同设备互踢）
     */
    private final Channel oldChannel;

    /**
     * 旧连接的 ConnectionInfo（含 deviceId，供互踢判定）
     */
    private final ConnectionInfo oldInfo;

    public BindResult(Channel oldChannel, ConnectionInfo oldInfo) {
        this.oldChannel = oldChannel;
        this.oldInfo = oldInfo;
    }

}
