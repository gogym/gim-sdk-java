package com.example.im.spi;

import io.getbit.gim.webrtc.enums.CallEndReason;
import io.getbit.gim.webrtc.singlecall.listener.ImSingleCallListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 1:1 通话事件回调示例 — 话单打印
 * <p>
 * 实现 ImSingleCallListener 并注册为 Spring Bean，
 * SDK 自动收集所有该类型 Bean，在通话建立/结束时回调。
 * 回调异常已被 SDK 隔离，不影响通话流程。
 *
 * @author gogym
 */
@Slf4j
@Component
public class SingleCallListenerImpl implements ImSingleCallListener {

    @Override
    public void onCallStart(String callId, String callerId, String calleeId, String callType) {
        log.info("[话单] 通话建立: callId={}, caller={}, callee={}, type={}",
                callId, callerId, calleeId, callType);
    }

    @Override
    public void onCallEnd(String callId, String callerId, String calleeId,
                          String callType, long durationSeconds, CallEndReason reason) {
        log.info("[话单] 通话结束: callId={}, caller={}, callee={}, type={}, duration={}s, reason={}({})",
                callId, callerId, calleeId, callType, durationSeconds, reason, reason.getDesc());
        // 实际业务可在此持久化通话记录 / 计费等
    }
}
