package io.getbit.gim.webrtc.singlecall;

import com.google.gson.Gson;
import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.message.handler.BaseHandler;
import io.getbit.gim.core.spi.ImIdGenerator;
import io.getbit.gim.protocol.codec.Cmd;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.protocol.codec.PacketCodec;
import io.getbit.gim.webrtc.dto.SingleCallRequestDto;
import io.getbit.gim.webrtc.enums.CallEndReason;
import io.getbit.gim.webrtc.enums.SingleSignalType;
import io.getbit.gim.webrtc.groupcall.GroupCallSessionManager;
import io.getbit.gim.webrtc.singlecall.listener.ImSingleCallListener;
import io.getbit.gim.webrtc.singlecall.listener.SingleCallListener;
import io.getbit.gim.webrtc.singlecall.model.SingleCallSession;
import lombok.extern.slf4j.Slf4j;
import io.netty.channel.Channel;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * SingleCallService.java
 *
 * 1:1 通话生命周期服务（signalType 4~8 生命周期信令编排）
 * 由 RtcSingleHandler 在收到 cmd=50 且信令类型为生命周期类时委托调用
 * （继承 BaseHandler 仅为复用路由能力，自身不注册进 MessageDispatcher，避免与 RtcSingleHandler 的 cmd 冲突）
 *
 * 职责：
 * 1. callRequest(4)：忙线互斥判定（1:1 ↔ 群通话双向）→ 创建会话 → 服务端生成/回填 callId
 *    忙线时拦截转发并向主叫回 callReject(reason=busy)
 * 2. callAccept(5)：被叫接听校验 → 会话转 CONNECTING；会话失效时回 callReject(reason=invalid)
 * 3. offer(1)：转发前标记会话进入 TALKING（近似接通时刻）
 * 4. callReject(6)/callCancel(7)/callHangup(8)：先结束会话再放行转发
 * 5. 振铃超时：向主叫下发 callCancel(reason=timeout)
 * 6. 掉线清理：向对端下发 callHangup(reason=disconnect)
 * 7. 会话建立/结束时回调业务侧 ImSingleCallListener（话单统计等）
 *
 * @author gogym
 */
@Slf4j
public class SingleCallService extends BaseHandler implements SingleCallListener {

    private static final Gson GSON = new Gson();

    private final SingleCallSessionManager sessionManager;

    /**
     * 群通话管理器（可为 null）：用于 1:1 与群通话的双向占用互斥
     */
    private final GroupCallSessionManager groupCallManager;

    /**
     * ID 生成器（可为 null，为 null 时 callId 退化为 UUID）
     */
    private final ImIdGenerator idGenerator;

    /**
     * 业务侧通话事件回调列表（话单统计等，可为空列表）
     */
    private final List<ImSingleCallListener> businessListeners;

    public SingleCallService(IMServerFacade facade,
                               SingleCallSessionManager sessionManager,
                               GroupCallSessionManager groupCallManager,
                               ImIdGenerator idGenerator,
                               List<ImSingleCallListener> businessListeners) {
        super(facade);
        this.sessionManager = sessionManager;
        this.groupCallManager = groupCallManager;
        this.idGenerator = idGenerator;
        this.businessListeners = businessListeners == null
                ? Collections.emptyList() : businessListeners;
        // 管理器内部事件（超时/掉线/结束）→ 信令下发 + 业务回调
        sessionManager.setListener(this);
    }

    /**
     * 本服务不注册进 MessageDispatcher，仅为满足 BaseHandler 抽象方法；
     * cmd=50 由 RtcSingleHandler 处理
     */
    @Override
    public int cmd() {
        return Cmd.RTC_SIGNAL;
    }

    /**
     * 本服务不注册进 MessageDispatcher，该方法不应被调用；
     * 生命周期信令由 RtcSingleHandler 通过 onCallRequest/onAccept/onTermination 委托
     */
    @Override
    public void handle(ImProto.Packet packet, Channel channel, String userId) {
        log.warn("SingleCallService 不直接处理信令, 应由 RtcSingleHandler 委托调用, userId={}", userId);
    }

