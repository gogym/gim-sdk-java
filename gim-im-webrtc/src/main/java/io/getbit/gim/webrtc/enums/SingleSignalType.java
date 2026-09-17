package io.getbit.gim.webrtc.enums;

import lombok.Getter;

/**
 * SingleSignalType.java
 *
 * 1:1 通话信令类型枚举（signalType 1~8，与客户端及 ImProto.proto 注释保持一致）
 * 群通话信令（9~17）见 {@link GroupSignalType}，二者共用同一数值空间但职责分离
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
    CALL_HANGUP(8);

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
