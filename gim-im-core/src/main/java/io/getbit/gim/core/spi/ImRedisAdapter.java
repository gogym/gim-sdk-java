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

/**
 * ImRedisAdapter.java
 *
 * SPI接口：Redis操作适配器
 * 使用方可自行实现此接口对接不同的Redis客户端（Jedis / Lettuce / Redisson 等）
 *
 * @author gogym
 */
public interface ImRedisAdapter {

    /**
     * SET with expiration
     *
     * @param key     Redis key
     * @param seconds TTL in seconds
     * @param value   value
     */
    void setex(String key, int seconds, String value);

    /**
     * GET
     *
     * @param key Redis key
     * @return value, or null if not exists
     */
    String get(String key);

    /**
     * SET NX with expiration（原子占位）
     * 默认返回 false 表示未实现，调用方将降级为 GET + SETEX 的非原子占位（存在极小竞态窗口）
     *
     * @param key     Redis key
     * @param value   value
     * @param seconds TTL in seconds
     * @return true=占位成功, false=key 已存在或实现方不支持
     */
    default boolean setnx(String key, String value, int seconds) {
        return false;
    }

    /**
     * DELETE
     *
     * @param key Redis key
     */
    void del(String key);

    /**
     * PUBLISH message to a Redis channel
     *
     * @param channel Redis Pub/Sub channel
     * @param message message body
     */
    void publish(String channel, String message);
}
