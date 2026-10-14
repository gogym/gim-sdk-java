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

package io.getbit.gim.webrtc.sfu;

import io.getbit.gim.webrtc.dto.TurnCredentialsDto;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * TURN 凭证生成服务
 *
 * @author gogym
 */
@Slf4j
public class TurnCredentialService {

    private final TurnConfig config;

    public TurnCredentialService(TurnConfig config) {
        this.config = config;
    }

    public TurnCredentialsDto generateTurnInfo() {
        long timestamp = System.currentTimeMillis() / 1000 + config.getCredentialTtl();
        String username = String.valueOf(timestamp);
        String password;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(config.getSharedSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            password = Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("TURN凭证生成失败", e);
            return null;
        }
        TurnCredentialsDto turnInfo = new TurnCredentialsDto();
        turnInfo.setStunUrl(config.getStunUrl());
        turnInfo.setTurnUrl(config.getTurnUrl());
        turnInfo.setUsername(username);
        turnInfo.setCredential(password);
        return turnInfo;
    }
}
