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

package io.getbit.gim.core.spi;

/**
 * ImTokenVerifier.java
 *
 * SPI接口：Token验证器
 * 使用方自行提供Token校验实现（JWT / OAuth / 自定义）
 *
 * @author gogym
 */
public interface ImTokenVerifier {

    /**
     * 验证Token并提取用户ID
     *
     * @param token 客户端携带的认证令牌
     * @return 验证通过返回用户ID字符串，失败返回 null
     */
    String verifyAndExtractUserId(String token);
}
