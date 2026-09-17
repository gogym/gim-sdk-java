package io.getbit.gim.webrtc.singlecall;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.google.gson.Gson;
import io.getbit.gim.core.cache.CacheKeyBuilder;
import io.getbit.gim.core.spi.ImRedisAdapter;
import io.getbit.gim.core.util.GimThreads;
import io.getbit.gim.webrtc.enums.CallEndReason;
import io.getbit.gim.webrtc.enums.SingleCallSessionStatus;
import io.getbit.gim.webrtc.singlecall.listener.SingleCallListener;
import io.getbit.gim.webrtc.singlecall.model.SingleCallSession;
import io.netty.channel.Channel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SingleCallSessionManager.java
 *
 * 1:1 通话会话生命周期管理器（线程安全）
 * 职责：
 * 1. 会话状态机维护：CALLING → CONNECTING → TALKING → ENDED
 * 2. 占用判定：单用户同时只允许一路 1:1 通话（群通话经 GroupCallSessionManager.isUserBusy 汇聚判定）
 * 3. 超时策略：CALLING 超过 ringTimeoutSeconds 无应答自动结束会话，事件经 SingleCallListener 通知上层
 * 4. 掉线清理：endSessionsByUser 等价于被动挂断，供 ConnectionCloseListener 掉线链路调用
 *
 * 存储分层：
 * - 配置 ImRedisAdapter 时（集群模式）：会话元数据与占用 key 存 Redis，跨节点可见；
 *   Channel 为节点本地资源，不随 Redis 同步（对端通知统一走信令路由）
 * - 未配置时（单机/测试）：退化为本地 Caffeine 缓存，行为与集群模式一致
 *
 * Redis Key 结构：
 * - im:rtc:call:{callId} → 会话 JSON（不含 Channel 字段）
 * - im:rtc:busy:{userId} → callId（RINGING 态 TTL = ringTimeout + 30s，接通后延长为 sessionTtl）
 *
 * @author gogym
 */
@Slf4j
public class SingleCallSessionManager {

    private static final Gson GSON = new Gson();

    /**
     * 本地会话表：内存模式下的状态源；集群模式下仅作为节点本地 Channel 的覆盖层
     * 按状态差异化过期（见 {@link #localExpireNanos}），不做固定时长驱逐，避免通话中被时间清理
     */
    private final Cache<String, SingleCallSession> localSessionMap = Caffeine.newBuilder()
            .expireAfter(new Expiry<String, SingleCallSession>() {
                @Override
                public long expireAfterCreate(String key, SingleCallSession session, long now) {
                    return localExpireNanos(session);
                }

                @Override
                public long expireAfterUpdate(String key, SingleCallSession session, long now, long currentDurationNanos) {
                    return localExpireNanos(session);
                }

                @Override
                public long expireAfterRead(String key, SingleCallSession session, long now, long currentDurationNanos) {
                    return currentDurationNanos;
                }
            }).build();

    /**
     * 本地占用表：仅内存模式使用（userId -> callId）
     * 采用 ConcurrentHashMap 事件驱动（与 ChannelManager 同一决策）：
     * 生命周期严格绑定会话（create 写入 / end 删除），不做任何时间驱逐，
     * 避免用户仍在通话中却被时间清理判定为空闲
     */
    private final Map<String, String> localUserCallMap = new ConcurrentHashMap<>();

    /**
     * Redis 适配器（为 null 时退化为本地内存模式）
     */
    private final ImRedisAdapter redisAdapter;

    /**
     * 振铃超时时间（秒）
     */
    private final int ringTimeoutSeconds;

    /**
     * 会话 TTL（秒）：Redis 会话与接通后占用 key 的过期时间天花板；
     * TALKING 会话由续期任务滚动重写续期，长通话不会因该 TTL 失效
     */
    private final int sessionTtlSeconds;

    /**
     * 振铃超时任务（仅会话创建节点持有，接听后取消）
     */
    private final Map<String, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = GimThreads.singleDaemonScheduler("gim-one-to-one-call");

