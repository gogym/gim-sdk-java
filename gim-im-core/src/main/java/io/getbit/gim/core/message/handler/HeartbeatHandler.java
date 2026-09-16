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
