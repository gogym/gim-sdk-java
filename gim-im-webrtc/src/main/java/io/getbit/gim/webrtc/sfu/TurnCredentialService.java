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
