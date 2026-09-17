package io.getbit.gim.webrtc.singlecall;

import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.connection.channel.ChannelManager;
import io.getbit.gim.core.routing.UserRouteService;
import io.getbit.gim.core.spi.ImIdGenerator;
import io.getbit.gim.core.spi.ImRedisAdapter;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.webrtc.enums.CallEndReason;
import io.getbit.gim.webrtc.enums.SingleSignalType;
import io.getbit.gim.webrtc.enums.SingleCallSessionStatus;
import io.getbit.gim.webrtc.singlecall.listener.ImSingleCallListener;
import io.getbit.gim.webrtc.singlecall.model.SingleCallSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SingleCallServiceTest.java
 * 覆盖忙线拦截、服务端 callId 生成、接听校验、终止信令会话释放与业务回调
 *
 * @author gogym
 */
class SingleCallServiceTest {

    private SingleCallSessionManager sessionManager;
    private SingleCallService callService;
    private List<String> startedCalls;
    private List<CallEndReason> endedReasons;

    @BeforeEach
    void setUp() {
        sessionManager = new SingleCallSessionManager();

        startedCalls = new CopyOnWriteArrayList<>();
        endedReasons = new CopyOnWriteArrayList<>();
        List<ImSingleCallListener> businessListeners = List.of(new ImSingleCallListener() {
            @Override
            public void onCallStart(String callId, String callerId, String calleeId, String callType) {
                startedCalls.add(callId);
            }

            @Override
            public void onCallEnd(String callId, String callerId, String calleeId,
                                  String callType, long durationSeconds, CallEndReason reason) {
                endedReasons.add(reason);
            }
        });

        callService = new SingleCallService(
                minimalFacade(), sessionManager, null,
                (ImIdGenerator) () -> "id-gen-1", businessListeners);
    }

    @AfterEach
    void tearDown() {
        sessionManager.shutdown();
    }

    /**
     * 最小可用门面：仅装配路由/连接组件（信令下发路径在无集群时降级为离线，不影响会话断言）
     */
    private IMServerFacade minimalFacade() {
        GimProperties config = new GimProperties();
        return new IMServerFacade.Builder()
                .config(config)
                .channelManager(new ChannelManager(config))
                .userRouteService(new UserRouteService(config, noopRedisAdapter()))
                .build();
    }

    /**
     * 空实现 Redis 适配器：保证路由查询不异常，所有用户均视为离线
     */
    private ImRedisAdapter noopRedisAdapter() {
        return new ImRedisAdapter() {
            @Override
            public void setex(String key, int seconds, String value) {
            }

            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public void del(String key) {
            }

            @Override
            public void publish(String channel, String message) {
            }
        };
    }

    private ImProto.RtcSignal signal(SingleSignalType type, String from, String to, String callId, String payload) {
        ImProto.RtcSignal.Builder builder = ImProto.RtcSignal.newBuilder()
                .setSignalType(type.getCode())
                .setSenderId(from)
                .setReceiverId(to)
                .setPayload(payload == null ? "" : payload);
        if (callId != null) {
            builder.setCallId(callId);
        }
        return builder.build();
    }

    @Test
    @DisplayName("callRequest：callId 为空时服务端生成并回填，触发业务开始回调")
    void callRequestGeneratesCallId() {
        ImProto.RtcSignal result = callService.onCallRequest(
                signal(SingleSignalType.CALL_REQUEST, "a", "b", null, "{\"callType\":\"video\"}"), null, "a");

        assertNotNull(result);
        assertEquals("id-gen-1", result.getCallId());

        SingleCallSession session = sessionManager.getSession("id-gen-1");
        assertNotNull(session);
        assertEquals(SingleCallSessionStatus.CALLING, session.getStatus());
        assertEquals("a", session.getCallerId());
        assertEquals("b", session.getCalleeId());
        assertEquals("video", session.getCallType());
        assertEquals(1, startedCalls.size());
    }

