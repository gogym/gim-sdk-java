package io.getbit.gim.webrtc.enums;

/**
 * GroupCallRoomStatus.java
 *
 * 群通话房间状态机：CREATED → RINGING → TALKING → ENDED
 *
 * @author gogym
 */
public enum GroupCallRoomStatus {

    /**
     * 已创建（瞬时态，创建后立即进入 RINGING）
     */
    CREATED,

    /**
     * 邀请中（等待成员响应）
     */
    RINGING,

    /**
     * 通话中（至少两名成员已加入）
     */
    TALKING,

    /**
     * 已结束
     */
    ENDED
}
