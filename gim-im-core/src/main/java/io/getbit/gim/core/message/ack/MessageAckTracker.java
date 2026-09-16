package io.getbit.gim.core.message.ack;

import io.getbit.gim.core.spi.ImEventListener;
import io.getbit.gim.protocol.codec.ImProto;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MessageAckTracker.java
 * <p>
 * 消息ACK追踪器
 * 追踪已发送但未收到ACK的消息，支持超时重发
 * ACK超时时：若开启自动重发且未超过重发次数，则延迟重发；否则触发离线消息回调
 *
 * @author gogym
 */
@Slf4j
public class MessageAckTracker {

    /**
     * 最大追踪数量
     */
    private static final int MAX_TRACK_SIZE = 100_000;

    /**
     * ACK超时时间（秒），从 MessageProperties 接入
     */
    private final int ackTimeoutSeconds;

    /**
     * 是否开启自动重发
     */
    private final boolean autoRewrite;

    /**
     * 最大重发次数
     */
    private final int reWriteNum;

    /**
     * 重发间隔（毫秒）
     */
    private final long reWriteDelay;

    /**
     * 重发回调（由外部提供路由投递能力）
     */
    private final ResendCallback resendCallback;

    private final List<ImEventListener> eventListeners;

    /**
     * msgId -> AckInfo 映射
     */
    private final Cache<String, AckInfo> pendingAcks;

    /**
     * 延迟重发调度器
     */
    private final ScheduledExecutorService scheduler;

    public MessageAckTracker() {
        this(10, Collections.emptyList(), false, 3, 1000, null);
    }

    public MessageAckTracker(int ackTimeoutSeconds, List<ImEventListener> eventListeners) {
        this(ackTimeoutSeconds, eventListeners, false, 3, 1000, null);
    }

    public MessageAckTracker(int ackTimeoutSeconds,
                             List<ImEventListener> eventListeners,
                             boolean autoRewrite,
                             int reWriteNum,
                             long reWriteDelay,
                             ResendCallback resendCallback) {
        this.ackTimeoutSeconds = ackTimeoutSeconds > 0 ? ackTimeoutSeconds : 10;
        this.eventListeners = eventListeners != null ? eventListeners : Collections.emptyList();
        this.autoRewrite = autoRewrite;
        this.reWriteNum = reWriteNum > 0 ? reWriteNum : 3;
        this.reWriteDelay = reWriteDelay > 0 ? reWriteDelay : 1000;
        this.resendCallback = resendCallback;
        this.scheduler = autoRewrite ? Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ack-resend");
            t.setDaemon(true);
            return t;
        }) : null;

        this.pendingAcks = Caffeine.newBuilder()
                .expireAfterWrite(this.ackTimeoutSeconds, TimeUnit.SECONDS)
                .maximumSize(MAX_TRACK_SIZE)
                .removalListener((String key, AckInfo value, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    if (cause == com.github.benmanes.caffeine.cache.RemovalCause.EXPIRED && value != null) {
                        handleAckTimeout(key, value);
                    }
                })
                .build();
    }

    /**
     * 注册待ACK消息
     *
     * @param msgId      消息ID
     * @param receiverId 接收者ID
     * @param packet     发送的Packet
     */
    public void track(String msgId, String receiverId, ImProto.Packet packet) {
        track(msgId, receiverId, packet, 0);
    }

    /**
     * 注册待ACK消息（带重发计数）
     *
     * @param msgId      消息ID
     * @param receiverId 接收者ID
     * @param packet     发送的Packet
     * @param retryCount 当前已重发次数
     */
    private void track(String msgId, String receiverId, ImProto.Packet packet, int retryCount) {
        pendingAcks.put(msgId, new AckInfo(receiverId, System.currentTimeMillis(), packet, retryCount));
        log.debug("注册ACK追踪: msgId={}, receiver={}, retry={}/{}", msgId, receiverId, retryCount, reWriteNum);
    }

    /**
     * 确认收到ACK
     *
     * @param msgId 消息ID
     * @return true if was pending
     */
    public boolean acknowledge(String msgId) {
        AckInfo removed = pendingAcks.getIfPresent(msgId);
        if (removed != null) {
            pendingAcks.invalidate(msgId);
            log.debug("消息ACK确认: msgId={}, receiver={}", msgId, removed.receiverId);
            return true;
        }
        return false;
    }

    /**
     * 获取待ACK数量
     */
    public long getPendingCount() {
        return pendingAcks.estimatedSize();
    }

    /**
     * ACK超时处理
     */
    private void handleAckTimeout(String msgId, AckInfo info) {
        // 自动重发：未超过最大次数且有重发回调
        if (autoRewrite && resendCallback != null && info.retryCount < reWriteNum) {
            int nextRetry = info.retryCount + 1;
            log.info("消息ACK超时，准备第{}次重发: msgId={}, receiver={}", nextRetry, msgId, info.receiverId);

            // 延迟重发
            scheduler.schedule(() -> {
                try {
                    resendCallback.resend(info.receiverId, info.packet);
                    // 重新追踪（增加重发计数）
                    track(msgId, info.receiverId, info.packet, nextRetry);
                    log.debug("消息重发完成: msgId={}, retry={}/{}", msgId, nextRetry, reWriteNum);
                } catch (Exception e) {
                    log.error("消息重发失败: msgId={}", msgId, e);
                }
            }, reWriteDelay, TimeUnit.MILLISECONDS);
        } else {
            // 重发次数用尽或未开启自动重发，触发离线回调
            log.warn("消息ACK超时{}: msgId={}, receiver={}",
                    autoRewrite ? "且重发次数用尽" : "", msgId, info.receiverId);
            fireAckTimeout(msgId, info);
        }
    }

    /**
     * ACK超时触发离线回调
     */
    private void fireAckTimeout(String msgId, AckInfo info) {
        for (ImEventListener listener : eventListeners) {
            try {
                listener.onOfflineMessage(info.packet, info.receiverId, "ACK_TIMEOUT");
            } catch (Exception e) {
                log.error("ACK超时离线回调异常, msgId={}", msgId, e);
            }
        }
    }
}
