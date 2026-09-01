package io.getbit.gim.webrtc;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WebRtcSessionManager {
    private final Cache<String, String> userCallMap = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS).maximumSize(50_000).build();
    private final Cache<String, WebRtcSession> sessionMap = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS).maximumSize(10_000)
            .removalListener((key, value, cause) -> {
                if (value instanceof WebRtcSession session) {
                    userCallMap.invalidate(session.getCallerId());
                    userCallMap.invalidate(session.getCalleeId());
                }
            }).build();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Cache<String, ScheduledFuture<?>> timeoutTasks = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS).maximumSize(10_000).build();
    private static final int DEFAULT_CALL_TIMEOUT = 60;

    public boolean createSession(String callId, String callerId, String calleeId,
                                  String callType, Channel callerChannel, Channel calleeChannel) {
        if (userCallMap.getIfPresent(callerId) != null || userCallMap.getIfPresent(calleeId) != null) return false;
        WebRtcSession session = new WebRtcSession();
        session.setCallId(callId); session.setCallerId(callerId); session.setCalleeId(calleeId);
        session.setCallType(callType); session.setCallerChannel(callerChannel); session.setCalleeChannel(calleeChannel);
        session.setStatus(WebRtcSessionStatus.CALLING); session.setCreateTime(System.currentTimeMillis());
        sessionMap.put(callId, session); userCallMap.put(callerId, callId);
        scheduleTimeoutTask(callId);
        return true;
    }

    public boolean acceptSession(String callId, Channel calleeChannel) {
        WebRtcSession session = sessionMap.getIfPresent(callId);
        if (session == null || session.getStatus() != WebRtcSessionStatus.CALLING) return false;
        session.setCalleeChannel(calleeChannel); session.setStatus(WebRtcSessionStatus.CONNECTING);
        userCallMap.put(session.getCalleeId(), callId); cancelTimeoutTask(callId);
        return true;
    }

    public boolean startTalking(String callId) {
        WebRtcSession session = sessionMap.getIfPresent(callId);
        if (session == null) return false;
        session.setStatus(WebRtcSessionStatus.TALKING); session.setConnectTime(System.currentTimeMillis());
        return true;
    }

    public WebRtcSession endSession(String callId) {
        WebRtcSession session = sessionMap.getIfPresent(callId);
        if (session == null) return null;
        sessionMap.invalidate(callId); cancelTimeoutTask(callId);
        userCallMap.invalidate(session.getCallerId()); userCallMap.invalidate(session.getCalleeId());
        session.setStatus(WebRtcSessionStatus.ENDED); session.setEndTime(System.currentTimeMillis());
        return session;
    }

    public WebRtcSession getSession(String callId) { return sessionMap.getIfPresent(callId); }
    public String getCallIdByUser(String userId) { return userCallMap.getIfPresent(userId); }
    public boolean isInCall(String userId) { return userCallMap.getIfPresent(userId) != null; }

    /**
     * 统一占用查询：用户是否在任一通话中
     * 群通话占用由 GroupCallSessionManager.isUserBusy 统一判定（含本管理器的 1:1 通话）
     */
    public boolean isUserBusy(String userId) { return isInCall(userId); }
    public WebRtcSession getSessionByUser(String userId) {
        String callId = userCallMap.getIfPresent(userId);
        return callId == null ? null : sessionMap.getIfPresent(callId);
    }
    public Channel getPeerChannel(String callId, String userId) {
        WebRtcSession s = sessionMap.getIfPresent(callId);
        if (s == null) return null;
        return userId.equals(s.getCallerId()) ? s.getCalleeChannel() : userId.equals(s.getCalleeId()) ? s.getCallerChannel() : null;
    }
    public String getPeerUserId(String callId, String userId) {
        WebRtcSession s = sessionMap.getIfPresent(callId);
        if (s == null) return null;
        return userId.equals(s.getCallerId()) ? s.getCalleeId() : userId.equals(s.getCalleeId()) ? s.getCallerId() : null;
    }
    public Channel[] getAllChannels(String callId) {
        WebRtcSession s = sessionMap.getIfPresent(callId);
        return s == null ? new Channel[0] : new Channel[]{s.getCallerChannel(), s.getCalleeChannel()};
    }

    private void scheduleTimeoutTask(String callId) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            WebRtcSession session = sessionMap.getIfPresent(callId);
            if (session != null && session.getStatus() == WebRtcSessionStatus.CALLING) {
                log.warn("WebRTC call timeout: callId={}", callId);
            }
        }, DEFAULT_CALL_TIMEOUT, TimeUnit.SECONDS);
        timeoutTasks.put(callId, future);
    }

    private void cancelTimeoutTask(String callId) {
        ScheduledFuture<?> future = timeoutTasks.getIfPresent(callId);
        if (future != null) { timeoutTasks.invalidate(callId); future.cancel(false); }
    }

    /**
     * 关闭会话管理器，释放资源
     */
    public void shutdown() { scheduler.shutdown(); sessionMap.invalidateAll(); userCallMap.invalidateAll(); timeoutTasks.invalidateAll(); }
}
