package io.getbit.gim.webrtc.singlecall;

import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.spi.ImRedisAdapter;
import io.getbit.gim.webrtc.enums.CallEndReason;
import io.getbit.gim.webrtc.enums.SingleCallSessionStatus;
import io.getbit.gim.webrtc.singlecall.listener.SingleCallListener;
import io.getbit.gim.webrtc.singlecall.model.SingleCallSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SingleCallSessionManagerTest.java
 * 覆盖内存态与 Redis 态两条路径的生命周期/占用互斥/超时/掉线清理
 *
 * @author gogym
 */
class SingleCallSessionManagerTest {

    // ====================== Fake Redis ======================

    /**
     * 内存版 Redis 适配器（忽略 TTL，仅模拟 setex/get/del/setnx 语义）
     */
    private static class FakeRedisAdapter implements ImRedisAdapter {
        final Map<String, String> store = new ConcurrentHashMap<>();

        @Override
        public void setex(String key, int seconds, String value) {
            store.put(key, value);
        }

        @Override
        public String get(String key) {
            return store.get(key);
        }

        @Override
        public boolean setnx(String key, String value, int seconds) {
            return store.putIfAbsent(key, value) == null;
        }

        @Override
        public void del(String key) {
            store.remove(key);
        }

        @Override
        public void publish(String channel, String message) {
        }
    }

    // ====================== 配置构造辅助 ======================

    /**
     * 集群模式配置（enable-cluster=true）：会话走 Redis，振铃/TTL 取默认 60/7200
     */
    private static GimProperties clusterConfig() {
        GimProperties p = new GimProperties();
        p.setEnableCluster(true);
        return p;
    }

    /**
     * 单机模式配置（enable-cluster=false）：自定义振铃超时与会话 TTL
     */
    private static GimProperties localConfig(int ringTimeoutSeconds, int sessionTtlSeconds) {
        GimProperties p = new GimProperties();
        p.getRtcCall().setRingTimeoutSeconds(ringTimeoutSeconds);
        p.getRtcCall().setSessionTtlSeconds(sessionTtlSeconds);
        return p;
    }

    // ====================== 生命周期（内存态） ======================

