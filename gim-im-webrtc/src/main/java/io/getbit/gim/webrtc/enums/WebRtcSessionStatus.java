package io.getbit.gim.webrtc.enums;

import lombok.Getter;

@Getter
public enum WebRtcSessionStatus {
    CALLING(1, "呼叫中"), RINGING(2, "响铃中"), CONNECTING(3, "连接中"),
    TALKING(4, "通话中"), ENDED(5, "已挂断"), REJECTED(6, "已拒绝"),
    CANCELLED(7, "已取消"), TIMEOUT(8, "超时"), BUSY(9, "忙线");

    private final int code;
    private final String desc;
    WebRtcSessionStatus(int code, String desc) { this.code = code; this.desc = desc; }

}
