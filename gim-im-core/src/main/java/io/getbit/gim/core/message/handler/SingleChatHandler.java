package io.getbit.gim.core.message.handler;

import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.message.ack.MessageAckTracker;
import io.getbit.gim.core.spi.ImFriendProvider;
import io.getbit.gim.core.spi.ImIdGenerator;
import io.getbit.gim.protocol.codec.Cmd;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.protocol.codec.PacketCodec;
import io.netty.channel.Channel;

import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * SingleChatHandler.java
 *
 * 单聊消息处理器
 *
 * 处理流程：
 * 1. 解析 ChatMessage
 * 2. 好友关系校验（通过 ImFriendProvider SPI，可选）
 * 3. 生成 msgId，回复 ServerAck 给发送方
 * 4. 路由消息到接收方（本地/远程）
 * 5. 追踪 ACK（等待接收方送达确认）
 * 6. 离线 → 触发离线消息回调
 *
 * @author gogym
 */
@Slf4j
public class SingleChatHandler extends BaseHandler {

    private final ImIdGenerator idGenerator;
    private final MessageAckTracker ackTracker;
    private final ImFriendProvider friendProvider;

    public SingleChatHandler(IMServerFacade facade,
                             ImIdGenerator idGenerator,
                             MessageAckTracker ackTracker,
                             ImFriendProvider friendProvider) {
        super(facade);
        this.idGenerator = idGenerator;
        this.ackTracker = ackTracker;
        this.friendProvider = friendProvider;
    }

    @Override
    public int cmd() {
        return Cmd.SINGLE_CHAT_MSG;
    }

    @Override
    public void handle(ImProto.Packet packet, Channel channel, String userId) {
        try {
            ImProto.ChatMessage chatMsg = PacketCodec.parseChatMessage(packet);
            String receiverId = chatMsg.getReceiverId();

            // 1. 好友关系校验（如果配置了 ImFriendProvider）
            if (friendProvider != null && !friendProvider.isFriend(userId, receiverId)) {
                log.info("单聊消息被拒绝(非好友): from={}, to={}", userId, receiverId);
                ImProto.Packet failAck = PacketCodec.buildServerAckFail(
                        packet.getRequestId(), 403, packet.getSequence());
                channel.writeAndFlush(failAck);
                return;
            }

            // 2. 生成消息ID
            String msgId = chatMsg.getMsgId().isEmpty()
                    ? idGenerator.generateMsgId()
                    : chatMsg.getMsgId();

            // 3. 构建带 msgId 的完整消息
            ImProto.ChatMessage enrichedMsg = chatMsg.toBuilder()
                    .setMsgId(msgId)
                    .build();
            ImProto.Packet fwdPacket = PacketCodec.create(Cmd.SINGLE_CHAT_MSG, 0, enrichedMsg);

            // 4. 回复 ServerAck 给发送方
            String requestId = packet.getRequestId();
            ImProto.Packet ack = PacketCodec.buildServerAck(
                    requestId,
                    msgId,
                    packet.getSequence());
            channel.writeAndFlush(ack);

            // 5. 路由到接收方
            boolean delivered = routeToUser(receiverId, fwdPacket);

            if (delivered) {
                // 6. 追踪 ACK（等待接收方送达确认）
                ackTracker.track(msgId, receiverId, fwdPacket);
            } else {
                log.debug("单聊消息接收方离线: msgId={}, receiver={}", msgId, receiverId);
                // 触发离线消息回调
                fireOfflineMessage(fwdPacket, receiverId, "OFFLINE");
            }

            // 7. 触发消息回调（业务层持久化）
            fireReceivedMessage(fwdPacket);

            log.debug("单聊消息处理完成: msgId={}, from={}, to={}", msgId, userId, receiverId);

        } catch (Exception e) {
            log.error("单聊消息处理失败, userId={}", userId, e);
            ImProto.Packet failAck = PacketCodec.buildServerAckFail(
                    packet.getRequestId(), 500, packet.getSequence());
            channel.writeAndFlush(failAck);
        }
    }
}
