package io.getbit.gim.core.message.handler;

import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.message.ack.MessageAckTracker;
import io.getbit.gim.core.spi.ImGroupMemberProvider;
import io.getbit.gim.core.spi.ImIdGenerator;
import io.getbit.gim.protocol.codec.Cmd;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.protocol.codec.PacketCodec;
import io.netty.channel.Channel;

import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * GroupChatHandler.java
 *
 * 群聊消息处理器
 *
 * 处理流程：
 * 1. 解析 ChatMessage，生成 msgId
 * 2. 禁言检查（通过 ImGroupMemberProvider SPI）
 * 3. 回复 ServerAck 给发送方
 * 4. 遍历群成员，逐一投递（排除发送者）
 *    - 本地在线 → 直接投递
 *    - 远程节点 → Redis Pub/Sub 路由
 *    - 离线 → 触发离线消息回调
 * 5. 追踪每个在线成员的 ACK
 *
 * @author gogym
 */
@Slf4j
public class GroupChatHandler extends BaseHandler {

    private final ImIdGenerator idGenerator;
    private final MessageAckTracker ackTracker;
    private final ImGroupMemberProvider groupMemberProvider;

    public GroupChatHandler(IMServerFacade facade,
                            ImIdGenerator idGenerator,
                            MessageAckTracker ackTracker,
                            ImGroupMemberProvider groupMemberProvider) {
        super(facade);
        this.idGenerator = idGenerator;
        this.ackTracker = ackTracker;
        this.groupMemberProvider = groupMemberProvider;
    }

    @Override
    public int cmd() {
        return Cmd.GROUP_CHAT_MSG;
    }

    @Override
    public void handle(ImProto.Packet packet, Channel channel, String userId) {
        try {
            ImProto.ChatMessage chatMsg = PacketCodec.parseChatMessage(packet);
            String groupId = chatMsg.getReceiverId();

            // 1. 群成员资格校验
            if (!groupMemberProvider.isGroupMember(groupId, userId)) {
                log.info("群消息被拒绝(非群成员): userId={}, groupId={}", userId, groupId);
                ImProto.Packet rejectAck = PacketCodec.buildServerAckFail(
                        packet.getRequestId(), 403, packet.getSequence());
                channel.writeAndFlush(rejectAck);
                return;
            }

            // 2. 禁言检查
            String muteReason = groupMemberProvider.checkCanSendMessage(groupId, userId);
            if (muteReason != null) {
                log.info("群消息被拒绝: userId={}, groupId={}, reason={}", userId, groupId, muteReason);
                ImProto.Packet muteAck = PacketCodec.buildServerAckFail(
                        packet.getRequestId(), 403, packet.getSequence());
                channel.writeAndFlush(muteAck);
                return;
            }

            // 3. 生成消息ID
            chatMsg.getMsgId();
            String msgId = chatMsg.getMsgId().isEmpty()
                    ? idGenerator.generateMsgId()
                    : chatMsg.getMsgId();

            // 4. 构建带 msgId 的完整消息
            ImProto.ChatMessage enrichedMsg = chatMsg.toBuilder()
                    .setMsgId(msgId)
                    .build();
            ImProto.Packet msgPacket = PacketCodec.create(Cmd.GROUP_CHAT_MSG, 0, enrichedMsg);

            // 5. 回复 ServerAck 给发送方
            String requestId = packet.getRequestId();
            ImProto.Packet ack = PacketCodec.buildServerAck(
                    requestId,
                    msgId,
                    packet.getSequence());
            channel.writeAndFlush(ack);

            // 6. 路由投递给在线群成员
            routeToMembers(enrichedMsg, msgPacket, userId, groupId);

            // 7. 触发消息回调（业务层持久化）
            fireReceivedMessage(msgPacket);

            log.debug("群聊消息处理完成: msgId={}, from={}, group={}", msgId, userId, groupId);

        } catch (Exception e) {
            log.error("群聊消息处理失败, userId={}", userId, e);
            ImProto.Packet failAck = PacketCodec.buildServerAckFail(
                    packet.getRequestId(), 500, packet.getSequence());
            channel.writeAndFlush(failAck);
        }
    }

    /**
     * 群聊消息路由投递
     *
     * 流程：
     * 1. 获取群内所有活跃成员 userId 列表
     * 2. 为每个成员（排除发送者）路由投递
     * 3. 本地 → 直接投递 + ACK 追踪，远程 → Redis Pub/Sub，离线 → 离线消息回调
     */
    private void routeToMembers(ImProto.ChatMessage chatMsg, ImProto.Packet msgPacket, String senderId, String groupId) {
        List<String> memberUserIds = groupMemberProvider.getGroupMemberUserIds(groupId);
        if (memberUserIds == null || memberUserIds.isEmpty()) {
            log.warn("群消息路由: 群 {} 无活跃成员", groupId);
            return;
        }

        int deliveredCount = 0;
        int offlineCount = 0;

        for (String memberId : memberUserIds) {
            // 跳过发送者
            if (memberId.equals(senderId)) {
                continue;
            }

            // 路由投递
            ImProto.Packet downstreamPacket = msgPacket;
            boolean delivered = routeToUser(memberId, downstreamPacket);

            if (delivered) {
                ackTracker.track(chatMsg.getMsgId(), memberId, downstreamPacket);
                deliveredCount++;
            } else {
                fireOfflineMessage(downstreamPacket, memberId, "OFFLINE");
                offlineCount++;
            }
        }

        log.debug("群消息路由完成: group={}, msgId={}, members={}, delivered={}, offline={}",
                groupId, chatMsg.getMsgId(), memberUserIds.size(), deliveredCount, offlineCount);
    }
}
