package io.getbit.gim.webrtc.sfu;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * TURN 凭证生成服务
 *
 * @author gogym
 */
@Slf4j
public class TurnCredentialService {

    private final String stunUrl;
    private final String turnUrl;
    private final String sharedSecret;
    private final int credentialTtl;

    public TurnCredentialService(String stunUrl, String turnUrl, String sharedSecret, int credentialTtl) {
        this.stunUrl = stunUrl;
        this.turnUrl = turnUrl;
        this.sharedSecret = sharedSecret;
        this.credentialTtl = credentialTtl;
    }

    /**
     * 使用默认配置构造（本地开发环境）
     */
    public TurnCredentialService() {
        this("stun:127.0.0.1:3478", "turn:127.0.0.1:3478", "secret", 3600);
    }

    public Map<String, Object> generateTurnInfo() {
        long timestamp = System.currentTimeMillis() / 1000 + credentialTtl;
        String username = String.valueOf(timestamp);
        String password;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            password = Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("TURN凭证生成失败", e);
            return null;
        }
        Map<String, Object> turnInfo = new HashMap<>();
        turnInfo.put("stunUrl", stunUrl);
        turnInfo.put("turnUrl", turnUrl);
        turnInfo.put("username", username);
        turnInfo.put("credential", password);
        return turnInfo;
    }

    // ==================== 配置类 ====================

    /**
     * WebRTC TURN 配置
     */
    @Getter
    @Setter
    public static class TurnConfig {
        private String stunUrl = "stun:127.0.0.1:3478";
        private String turnUrl = "turn:127.0.0.1:3478";
        private String sharedSecret = "secret";
        private int credentialTtl = 3600;
    }
}
