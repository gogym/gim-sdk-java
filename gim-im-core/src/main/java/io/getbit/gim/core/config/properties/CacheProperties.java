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

package io.getbit.gim.core.config.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * CacheProperties.java
 *
 * 本地缓存配置
 *
 * @author gogym
 */
@Getter
@Setter
public class CacheProperties {

    /** 本地缓存最大容量 */
    private int maxSize = 100_000;

    /** 本地缓存过期时间（秒） */
    private int expireSeconds = 300;
}
