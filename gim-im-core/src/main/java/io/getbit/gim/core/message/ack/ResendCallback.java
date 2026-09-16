package io.getbit.gim.core.message.ack;

import io.getbit.gim.protocol.codec.ImProto;

/**
 * ResendCallback.java
 *
 * 消息重发回调 SPI
 * ACK 超时且开启自动重发时，由 MessageAckTracker 调用，实现方负责将消息重新投递给接收者
 *
 * @author gogym
 */
@FunctionalInterface
public interface ResendCallback {

    /**
     * 重发消息
     *
     * @param receiverId 接收者 userId
     * @param packet     原始消息包
     */
    void resend(String receiverId, ImProto.Packet packet);
}
