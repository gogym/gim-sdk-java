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

package com.example.im.spi;

import io.getbit.gim.core.spi.ImTokenVerifier;
import org.springframework.stereotype.Component;

/**
 * Token 验证器实现
 * <p>
 * SDK 在 Netty 连接握手时调用此方法验证客户端身份。
 * 你可以替换为 JWT 解析、OAuth2 校验、或自定义签名验证。
 * <p>
 * 示例：简单格式 "userId:timestamp"，生产环境请使用 JWT 等安全方案。
 *
 * @author gogym
 */
@Component
public class TokenVerifierImpl implements ImTokenVerifier {

    @Override
    public String verifyAndExtractUserId(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        // ===== 示例实现：简单冒号分隔格式 =====
        // 生产环境请替换为 JWT 解析或其他安全方案
        // 例如:
        //   Claims claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
        //   return claims.getSubject();

        try {
            // token 格式: "userId:timestamp"
            int colonIndex = token.indexOf(':');
            if (colonIndex > 0) {
                String userId = token.substring(0, colonIndex);
                Long.parseLong(userId); // 验证是合法数字
                return userId;
            }
            // 也支持纯数字 token（直接用作用户ID）
            Long.parseLong(token);
            return token;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
