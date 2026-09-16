package io.getbit.gim.protocol.codec;

import lombok.Getter;

import java.util.Arrays;

/**
 * DeviceType.java
 * <p>
 * 设备类型枚举
 * 同一用户相同设备类型只保留最新连接（互踢），不同设备类型可共存
 */
@Getter
public enum DeviceType {

    /**
     * 手机端
     */
    MOBILE("mobile"),

    /**
     * 桌面客户端
     */
    DESKTOP("desktop"),

    /**
     * 网页端
     */
    WEB("web"),

    /**
     * 平板端
     */
    PAD("pad");

    private final String code;

    DeviceType(String code) {
        this.code = code;
    }

    /**
     * 根据 code 获取设备类型
     *
     * @param code 设备类型编码
     * @return 设备类型，未找到返回 MOBILE 作为默认值
     */
    public static DeviceType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return MOBILE;
        }
        return Arrays.stream(values())
                .filter(d -> d.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(MOBILE);
    }
}