    // ====================== 信令编排（RtcSingleHandler 委托入口） ======================

    /**
     * 处理 callRequest(4)：忙线互斥 → 创建会话 → 回填服务端 callId
     *
     * @return 处理后的信令（可能改写 callId）供转发；null 表示已拦截（忙线/非法，已回信令）
     */
    public ImProto.RtcSignal onCallRequest(ImProto.RtcSignal signal, Channel channel, String userId) {
        String calleeId = signal.getReceiverId();
        if (calleeId.isEmpty() || calleeId.equals(userId)) {
            log.warn("1:1通话请求无效: userId={}, receiver={}", userId, calleeId);
            return null;
        }
        String callType = resolveCallType(signal.getPayload());

        // 忙线互斥：主叫/被叫任一在 1:1 或群通话中则拦截（群通话占用经 GroupCallSessionManager 判定）
        if (isAnyBusy(userId) || isAnyBusy(calleeId)) {
            log.info("1:1通话忙线拦截: caller={}, callee={}", userId, calleeId);
            sendSignal(SingleSignalType.CALL_REJECT, calleeId, userId, signal.getCallId(), CallEndReason.BUSY);
            return null;
        }

        String callId = resolveCallId(signal.getCallId());
        if (!sessionManager.createSession(callId, userId, calleeId, callType, channel, null)) {
            // 创建时被并发请求占用，兜底拦截
            log.info("1:1通话创建失败(并发占用): caller={}, callee={}", userId, calleeId);
            sendSignal(SingleSignalType.CALL_REJECT, calleeId, userId, callId, CallEndReason.BUSY);
            return null;
        }

        fireCallStart(callId, userId, calleeId, callType);
        log.info("1:1通话会话已创建: callId={}, caller={}, callee={}, type={}",
                callId, userId, calleeId, callType);
        // callId 为空时由服务端生成，重写后回传双方
        return signal.toBuilder().setCallId(callId).build();
    }

    /**
     * 处理 callAccept(5)：被叫接听校验与会话状态推进
     *
     * @return true=放行转发给主叫；false=已拦截（会话失效，已回信令）
     */
    public boolean onAccept(ImProto.RtcSignal signal, Channel channel, String userId) {
        String callId = signal.getCallId();
        if (callId == null || callId.isEmpty()) {
            log.warn("1:1通话接听缺少callId: userId={}", userId);
            return false;
        }
        if (!sessionManager.acceptSession(callId, userId, channel)) {
            log.info("1:1通话接听被拦截(会话已失效): callId={}, userId={}", callId, userId);
            // 通话已结束/不存在，告知接听方（如对端先取消了）
            sendSignal(SingleSignalType.CALL_REJECT, "", userId, callId, CallEndReason.REJECTED);
            return false;
        }
        return true;
    }

    /**
     * 处理 offer(1) 转发前的接通标记（近似接通时刻，非媒体层真实建联时刻）
     */
    public void onTalkStart(String callId) {
        if (callId == null || callId.isEmpty()) {
            return;
        }
        sessionManager.startTalking(callId);
    }

    /**
     * 处理终止类信令（callReject(6)/callCancel(7)/callHangup(8)）：先结束会话，由调用方继续转发
     */
    public void onTermination(ImProto.RtcSignal signal, String userId, CallEndReason reason) {
        String callId = signal.getCallId();
        if (callId == null || callId.isEmpty()) {
            return;
        }
        sessionManager.endSession(callId, reason);
    }

    // ====================== 内部事件（SingleCallSessionManager 回调） ======================

