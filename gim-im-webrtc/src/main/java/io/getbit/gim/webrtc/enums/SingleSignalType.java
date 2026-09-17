package io.getbit.gim.webrtc.enums;

import lombok.Getter;

/**
 * SingleSignalType.java
 *
 * 1:1 通话信令类型枚举（signalType 1~8 生命周期与 SDP/ICE 信令，9 为服务端回传主叫的呼叫确认 CALL_ACK；
 * 10~19 预留给 1:1 后续扩展；媒体开关信令 MEDIA_STATE=100 为跨场景独立高位编号、与群通话共用，
 * 与客户端及 ImProto.proto 注释保持一致）
 * 群通话信令（20~27）见 {@link GroupSignalType}，二者共用同一数值空间但职责分离；
 * MEDIA_STATE(100) 在 1:1 场景由服务端纯转发（P2P 透传），在群通话场景由 GroupCallService
 * 聚合成员状态后广播
 *
 * @author gogym
 */
@Getter
public enum SingleSignalType {

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
     * 呼叫确认（服务端 → 主叫）：主叫发送 callRequest 且未携带 callId 时，
     * 服务端生成 callId 后以本信令回传主叫，使双方持有同一权威 callId（区别于 callRequest，避免被误判为新来电）
     */
    CALL_ACK(9),

    /**
     * 媒体开关状态（成员切换摄像头/麦克风时上报，1:1 场景服务端纯转发给对端）
     */
    MEDIA_STATE(100);

    private final int code;

    SingleSignalType(int code) {
        this.code = code;
    }

    /**
     * 按 signalType 数值解析枚举
     *
     * @return 对应枚举，未知类型返回 null
     */
    public static SingleSignalType fromCode(int code) {
        for (SingleSignalType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
