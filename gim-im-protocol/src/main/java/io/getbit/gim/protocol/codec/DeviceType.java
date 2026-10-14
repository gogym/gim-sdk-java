/*
 * Copyright 2026 gogym
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getbit.gim.protocol.codec;

import lombok.Getter;

import java.util.Arrays;

/**
 * DeviceType.java
 * <p>
 * 设备类型枚举
 * 同一用户相同设备类型只保留最新连接（互踢），不同设备类型可共存
 *
 * @author gogym
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
