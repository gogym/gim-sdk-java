package com.example.im.spi;

import io.getbit.gim.core.spi.ImEventListener;
import io.getbit.gim.protocol.codec.DeviceType;
import io.getbit.gim.protocol.codec.ImProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * IM 事件监听器实现
 * <p>
 * SDK 在以下场景触发事件回调：
 * <ul>
 *   <li>onUserOnline — 用户上线（可记录登录日志、更新在线状态）</li>
 *   <li>onUserOffline — 用户下线（可清理在线状态、记录离线时间）</li>
 *   <li>onOfflineMessage — 离线消息（可触发 APNs/FCM 推送）</li>
 *   <li>onMessageDeliveryFailed — 消息投递失败</li>
 *   <li>onMessageRecalled — 消息撤回（可更新 DB 消息状态）</li>
 *   <li>onReceivedMessage — 消息回调（可持久化到 DB / MQ）</li>
 * </ul>
 */
@Component
@Slf4j
public class ImEventListenerImpl implements ImEventListener {


    @Override
    public void onUserOnline(String userId, DeviceType device, String serverId) {
        log.info("[IM事件] 用户上线: userId={}, device={}, serverId={}", userId, device, serverId);
        // TODO: 更新用户在线状态、记录登录日志
    }

    @Override
    public void onUserOffline(String userId) {
        log.info("[IM事件] 用户下线: userId={}", userId);
        // TODO: 更新用户离线状态、记录离线时间
    }

    @Override
    public void onOfflineMessage(ImProto.Packet packet, String receiverId, String reason) {
        log.info("[IM事件] 离线消息: receiverId={}, cmd={}, reason={}",
                receiverId, packet.getCmd(), reason);
        // TODO: 触发离线推送（APNs/FCM/华为推送等）
        // pushService.sendOfflinePush(receiverId, packet);
    }

    @Override
    public void onMessageDeliveryFailed(ImProto.Packet packet, String receiverId, String reason) {
        log.warn("[IM事件] 消息投递失败: receiverId={}, cmd={}, reason={}", receiverId, packet.getCmd(), reason);
        // TODO: 记录投递失败日志、触发告警
    }

    @Override
    public void onMessageRecalled(ImProto.Packet packet) {
        log.info("[IM事件] 消息撤回: cmd={}", packet.getCmd());
        // TODO: 更新 DB 中消息状态为已撤回
        // ImProto.MsgRecallRequest req = PacketCodec.parseMsgRecallRequest(packet);
        // messageRepository.updateRecalled(req.getMsgId());
    }

    @Override
    public void onReceivedMessage(ImProto.Packet packet) {
        log.info("[IM事件] 消息回调: cmd={}", packet.getCmd());
        // TODO: 消息持久化到 DB 或发送到 MQ
        // messageRepository.save(packet);
    }
}
