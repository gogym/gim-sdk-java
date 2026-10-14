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
 * NettyProperties.java
 * <p>
 * Netty服务器配置
 *
 * @author gogym
 */
@Getter
@Setter
public class NettyProperties {

    /**
     * 监听端口
     */
    private int port = 3333;

    /**
     * Boss线程数
     */
    private int bossThreads = 1;

    /**
     * Worker线程数（0=自动检测CPU核心数）
     */
    private int workerThreads = 0;

    /**
     * 连接队列大小
     */
    private int backlog = 512;
}
