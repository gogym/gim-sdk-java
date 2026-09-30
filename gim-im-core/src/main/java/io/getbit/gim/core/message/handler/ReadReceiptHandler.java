package io.getbit.gim.core.message.handler;

import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.spi.ImEventListener;
import io.getbit.gim.protocol.codec.Cmd;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.protocol.codec.PacketCodec;
import io.netty.channel.Channel;

import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * ReadReceiptHandler.java
 *
 * 已读回执处理器
 * 收到已读回执后，通知消息发送方标记为已读
 *
 * 处理流程：
 * 1. 解析已读回执（conversationId + lastReadMsgId）
 * 2. 单聊：从 conversationId 解析对方 userId，转发已读回执
 * 3. 群聊：暂不处理（群已读回执需要更复杂的实现）
 *
 * @author gogym
 */
@Slf4j
public class ReadReceiptHandler extends BaseHandler {

    public ReadReceiptHandler(IMServerFacade facade) {
        super(facade);
    }

    @Override
    public int cmd() {
        return Cmd.READ_RECEIPT;
    }

    @Override
    public void handle(ImProto.Packet packet, Channel channel, String userId) {
        try {
            ImProto.ReadReceipt readReceipt = PacketCodec.parseReadReceipt(packet);
            String conversationId = readReceipt.getConversationId();
            String lastReadMsgId = readReceipt.getLastReadMsgId();

            log.debug("已读回执: userId={}, conversation={}, lastReadMsg={}",
                    userId, conversationId, lastReadMsgId);

            // 单聊：客户端上报的 receiverId 即转发目标；群聊 receiverId 留空，走回调由使用方处理
            String otherUserId = readReceipt.getReceiverId();
            if (otherUserId != null && !otherUserId.isEmpty()) {
                // 构建转发包（携带已读信息，receiverId 回填为原发送方）
                ImProto.ReadReceipt fwdReceipt = ImProto.ReadReceipt.newBuilder()
                        .setConversationId(conversationId)
                        .setLastReadMsgId(lastReadMsgId)
                        .setReceiverId(userId)
                        .build();
                ImProto.Packet fwdPacket = PacketCodec.create(Cmd.READ_RECEIPT, 0, fwdReceipt);
                boolean delivered = routeToUser(otherUserId, fwdPacket);

                if (!delivered) {
                    log.debug("已读回执目标用户离线: to={}", otherUserId);
                }
            } else {
                // 群聊回执（receiverId 留空）：通过回调让使用方处理
                for (ImEventListener listener : eventListeners) {
                    try {
                        listener.onReadReceipt(packet);
                    } catch (Exception e) {
                        log.error("已读回执回调异常: userId={}", userId, e);
                    }
                }
            }

        } catch (Exception e) {
            log.error("已读回执处理失败, userId={}", userId, e);
        }
    }
}
