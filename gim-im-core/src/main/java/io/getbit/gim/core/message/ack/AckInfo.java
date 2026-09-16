package io.getbit.gim.core.message.ack;

import io.getbit.gim.protocol.codec.ImProto;

/**
 * AckInfo.java
 *
 * 待 ACK 消息追踪记录（包级私有，仅供 MessageAckTracker 使用）
 *
 * @author gogym
 */
class AckInfo {

    final String receiverId;
    final long sentAt;
    final ImProto.Packet packet;
    final int retryCount;

    AckInfo(String receiverId, long sentAt, ImProto.Packet packet, int retryCount) {
        this.receiverId = receiverId;
        this.sentAt = sentAt;
        this.packet = packet;
        this.retryCount = retryCount;
    }
}
