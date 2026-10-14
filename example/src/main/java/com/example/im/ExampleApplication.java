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

package com.example.im;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GIM IM SDK 对接使用示例（Spring Boot 方式）
 * <p>
 * ═══════════════════════════════════════════════════════
 * 接入步骤（3 步即可运行）：
 * ═══════════════════════════════════════════════════════
 * <p>
 * 1. 引入依赖：pom.xml 中添加 gim-im-starter
 * <pre>{@code
 * <dependency>
 *     <groupId>io.getbit</groupId>
 *     <artifactId>gim-im-starter</artifactId>
 *     <version>${gim.version}</version>
 * </dependency>
 * }</pre>
 * <p>
 * 2. 实现 SPI 接口（参见 spi/ 目录下的实现类）：
 * <ul>
 *   <li>{@code ImRedisAdapter}      — Redis 操作（必须）</li>
 *   <li>{@code ImTokenVerifier}     — Token 验证（必须）</li>
 *   <li>{@code ImIdGenerator}       — 消息 ID 生成（必须）</li>
 *   <li>{@code ImRedisSubscriber}   — Redis 订阅（集群模式必须）</li>
 *   <li>{@code ImGroupMemberProvider} — 群组成员查询（群聊功能必须）</li>
 *   <li>{@code ImFriendProvider}      — 好友关系查询（好友校验/在线状态同步必须）</li>
 *   <li>{@code ImEventListener}     — 事件监听（可选，消息持久化也通过此接口处理）</li>
 * </ul>
 * <p>
 * 3. 配置 application.yml（参见 resources/application.yml）
 * <p>
 * ═══════════════════════════════════════════════════════
 * SDK 自动装配的能力：
 * ═══════════════════════════════════════════════════════
 * <ul>
 *   <li>Netty IM 长连接服务器（端口可配置）</li>
 *   <li>消息路由（单机 / 集群）</li>
 *   <li>ACK 确认、心跳管理、自动重发</li>
 *   <li>WebRTC 信令处理</li>
 *   <li>健康指标（Actuator）</li>
 * </ul>
 * <p>
 * ═══════════════════════════════════════════════════════
 * 非 Spring 环境使用请参考 {@code NonSpringExample}
 * 集群模式请参考 {@link ClusterExample}
 * ═══════════════════════════════════════════════════════
 *
 * @author gogym
 */
@SpringBootApplication
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
