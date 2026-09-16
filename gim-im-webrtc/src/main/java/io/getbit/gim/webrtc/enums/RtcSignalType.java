package io.getbit.gim.webrtc.enums;

import lombok.Getter;

/**
 * RtcSignalType.java
 *
 * WebRTC 信令类型枚举（与客户端 RtcSignalType 及 ImProto.proto 注释保持一致）
 * 1~8：1:1 通话与媒体信令；9~17：群通话生命周期与媒体开关信令
 *
 * @author gogym
 */
@Getter
public enum RtcSignalType {

    /**
     * SDP Offer
     */
    OFFER(1),

    /**
     * SDP Answer
     */
    ANSWER(2),

    /**
     * ICE Candidate
     */
    ICE_CANDIDATE(3),

    /**
     * 通话请求
     */
    CALL_REQUEST(4),

    /**
     * 通话接受
     */
    CALL_ACCEPT(5),

    /**
     * 通话拒绝
     */
    CALL_REJECT(6),

    /**
     * 通话取消
     */
    CALL_CANCEL(7),

    /**
     * 挂断
     */
    CALL_HANGUP(8),

    /**
     * 群通话发起
     */
    GROUP_CALL_REQUEST(9),

    /**
     * 群通话邀请（服务端下发）
     */
    GROUP_CALL_INVITE(10),

    /**
     * 群通话加入
     */
    GROUP_CALL_JOIN(11),

    /**
     * 群通话拒绝
     */
    GROUP_CALL_REJECT(12),

    /**
     * 群通话离开
     */
    GROUP_CALL_LEAVE(13),

    /**
     * 群通话结束
     */
    GROUP_CALL_END(14),

    /**
     * 成员变更通知（服务端下发）
     */
    PARTICIPANT_NOTIFY(15),

    /**
     * 房间状态快照（服务端下发）
     */
    ROOM_STATE(16),

    /**
     * 媒体开关状态（对端切换摄像头/麦克风时通知）
     */
    MEDIA_STATE(17);

    private final int code;

    RtcSignalType(int code) {
        this.code = code;
    }

    /**
     * 按 signalType 数值解析枚举
     *
     * @return 对应枚举，未知类型返回 null
     */
    public static RtcSignalType fromCode(int code) {
        for (RtcSignalType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
