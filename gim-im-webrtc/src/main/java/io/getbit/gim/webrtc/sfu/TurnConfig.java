package io.getbit.gim.webrtc.sfu;

import lombok.Data;

/**
 * WebRTC TURN 配置
 *
 * @author gogym
 */
@Data
public class TurnConfig {
    private String stunUrl = "stun:127.0.0.1:3478";
    private String turnUrl = "turn:127.0.0.1:3478";
    private String sharedSecret = "secret";
    private int credentialTtl = 3600;
}
