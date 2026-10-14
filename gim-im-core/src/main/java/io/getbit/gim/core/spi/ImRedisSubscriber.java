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

package io.getbit.gim.core.spi;

import java.util.function.Consumer;

/**
 * ImRedisSubscriber.java
 *
 * SPI接口：Redis Pub/Sub 订阅器
 * 用于集群模式下节点间消息转发
 * 使用方需自行实现（Jedis / Lettuce / Redisson）
 *
 * @author gogym
 */
public interface ImRedisSubscriber {

    /**
     * 订阅指定的 Redis channel（阻塞调用，需在独立线程中执行）
     *
     * @param channel  Redis channel name
     * @param callback 消息回调
     */
    void subscribe(String channel, Consumer<String> callback);

    /**
     * 取消订阅
     */
    void unsubscribe();

    /**
     * 检查是否已订阅
     *
     * @return true if subscribed
     */
    boolean isSubscribed();
}
