package io.getbit.gim.webrtc.enums;

import lombok.Getter;

/**
 * 1:1 通话会话状态机
 * 仅含生命周期状态；结束原因（拒绝/取消/超时/忙线等）由 {@link CallEndReason} 承载，二者不重叠
 */
@Getter
public enum SingleCallSessionStatus {
    CALLING(1, "呼叫中"), CONNECTING(2, "连接中"),
    TALKING(3, "通话中"), ENDED(4, "已结束");

    private final int code;
    private final String desc;
    SingleCallSessionStatus(int code, String desc) { this.code = code; this.desc = desc; }

}
