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

package io.getbit.gim.core.connection.server;

import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.core.connection.auth.ConnectionAuthHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * GimServerInitializer.java
 *
 * Netty 处理器配置
 * pipeline 顺序：
 * 1. IdleStateHandler - 空闲检测（读超时 = 心跳间隔 × 3）
 * 2. ProtobufVarint32FrameDecoder - 帧解码
 * 3. ProtobufDecoder - Protobuf 反序列化为 Packet
 * 4. ProtobufVarint32LengthFieldPrepender - 帧编码
 * 5. ProtobufEncoder - Protobuf 序列化
 * 6. ChatServerHandler - 业务处理
 *
 * @author gogym
 */
public class GimServerInitializer extends ChannelInitializer<SocketChannel> {

    private final IMServerFacade facade;
    private final ConnectionAuthHandler authHandler;

    /**
     * 读超时（秒）：超过此时间未收到任何消息则触发 userEventTriggered
     */
    private final int readerIdleSeconds;

    public GimServerInitializer(IMServerFacade facade, ConnectionAuthHandler authHandler) {
        this.facade = facade;
        this.authHandler = authHandler;
        int heartbeatInterval = facade.getConfig().getHeartBeatInterval();
        this.readerIdleSeconds = Math.max(heartbeatInterval * 3, 30);
        this.enableHeartBeat = facade.getConfig().isEnableHeartBeat();
    }

    private final boolean enableHeartBeat;

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // 空闲检测（仅在开启心跳时添加）
        if (enableHeartBeat) {
            pipeline.addLast("idleState", new IdleStateHandler(readerIdleSeconds, 0, 0, TimeUnit.SECONDS));
        }

        // Protobuf 解码
        pipeline.addLast("frameDecoder", new ProtobufVarint32FrameDecoder());
        pipeline.addLast("protobufDecoder", new ProtobufDecoder(ImProto.Packet.getDefaultInstance()));

        // Protobuf 编码
        pipeline.addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender());
        pipeline.addLast("protobufEncoder", new ProtobufEncoder());

        // 业务处理器
        pipeline.addLast("chatHandler", new ChatServerHandler(facade, authHandler));
    }
}
