package io.getbit.gim.webrtc.enums;

import lombok.Getter;

/**
 * GroupSignalType.java
 *
 * 群通话信令类型枚举（signalType 9~17，与客户端及 ImProto.proto 注释保持一致）
 * 1:1 通话信令（1~8）见 {@link SingleSignalType}，二者共用同一数值空间但职责分离
 *
 * @author gogym
 */
@Getter
public enum GroupSignalType {

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
     * 媒体开关状态（成员切换摄像头/麦克风时上报，服务端广播给其他在通话成员）
     */
    MEDIA_STATE(17);

    private final int code;

    GroupSignalType(int code) {
        this.code = code;
    }

    /**
     * 按 signalType 数值解析枚举
     *
     * @return 对应枚举，未知类型返回 null
     */
    public static GroupSignalType fromCode(int code) {
        for (GroupSignalType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