    /**
     * 内部事件监听器（由 SingleCallService 注册，超时/掉线/结束事件转化为信令下发）
     */
    @Setter
    private volatile SingleCallListener listener;

    /**
     * 内存模式构造（单机 / 测试场景）
     */
    public SingleCallSessionManager() {
        this(null, 60, 7200);
    }

    /**
     * 完整构造
     *
     * @param redisAdapter       Redis 适配器，null 时退化为内存模式
     * @param ringTimeoutSeconds 振铃超时（秒）
     * @param sessionTtlSeconds  会话 TTL（秒）
     */
    public SingleCallSessionManager(ImRedisAdapter redisAdapter, int ringTimeoutSeconds, int sessionTtlSeconds) {
        this.redisAdapter = redisAdapter;
        this.ringTimeoutSeconds = Math.max(1, ringTimeoutSeconds);
        this.sessionTtlSeconds = Math.max(60, sessionTtlSeconds);

        // TALKING 会话滚动续期：重写会话（Redis re-SETEX / 本地刷新写时间）并续期占用 key，
        // 保证长通话不被 TTL 驱逐；周期为 sessionTtl/4（至少 30s）
        long renewIntervalSeconds = Math.max(30, sessionTtlSeconds / 4);
        scheduler.scheduleWithFixedDelay(this::renewTalkingSessions,
                renewIntervalSeconds, renewIntervalSeconds, TimeUnit.SECONDS);
    }

    // ====================== 会话生命周期 ======================

    /**
     * 创建通话会话（主叫发起）
     * 双方任一占用中则创建失败；成功后调度振铃超时任务
     *
     * @return true=创建成功, false=主叫或被叫通话占用中
     */
    public boolean createSession(String callId, String callerId, String calleeId,
                                 String callType, Channel callerChannel, Channel calleeChannel) {
        // 占位：优先 setnx 原子占位；实现方未支持时降级为 GET+SETEX（存在极小竞态窗口，见 ImRedisAdapter#setnx）
        if (!tryAcquireBusy(callerId, callId)) {
            log.warn("创建1:1通话失败: 主叫通话占用中, caller={}, callId={}", callerId, callId);
            return false;
        }
        if (!tryAcquireBusy(calleeId, callId)) {
            releaseBusy(callerId, callId);
            log.warn("创建1:1通话失败: 被叫通话占用中, callee={}, callId={}", calleeId, callId);
            return false;
        }

        SingleCallSession session = new SingleCallSession();
        session.setCallId(callId);
        session.setCallerId(callerId);
        session.setCalleeId(calleeId);
        session.setCallType(callType);
        session.setCallerChannel(callerChannel);
        session.setCalleeChannel(calleeChannel);
        session.setStatus(SingleCallSessionStatus.CALLING);
        session.setCreateTime(System.currentTimeMillis());
        saveSession(session);
        scheduleTimeoutTask(callId);
        return true;
    }

    /**
     * 被叫接受通话（callAccept）
     * 校验会话存在、振铃中、操作者为被叫；成功后转 CONNECTING 并取消振铃超时
     *
     * @param operatorId 信令发送者（应为被叫）
     * @return true=接受成功或幂等重复接受
     */
    public boolean acceptSession(String callId, String operatorId, Channel channel) {
        SingleCallSession session = loadSession(callId);
        if (session == null) {
            log.warn("接受1:1通话失败: 会话不存在, callId={}", callId);
            return false;
        }
        if (operatorId == null || !operatorId.equals(session.getCalleeId())) {
            log.warn("接受1:1通话失败: 操作者非被叫, callId={}, operator={}", callId, operatorId);
            return false;
        }
        // 幂等：重复 accept（重发）直接刷新 TTL
        if (session.getStatus() == SingleCallSessionStatus.CONNECTING) {
            refreshBusyTtls(session);
            return true;
        }
        if (session.getStatus() != SingleCallSessionStatus.CALLING) {
            log.warn("接受1:1通话失败: 状态非振铃中, callId={}, status={}", callId, session.getStatus());
            return false;
        }
        // 读-改-写：跨节点并发 accept 以 CALLING 状态检查为界，状态进入 CONNECTING 后后续重复请求走幂等分支
        session.setStatus(SingleCallSessionStatus.CONNECTING);
        session.setCalleeChannel(channel);
        saveSession(session);
        // 接通在即，双方占用 key 延长为会话 TTL
        refreshBusyTtls(session);
        cancelTimeoutTask(callId);
        return true;
    }

