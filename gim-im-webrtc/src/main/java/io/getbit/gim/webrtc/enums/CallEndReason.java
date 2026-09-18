package io.getbit.gim.webrtc.enums;

import lombok.Getter;

/**
 * CallEndReason.java
 *
 * 1:1 通话结束原因枚举
 * 用于通话事件回调（ImSingleCallListener）与服务端下发信令的 payload reason 字段
 *
 * @author gogym
 */
@Getter
public enum CallEndReason {

    /**
     * 通话中挂断（callHangup）
     */
    ANSWERED("normal", "通话结束"),

    /**
     * 被叫拒绝（callReject）
     */
    REJECTED("reject", "对方拒绝"),

    /**
     * 主叫取消（callCancel）
     */
    CANCELLED("cancel", "对方取消"),

    /**
     * 振铃超时无应答（服务端自动取消）
     */
    TIMEOUT("timeout", "无应答超时"),

    /**
     * 一方全部设备离线（服务端清理）
     */
    DISCONNECTED("disconnect", "对方离线"),

    /**
     * 忙线拒绝（被叫通话占用中，服务端拦截）
     */
    BUSY("busy", "对方忙线"),

    /**
     * 接听后连接失败/超时（客户端建联失败上报，或服务端 CONNECTING 阶段超时兜底）
     */
    CONNECT_FAILED("failed", "连接失败");

    /**
     * payload reason 字段取值（与客户端约定，小写）
     */
    private final String code;

    private final String desc;

    CallEndReason(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
