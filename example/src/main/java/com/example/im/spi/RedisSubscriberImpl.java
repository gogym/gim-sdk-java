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

package com.example.im.spi;

import io.getbit.gim.core.spi.ImRedisSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Redis Pub/Sub 订阅器实现 — 基于 Spring Data Redis
 * <p>
 * SDK 在集群模式下通过 Redis Pub/Sub 转发跨节点消息。
 * 单机模式下 SDK 会自动使用 NoOp 实现，此处可保留但不影响运行。
 *
 * @author gogym
 */
@Component
@Slf4j
public class RedisSubscriberImpl implements ImRedisSubscriber {


    private final RedisConnectionFactory connectionFactory;
    private final AtomicBoolean subscribed = new AtomicBoolean(false);
    private RedisMessageListenerContainer container;

    public RedisSubscriberImpl(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void subscribe(String channel, Consumer<String> callback) {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.afterPropertiesSet();

        MessageListener listener = (message, pattern) -> {
            try {
                String body = new String(message.getBody());
                callback.accept(body);
            } catch (Exception e) {
                log.error("Redis 消息处理失败: channel={}", channel, e);
            }
        };

        container.addMessageListener(listener, new ChannelTopic(channel));
        container.start();
        subscribed.set(true);
        log.info("已订阅 Redis 频道: {}", channel);
    }

    @Override
    public void unsubscribe() {
        if (container != null) {
            container.stop();
            subscribed.set(false);
        }
    }

    @Override
    public boolean isSubscribed() {
        return subscribed.get();
    }
}