    /**
     * 进入通话中状态（收到该通话的 SDP Offer 时近似标记接通）
     *
     * @return true=标记成功（幂等重复标记返回 true）
     */
    public boolean startTalking(String callId) {
        SingleCallSession session = loadSession(callId);
        if (session == null || session.getStatus() == SingleCallSessionStatus.ENDED) {
            return false;
        }
        if (session.getStatus() == SingleCallSessionStatus.TALKING) {
            return true;
        }
        session.setStatus(SingleCallSessionStatus.TALKING);
        session.setConnectTime(System.currentTimeMillis());
        saveSession(session);
        refreshBusyTtls(session);
        return true;
    }

    /**
     * 结束会话（指定原因）
     * 删除会话与双方占用 key，取消振铃超时任务，并触发 onSessionEnded 事件
     *
     * @param reason 结束原因（null 时不触发事件）
     * @return 结束前的会话（状态已置 ENDED）；会话不存在返回 null
     */
    public SingleCallSession endSession(String callId, CallEndReason reason) {
        SingleCallSession session = loadSession(callId);
        if (session == null) {
            return null;
        }
        cancelTimeoutTask(callId);
        deleteSession(session);
        session.setStatus(SingleCallSessionStatus.ENDED);
        session.setEndTime(System.currentTimeMillis());

        SingleCallListener l = listener;
        if (l != null) {
            try {
                l.onSessionEnded(session, reason);
            } catch (Exception e) {
                log.error("1:1通话结束事件回调异常, callId={}", callId, e);
            }
        }
        return session;
    }

    /**
     * 结束会话（未指定原因，兼容旧调用，不触发结束事件）
     */
    public SingleCallSession endSession(String callId) {
        return endSession(callId, null);
    }

    /**
     * 掉线清理：结束该用户进行中的 1:1 通话并触发 onCallDisconnected 事件
     * 供 ConnectionCloseListener 在用户全部设备离线时调用
     *
     * @return 该用户的会话（状态已置 ENDED）；无进行中通话返回 null
     */
    public SingleCallSession endSessionsByUser(String userId) {
        String callId = getBusyCallId(userId);
        if (callId == null) {
            return null;
        }
        SingleCallSession session = loadSession(callId);
        if (session == null || !session.isParticipant(userId)) {
            // 会话已过期或占用 key 脏数据，仅清理占用
            releaseBusy(userId, callId);
            return null;
        }
        endSession(callId, CallEndReason.DISCONNECTED);

        SingleCallListener l = listener;
        if (l != null) {
            try {
                l.onCallDisconnected(session, userId);
            } catch (Exception e) {
                log.error("1:1通话掉线事件回调异常, callId={}", callId, e);
            }
        }
        return session;
    }

    // ====================== 查询 ======================

    public SingleCallSession getSession(String callId) {
        return callId == null ? null : loadSession(callId);
    }

    public String getCallIdByUser(String userId) {
        return getBusyCallId(userId);
    }

    /**
     * 占用判定：用户是否在 1:1 通话中
     */
    public boolean isInCall(String userId) {
        return getBusyCallId(userId) != null;
    }

    public boolean isUserBusy(String userId) {
        return isInCall(userId);
    }

    public SingleCallSession getSessionByUser(String userId) {
        String callId = getBusyCallId(userId);
        return callId == null ? null : loadSession(callId);
    }

    public Channel getPeerChannel(String callId, String userId) {
        SingleCallSession session = loadSession(callId);
        if (session == null) {
            return null;
        }
        if (userId != null && userId.equals(session.getCallerId())) {
            return session.getCalleeChannel();
        }
        if (userId != null && userId.equals(session.getCalleeId())) {
            return session.getCallerChannel();
        }
        return null;
    }

