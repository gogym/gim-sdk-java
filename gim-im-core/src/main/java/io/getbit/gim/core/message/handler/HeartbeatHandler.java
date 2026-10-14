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

import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.protocol.codec.Cmd;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.protocol.codec.PacketCodec;
import io.netty.channel.Channel;

import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * HeartbeatHandler.java
 *
 * 心跳处理器 — 响应心跳 + 续期路由
 *
 * @author gogym
 */
@Slf4j
public class HeartbeatHandler extends BaseHandler {

    public HeartbeatHandler(IMServerFacade facade) {
        super(facade);
    }

    @Override
    public int cmd() {
        return Cmd.HEARTBEAT_REQ;
    }

    @Override
    public void handle(ImProto.Packet packet, Channel channel, String userId) {
        ImProto.Packet resp = PacketCodec.buildHeartbeatResp(packet.getSequence());
        channel.writeAndFlush(resp);

        // 续期用户路由（集群模式下保持路由有效）
        userRouteService.renew(userId);

        log.debug("[{}] 心跳响应, userId={}", channel.id().asShortText(), userId);
    }
}
