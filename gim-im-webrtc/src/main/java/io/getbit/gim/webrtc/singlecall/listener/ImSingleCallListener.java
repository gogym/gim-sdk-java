package io.getbit.gim.webrtc.singlecall.listener;

import io.getbit.gim.webrtc.enums.CallEndReason;

/**
 * ImSingleCallListener.java
 *
 * 1:1 通话事件回调 SPI（业务扩展点）
 * 使用方实现此接口并注册为 Spring Bean，即可收到通话开始/结束事件，
 * 典型用途：话单统计、通话时长计费、通话记录持久化。
 * 回调在 SDK 内部以 try-catch 隔离，实现方抛出异常不影响通话流程。
 *
 * 非 Spring 环境（GimBootstrap 手动装配）可通过
 * SingleCallService 构造参数直接传入实现列表。
 *
 * @author gogym
 */
public interface ImSingleCallListener {

    /**
     * 通话建立：被叫振铃、会话创建成功时触发（此时通话可能尚未接通）
     *
     * @param callId   通话唯一ID（服务端生成）
     * @param callerId 主叫用户ID
     * @param calleeId 被叫用户ID
     * @param callType 通话类型 audio / video
     */
    default void onCallStart(String callId, String callerId, String calleeId, String callType) {
    }

    /**
     * 通话结束：所有结束路径（挂断/拒绝/取消/超时/掉线）的统一回调
     *
     * @param callId          通话唯一ID
     * @param callerId        主叫用户ID
     * @param calleeId        被叫用户ID
     * @param callType        通话类型 audio / video
     * @param durationSeconds 通话时长（秒）；未接通的通话为 0
     * @param reason          结束原因
     */
    default void onCallEnd(String callId, String callerId, String calleeId,
                           String callType, long durationSeconds, CallEndReason reason) {
    }
}