    public String getPeerUserId(String callId, String userId) {
        SingleCallSession session = loadSession(callId);
        return session == null ? null : session.getPeerId(userId);
    }

    public Channel[] getAllChannels(String callId) {
        SingleCallSession session = loadSession(callId);
        return session == null ? new Channel[0] : new Channel[]{session.getCallerChannel(), session.getCalleeChannel()};
    }

    // ====================== 占用 key 操作 ======================

    /**
     * 原子占位用户占用 key：优先 setnx，实现方不支持时降级为 GET+SETEX
     */
    private boolean tryAcquireBusy(String userId, String callId) {
        if (redisAdapter != null) {
            int ttl = ringTimeoutSeconds + 30;
            if (redisAdapter.setnx(CacheKeyBuilder.rtcBusy(userId), callId, ttl)) {
                return true;
            }
            // 降级路径：setnx 未实现或 key 已存在，通过 GET 区分（存在极小竞态窗口）
            if (redisAdapter.get(CacheKeyBuilder.rtcBusy(userId)) != null) {
                return false;
            }
            redisAdapter.setex(CacheKeyBuilder.rtcBusy(userId), ttl, callId);
            return true;
        }
        return localUserCallMap.putIfAbsent(userId, callId) == null;
    }

    /**
     * 释放用户占用 key（值匹配才删除，避免误删该用户新通话的占位）
     */
    private void releaseBusy(String userId, String callId) {
        if (redisAdapter != null) {
            String current = redisAdapter.get(CacheKeyBuilder.rtcBusy(userId));
            if (callId != null && callId.equals(current)) {
                redisAdapter.del(CacheKeyBuilder.rtcBusy(userId));
            }
            return;
        }
        localUserCallMap.remove(userId, callId);
    }

    /**
     * 刷新双方占用 key 为会话 TTL（接通后长占用）
     */
    private void refreshBusyTtls(SingleCallSession session) {
        if (redisAdapter != null) {
            for (String userId : new String[]{session.getCallerId(), session.getCalleeId()}) {
                String current = redisAdapter.get(CacheKeyBuilder.rtcBusy(userId));
                if (session.getCallId().equals(current)) {
                    redisAdapter.setex(CacheKeyBuilder.rtcBusy(userId), sessionTtlSeconds, session.getCallId());
                }
            }
        }
    }

    private String getBusyCallId(String userId) {
        if (userId == null) {
            return null;
        }
        if (redisAdapter != null) {
            return redisAdapter.get(CacheKeyBuilder.rtcBusy(userId));
        }
        return localUserCallMap.get(userId);
    }

    // ====================== 会话存取 ======================

    private void saveSession(SingleCallSession session) {
        if (redisAdapter != null) {
            redisAdapter.setex(CacheKeyBuilder.rtcCall(session.getCallId()), sessionTtlSeconds, GSON.toJson(toSnapshot(session)));
        }
        localSessionMap.put(session.getCallId(), session);
    }

    private SingleCallSession loadSession(String callId) {
        if (callId == null || callId.isEmpty()) {
            return null;
        }
        if (redisAdapter != null) {
            String json = redisAdapter.get(CacheKeyBuilder.rtcCall(callId));
            if (json == null) {
                return null;
            }
            SingleCallSession session = fromSnapshot(GSON.fromJson(json, Snapshot.class));
            // Channel 为节点本地资源，从本地覆盖层回填
            SingleCallSession local = localSessionMap.getIfPresent(callId);
            if (local != null) {
                session.setCallerChannel(local.getCallerChannel());
                session.setCalleeChannel(local.getCalleeChannel());
            }
            return session;
        }
        return localSessionMap.getIfPresent(callId);
    }

    private void deleteSession(SingleCallSession session) {
        if (redisAdapter != null) {
            redisAdapter.del(CacheKeyBuilder.rtcCall(session.getCallId()));
        }
        localSessionMap.invalidate(session.getCallId());
        releaseBusy(session.getCallerId(), session.getCallId());
        releaseBusy(session.getCalleeId(), session.getCallId());
    }

    // ====================== 会话续期 ======================