    @Test
    @DisplayName("callRequest：客户端携带 callId 时沿用")
    void callRequestKeepsClientCallId() {
        ImProto.RtcSignal result = callService.onCallRequest(
                signal(SingleSignalType.CALL_REQUEST, "a", "b", "client-call-1", "{\"callType\":\"audio\"}"), null, "a");

        assertNotNull(result);
        assertEquals("client-call-1", result.getCallId());
        assertNotNull(sessionManager.getSession("client-call-1"));
    }

    @Test
    @DisplayName("callRequest：被叫占用时拦截，不创建会话")
    void callRequestBusyCallee() {
        assertTrue(sessionManager.createSession("c0", "x", "b", "video", null, null));

        ImProto.RtcSignal result = callService.onCallRequest(
                signal(SingleSignalType.CALL_REQUEST, "a", "b", null, "{\"callType\":\"video\"}"), null, "a");

        assertNull(result);
        // 未创建新会话，业务开始回调未触发
        assertEquals(0, startedCalls.size());
        assertFalse(sessionManager.isInCall("a"));
    }

    @Test
    @DisplayName("callRequest：主叫占用/自呼时拦截")
    void callRequestInvalidOrBusyCaller() {
        // 主叫占用
        assertTrue(sessionManager.createSession("c0", "a", "x", "video", null, null));
        assertNull(callService.onCallRequest(
                signal(SingleSignalType.CALL_REQUEST, "a", "b", null, "{\"callType\":\"video\"}"), null, "a"));
        sessionManager.endSession("c0");

        // 自呼
        assertNull(callService.onCallRequest(
                signal(SingleSignalType.CALL_REQUEST, "a", "a", null, "{\"callType\":\"video\"}"), null, "a"));
    }

    @Test
    @DisplayName("callAccept：被叫接听成功；会话失效时拦截")
    void callAccept() {
        assertNotNull(callService.onCallRequest(
                signal(SingleSignalType.CALL_REQUEST, "a", "b", "c1", "{\"callType\":\"video\"}"), null, "a"));

        // 非被叫接听失败
        assertFalse(callService.onAccept(signal(SingleSignalType.CALL_ACCEPT, "b", "a", "c1", ""), null, "a"));
        // 被叫接听成功
        assertTrue(callService.onAccept(signal(SingleSignalType.CALL_ACCEPT, "b", "a", "c1", ""), null, "b"));
        assertEquals(SingleCallSessionStatus.CONNECTING, sessionManager.getSession("c1").getStatus());

        // 结束后接听被拦截
        sessionManager.endSession("c1");
        assertFalse(callService.onAccept(signal(SingleSignalType.CALL_ACCEPT, "b", "a", "c1", ""), null, "b"));
    }

    @Test
    @DisplayName("termination：挂断/拒绝/取消均释放会话并触发结束回调")
    void terminationEndsSession() {
        assertNotNull(callService.onCallRequest(
                signal(SingleSignalType.CALL_REQUEST, "a", "b", "c1", "{\"callType\":\"video\"}"), null, "a"));
        assertTrue(sessionManager.startTalking("c1"));

        callService.onTermination(signal(SingleSignalType.CALL_HANGUP, "a", "b", "c1", "{\"reason\":\"normal\"}"),
                "a", CallEndReason.ANSWERED);

        // 会话已从存储移除（结束即释放），占用释放，业务结束回调已触发
        assertNull(sessionManager.getSession("c1"));
        assertFalse(sessionManager.isInCall("a"));
        assertFalse(sessionManager.isInCall("b"));
        assertEquals(1, endedReasons.size());
        assertEquals(CallEndReason.ANSWERED, endedReasons.get(0));
    }

    @Test
    @DisplayName("termination：缺少 callId 时不影响会话")
    void terminationWithoutCallId() {
        assertTrue(sessionManager.createSession("c1", "a", "b", "video", null, null));

        callService.onTermination(signal(SingleSignalType.CALL_HANGUP, "a", "b", null, "{\"reason\":\"normal\"}"),
                "a", CallEndReason.ANSWERED);

        assertNotNull(sessionManager.getSession("c1"));
        assertEquals(0, endedReasons.size());
    }
}