    @Test
    @DisplayName("内存态：创建→接听→通话→结束 完整状态机")
    void memoryLifecycle() {
        SingleCallSessionManager manager = new SingleCallSessionManager();
        try {
            assertTrue(manager.createSession("c1", "a", "b", "video", null, null));
            assertEquals(SingleCallSessionStatus.CALLING, manager.getSession("c1").getStatus());
            assertTrue(manager.isInCall("a"));
            assertTrue(manager.isInCall("b"));

            // 非被叫接听失败
            assertFalse(manager.acceptSession("c1", "a", null));
            // 被叫接听成功，重复接听幂等
            assertTrue(manager.acceptSession("c1", "b", null));
            assertTrue(manager.acceptSession("c1", "b", null));
            assertEquals(SingleCallSessionStatus.CONNECTING, manager.getSession("c1").getStatus());

            assertTrue(manager.startTalking("c1"));
            assertEquals(SingleCallSessionStatus.TALKING, manager.getSession("c1").getStatus());

            SingleCallSession ended = manager.endSession("c1", CallEndReason.ANSWERED);
            assertNotNull(ended);
            assertEquals(SingleCallSessionStatus.ENDED, ended.getStatus());
            assertFalse(manager.isInCall("a"));
            assertFalse(manager.isInCall("b"));
            // 重复结束无效
            assertNull(manager.endSession("c1"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @DisplayName("占用互斥：任一方在通话中不能被新建会话")
    void busyMutualExclusion() {
        SingleCallSessionManager manager = new SingleCallSessionManager();
        try {
            assertTrue(manager.createSession("c1", "a", "b", "video", null, null));

            // 被叫被占用
            assertFalse(manager.createSession("c2", "x", "b", "audio", null, null));
            // 主叫被占用
            assertFalse(manager.createSession("c2", "a", "x", "audio", null, null));
            // 无关用户可正常发起
            assertTrue(manager.createSession("c2", "x", "y", "audio", null, null));
        } finally {
            manager.shutdown();
        }
    }

    // ====================== Redis 态（跨节点可见） ======================

    @Test
    @DisplayName("Redis态：会话跨管理器实例可见（模拟跨节点）")
    void redisCrossNodeVisibility() {
        FakeRedisAdapter redis = new FakeRedisAdapter();
        SingleCallSessionManager nodeA = new SingleCallSessionManager(clusterConfig(), redis);
        SingleCallSessionManager nodeB = new SingleCallSessionManager(clusterConfig(), redis);
        try {
            assertTrue(nodeA.createSession("c1", "a", "b", "video", null, null));

            // 节点B可感知占用与会话
            assertTrue(nodeB.isInCall("a"));
            assertNotNull(nodeB.getSession("c1"));

            // 节点B接听、接通、结束
            assertTrue(nodeB.acceptSession("c1", "b", null));
            assertTrue(nodeB.startTalking("c1"));
            assertEquals(SingleCallSessionStatus.TALKING, nodeA.getSession("c1").getStatus());

            assertNotNull(nodeB.endSession("c1", CallEndReason.ANSWERED));
            assertFalse(nodeA.isInCall("a"));
            assertFalse(nodeB.isInCall("b"));
        } finally {
            nodeA.shutdown();
            nodeB.shutdown();
        }
    }

    @Test
    @DisplayName("Redis态：无 setnx 时降级为 GET+SETEX 占位")
    void redisFallbackWithoutSetnx() {
        FakeRedisAdapter redis = new FakeRedisAdapter() {
            @Override
            public boolean setnx(String key, String value, int seconds) {
                return false;
            }
        };
        SingleCallSessionManager manager = new SingleCallSessionManager(clusterConfig(), redis);
        try {
            assertTrue(manager.createSession("c1", "a", "b", "video", null, null));
            assertFalse(manager.createSession("c2", "a", "x", "video", null, null));
        } finally {
            manager.shutdown();
        }
    }

    // ====================== 振铃超时 ======================

    @Test
    @DisplayName("振铃超时：CALLING 超时自动结束并触发事件")
    void ringTimeout() throws InterruptedException {
        SingleCallSessionManager manager = new SingleCallSessionManager(localConfig(1, 7200), null);
        List<SingleCallSession> timeouts = new CopyOnWriteArrayList<>();
        List<CallEndReason> endReasons = new CopyOnWriteArrayList<>();
        manager.setListener(new SingleCallListener() {
            @Override
            public void onCallTimeout(SingleCallSession session) {
                timeouts.add(session);
            }

            @Override
            public void onSessionEnded(SingleCallSession session, CallEndReason reason) {
                endReasons.add(reason);
            }
        });
        try {
            assertTrue(manager.createSession("c1", "a", "b", "video", null, null));

            Thread.sleep(1800);

            assertNull(manager.getSession("c1"));
            assertFalse(manager.isInCall("a"));
            assertEquals(1, timeouts.size());
            assertEquals("c1", timeouts.get(0).getCallId());
            assertEquals(CallEndReason.TIMEOUT, endReasons.get(0));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @DisplayName("振铃超时：已接听的会话不超时")
    void ringTimeout_notAppliesToAccepted() throws InterruptedException {
        SingleCallSessionManager manager = new SingleCallSessionManager(localConfig(1, 7200), null);
        try {
            assertTrue(manager.createSession("c1", "a", "b", "video", null, null));
            assertTrue(manager.acceptSession("c1", "b", null));

            Thread.sleep(1800);

            assertNotNull(manager.getSession("c1"));
            assertEquals(SingleCallSessionStatus.CONNECTING, manager.getSession("c1").getStatus());
            assertTrue(manager.isInCall("a"));
        } finally {
            manager.shutdown();
        }
    }

    // ====================== 接听后连接超时 ======================

    @Test
    @DisplayName("连接超时：接听后 CONNECTING 超过 connectTimeoutSeconds 自动结束并触发事件")
    void connectTimeout() throws InterruptedException {
        GimProperties config = localConfig(60, 7200);
        config.getRtcCall().setConnectTimeoutSeconds(1);
        SingleCallSessionManager manager = new SingleCallSessionManager(config, null);
        List<SingleCallSession> timeouts = new CopyOnWriteArrayList<>();
        List<CallEndReason> endReasons = new CopyOnWriteArrayList<>();
        manager.setListener(new SingleCallListener() {
            @Override
            public void onConnectTimeout(SingleCallSession session) {
                timeouts.add(session);
            }

            @Override
            public void onSessionEnded(SingleCallSession session, CallEndReason reason) {
                endReasons.add(reason);
            }
        });
        try {
            assertTrue(manager.createSession("c1", "a", "b", "video", null, null));
            assertTrue(manager.acceptSession("c1", "b", null));

            Thread.sleep(1800);

            assertNull(manager.getSession("c1"));
            assertFalse(manager.isInCall("a"));
            assertFalse(manager.isInCall("b"));
            assertEquals(1, timeouts.size());
            assertEquals("c1", timeouts.get(0).getCallId());
            assertEquals(CallEndReason.CONNECT_FAILED, endReasons.get(0));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @DisplayName("连接超时：进入 TALKING 后取消连接超时任务，通话不受影响")
    void connectTimeout_notAppliesToTalking() throws InterruptedException {
        GimProperties config = localConfig(60, 7200);
        config.getRtcCall().setConnectTimeoutSeconds(1);
        SingleCallSessionManager manager = new SingleCallSessionManager(config, null);
        try {
            assertTrue(manager.createSession("c1", "a", "b", "video", null, null));
            assertTrue(manager.acceptSession("c1", "b", null));
            assertTrue(manager.startTalking("c1"));

            Thread.sleep(1800);

            assertNotNull(manager.getSession("c1"));
            assertEquals(SingleCallSessionStatus.TALKING, manager.getSession("c1").getStatus());
            assertTrue(manager.isInCall("a"));
        } finally {
            manager.shutdown();
        }
    }

    // ====================== 本地过期策略 ======================

    @Test
    @DisplayName("本地过期策略：CALLING 限振铃窗口，TALKING 仅 TTL 天花板且由续期任务滚动续期")
    void localExpirePolicy() {
        SingleCallSessionManager manager = new SingleCallSessionManager(localConfig(60, 7200), null);
        try {
            SingleCallSession calling = new SingleCallSession();
            calling.setStatus(SingleCallSessionStatus.CALLING);
            assertEquals(TimeUnit.SECONDS.toNanos(90), manager.localExpireNanos(calling));

            // 接通后不做时间驱逐，仅 sessionTtl 天花板（由续期任务滚动重写）
            SingleCallSession connecting = new SingleCallSession();
            connecting.setStatus(SingleCallSessionStatus.CONNECTING);
            assertEquals(TimeUnit.SECONDS.toNanos(7200), manager.localExpireNanos(connecting));

            SingleCallSession talking = new SingleCallSession();
            talking.setStatus(SingleCallSessionStatus.TALKING);
            assertEquals(TimeUnit.SECONDS.toNanos(7200), manager.localExpireNanos(talking));
        } finally {
            manager.shutdown();
        }
    }

    // ====================== 掉线清理 ======================

    @Test
    @DisplayName("掉线清理：结束用户通话并触发掉线事件")
    void disconnectCleanup() {
        SingleCallSessionManager manager = new SingleCallSessionManager();
        List<String> disconnected = new CopyOnWriteArrayList<>();
        manager.setListener(new SingleCallListener() {
            @Override
            public void onCallDisconnected(SingleCallSession session, String userId) {
                disconnected.add(userId);
            }
        });
        try {
            assertTrue(manager.createSession("c1", "a", "b", "video", null, null));
            assertTrue(manager.startTalking("c1"));

            SingleCallSession session = manager.endSessionsByUser("b");
            assertNotNull(session);
            assertEquals("c1", session.getCallId());
            assertFalse(manager.isInCall("a"));
            assertFalse(manager.isInCall("b"));
            assertEquals(1, disconnected.size());
            assertEquals("b", disconnected.get(0));

            // 无通话用户清理无事件
            assertNull(manager.endSessionsByUser("nobody"));
            assertEquals(1, disconnected.size());
        } finally {
            manager.shutdown();
        }
    }
}