    /**
     * 本地会话表按状态差异化过期：
     * - CALLING：限振铃窗口 ringTimeout + 30s（超时任务为第一道清理，此处兜底）
     * - CONNECTING / TALKING：sessionTtl 天花板，TALKING 由续期任务滚动重写续期，长通话不失效
     * （包级可见供测试断言策略）
     */
    long localExpireNanos(SingleCallSession session) {
        if (session != null && session.getStatus() == SingleCallSessionStatus.CALLING) {
            return TimeUnit.SECONDS.toNanos(ringTimeoutSeconds + 30);
        }
        return TimeUnit.SECONDS.toNanos(sessionTtlSeconds);
    }

    /**
     * 续期进行中的 TALKING 会话（两种模式共用）：
     * Redis 模式重写会话 key 与双方占用 key 完成滚动续期；
     * 内存模式重写本地会话表刷新写时间（本地占用表无时间驱逐，无需处理）
     * 仅扫描本节点覆盖层（会话所在节点），不做全量 Redis 扫描
     */
    private void renewTalkingSessions() {
        try {
            for (SingleCallSession session : localSessionMap.asMap().values()) {
                if (session.getStatus() != SingleCallSessionStatus.TALKING) {
                    continue;
                }
                saveSession(session);
                refreshBusyTtls(session);
            }
        } catch (Exception e) {
            log.warn("TALKING 会话续期异常", e);
        }
    }

    // ====================== 振铃超时 ======================

    private void scheduleTimeoutTask(String callId) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            timeoutTasks.remove(callId);
            SingleCallSession session = loadSession(callId);
            // 仅振铃中判定超时；已接通/已结束的会话由对应路径处理
            if (session == null || session.getStatus() != SingleCallSessionStatus.CALLING) {
                return;
            }
            endSession(callId, CallEndReason.TIMEOUT);
            log.info("1:1通话振铃超时自动结束: callId={}", callId);
            SingleCallListener l = listener;
            if (l != null) {
                try {
                    l.onCallTimeout(session);
                } catch (Exception e) {
                    log.error("1:1通话超时事件回调异常, callId={}", callId, e);
                }
            }
        }, ringTimeoutSeconds, TimeUnit.SECONDS);
        timeoutTasks.put(callId, future);
    }

    private void cancelTimeoutTask(String callId) {
        ScheduledFuture<?> future = timeoutTasks.remove(callId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 关闭会话管理器，释放调度线程与本地状态（应用下线时调用；Redis 状态由 TTL 兜底回收）
     */
    public void shutdown() {
        scheduler.shutdownNow();
        timeoutTasks.clear();
        localSessionMap.invalidateAll();
        localUserCallMap.clear();
    }

    // ====================== Redis 快照序列化 ======================

    private Snapshot toSnapshot(SingleCallSession session) {
        Snapshot snapshot = new Snapshot();
        snapshot.callId = session.getCallId();
        snapshot.callerId = session.getCallerId();
        snapshot.calleeId = session.getCalleeId();
        snapshot.callType = session.getCallType();
        snapshot.status = session.getStatus() == null ? null : session.getStatus().name();
        snapshot.createTime = session.getCreateTime();
        snapshot.connectTime = session.getConnectTime();
        snapshot.endTime = session.getEndTime();
        return snapshot;
    }

    private SingleCallSession fromSnapshot(Snapshot snapshot) {
        SingleCallSession session = new SingleCallSession();
        session.setCallId(snapshot.callId);
        session.setCallerId(snapshot.callerId);
        session.setCalleeId(snapshot.calleeId);
        session.setCallType(snapshot.callType);
        session.setStatus(snapshot.status == null ? null : SingleCallSessionStatus.valueOf(snapshot.status));
        session.setCreateTime(snapshot.createTime);
        session.setConnectTime(snapshot.connectTime);
        session.setEndTime(snapshot.endTime);
        return session;
    }

    /**
     * Redis 序列化快照（刻意排除 Channel 等节点本地字段）
     */
    private static class Snapshot {
        String callId;
        String callerId;
        String calleeId;
        String callType;
        String status;
        long createTime;
        long connectTime;
        long endTime;
    }
}