    @Override
    public void onCallTimeout(SingleCallSession session) {
        // 向主叫下发取消信令（reason=timeout），客户端据此结束呼叫界面
        sendSignal(SingleSignalType.CALL_CANCEL, session.getCalleeId(),
                session.getCallerId(), session.getCallId(), CallEndReason.TIMEOUT);
    }

    @Override
    public void onCallDisconnected(SingleCallSession session, String disconnectedId) {
        String peerId = session.getPeerId(disconnectedId);
        if (peerId == null) {
            return;
        }
        // 以掉线方名义向对端下发挂断信令（reason=disconnect）
        sendSignal(SingleSignalType.CALL_HANGUP, disconnectedId, peerId, session.getCallId(), CallEndReason.DISCONNECTED);
    }

    @Override
    public void onSessionEnded(SingleCallSession session, CallEndReason reason) {
        if (reason == null) {
            return;
        }
        fireCallEnd(session, reason);
    }

    // ====================== 内部工具 ======================

    /**
     * 综合占用判定：1:1 通话 + 群通话（本节点可见范围）
     */
    private boolean isAnyBusy(String userId) {
        if (sessionManager.isInCall(userId)) {
            return true;
        }
        return groupCallManager != null && groupCallManager.isUserBusy(userId);
    }

    private String resolveCallId(String clientCallId) {
        if (clientCallId != null && !clientCallId.isEmpty()) {
            return clientCallId;
        }
        if (idGenerator != null) {
            return idGenerator.generateMsgId();
        }
        return UUID.randomUUID().toString();
    }

    private String resolveCallType(String payload) {
        try {
            SingleCallRequestDto call = GSON.fromJson(payload, SingleCallRequestDto.class);
            if (call != null && call.getCallType() != null && !call.getCallType().isEmpty()) {
                return call.getCallType();
            }
        } catch (Exception e) {
            log.debug("1:1通话请求payload解析失败, 使用默认通话类型: {}", e.getMessage());
        }
        return "audio";
    }

    /**
     * 服务端主动下发信令（忙线拒绝/超时取消/掉线挂断）
     */
    private void sendSignal(SingleSignalType type, String fromUserId, String toUserId,
                            String callId, CallEndReason reason) {
        try {
            ImProto.RtcSignal signal = ImProto.RtcSignal.newBuilder()
                    .setSignalType(type.getCode())
                    .setSenderId(fromUserId == null ? "" : fromUserId)
                    .setReceiverId(toUserId)
                    .setCallId(callId == null ? "" : callId)
                    .setPayload(GSON.toJson(Collections.singletonMap("reason",
                            reason == null ? "" : reason.getCode())))
                    .build();
            boolean delivered = routeToUser(toUserId, PacketCodec.create(Cmd.RTC_SIGNAL, 0, signal));
            if (!delivered) {
                log.debug("1:1通话服务端信令目标用户离线: type={}, to={}, callId={}", type, toUserId, callId);
            } else {
                log.debug("1:1通话服务端信令已下发: type={}, from={}, to={}, callId={}",
                        type, fromUserId, toUserId, callId);
            }
        } catch (Exception e) {
            log.error("1:1通话服务端信令下发失败: type={}, to={}, callId={}", type, toUserId, callId, e);
        }
    }

    private void fireCallStart(String callId, String callerId, String calleeId, String callType) {
        for (ImSingleCallListener listener : businessListeners) {
            try {
                listener.onCallStart(callId, callerId, calleeId, callType);
            } catch (Exception e) {
                log.error("1:1通话开始回调异常, callId={}", callId, e);
            }
        }
    }

    private void fireCallEnd(SingleCallSession session, CallEndReason reason) {
        for (ImSingleCallListener listener : businessListeners) {
            try {
                listener.onCallEnd(session.getCallId(), session.getCallerId(), session.getCalleeId(),
                        session.getCallType(), session.getDurationSeconds(), reason);
            } catch (Exception e) {
                log.error("1:1通话结束回调异常, callId={}", session.getCallId(), e);
            }
        }
    }
}
