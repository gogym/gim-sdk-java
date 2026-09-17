package io.getbit.gim.webrtc.singlecall.listener;

import io.getbit.gim.webrtc.enums.CallEndReason;
import io.getbit.gim.webrtc.singlecall.model.SingleCallSession;

/**
 * SingleCallListener.java
 *
 * 1:1 通话内部事件监听器（SDK 内部使用，与 GroupCallListener 对称）
 * 由 SingleCallService 实现并注册到 SingleCallSessionManager，
 * 用于将管理器内部触发的事件（振铃超时、掉线清理、会话结束）转化为信令下发与业务回调。
 *
 * @author gogym
 */
public interface SingleCallListener {

    /**
     * 振铃超时：CALLING 状态超过 ringTimeoutSeconds 无人接听，会话已被自动结束
     * 实现方应向主叫下发 CALL_CANCEL 信令
     *
     * @param session 已结束的会话（状态 ENDED）
     */
    default void onCallTimeout(SingleCallSession session) {
    }

    /**
     * 一方全部设备离线，其进行中的通话已被服务端清理
     * 实现方应向对端下发 CALL_HANGUP 信令
     *
     * @param session        已结束的会话（状态 ENDED）
     * @param disconnectedId 掉线用户ID
     */
    default void onCallDisconnected(SingleCallSession session, String disconnectedId) {
    }

    /**
     * 会话已结束（所有结束路径的统一收口，事件在 Redis/内存状态清理后触发）
     *
     * @param session 已结束的会话（状态 ENDED，含时长信息）
     * @param reason  结束原因
     */
    default void onSessionEnded(SingleCallSession session, CallEndReason reason) {
    }
}
