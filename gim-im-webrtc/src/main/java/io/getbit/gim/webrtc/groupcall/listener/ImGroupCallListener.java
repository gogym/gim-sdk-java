package io.getbit.gim.webrtc.groupcall.listener;

import io.getbit.gim.webrtc.enums.GroupCallMode;

/**
 * ImGroupCallListener.java
 *
 * 群通话业务事件 SPI（与 1:1 通话的 ImSingleCallListener 对称）
 * Spring 环境下实现为 Bean 即被自动收集；非 Spring 环境经 GimBootstrap 装配传入
 * 回调异常被捕获隔离，不影响信令流程
 *
 * @author gogym
 */
public interface ImGroupCallListener {

    /**
     * 群通话发起成功（房间已创建）
     *
     * @param callId      通话唯一ID
     * @param groupId     群组ID
     * @param roomId      房间ID
     * @param initiatorId 发起人 userId
     * @param callType    通话类型 audio / video
     * @param mode        媒体模式 mesh / sfu
     */
    default void onCallStart(String callId, String groupId, String roomId, String initiatorId,
                             String callType, GroupCallMode mode) {
    }

    /**
     * 群通话结束
     *
     * @param durationSeconds 通话时长（秒，从首位成员加入起算；无人加入为 0）
     * @param endReason       结束原因：ended=发起人结束 / timeout=邀请超时 / empty=空房间回收
     */
    default void onCallEnd(String callId, String groupId, String roomId, String initiatorId,
                           String callType, GroupCallMode mode, long durationSeconds, String endReason) {
    }

    /**
     * 成员加入通话（幂等重连不触发）
     */
    default void onMemberJoin(String callId, String roomId, String userId) {
    }

    /**
     * 成员离开通话
     *
     * @param reason 离开原因：leave=主动退出 / disconnect=掉线清理
     */
    default void onMemberLeave(String callId, String roomId, String userId, String reason) {
    }
}
