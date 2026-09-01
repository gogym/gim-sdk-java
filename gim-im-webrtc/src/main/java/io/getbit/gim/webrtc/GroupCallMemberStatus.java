package io.getbit.gim.webrtc;

/**
 * GroupCallMemberStatus.java
 *
 * 群通话成员状态机：INVITED → JOINED → LEFT（或 INVITED → REJECTED）
 *
 * @author gogym
 */
public enum GroupCallMemberStatus {

    /**
     * 已邀请，未响应
     */
    INVITED,

    /**
     * 已加入通话
     */
    JOINED,

    /**
     * 已退出（主动退出或掉线）
     */
    LEFT,

    /**
     * 已拒绝邀请
     */
    REJECTED
}
