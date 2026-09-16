package io.getbit.gim.webrtc.sfu;

import lombok.Getter;
import lombok.Setter;

/**
 * LiveKitConfig.java
 *
 * LiveKit 连接配置
 * 默认值面向本地开发环境，生产环境请覆盖 host/apiKey/apiSecret/wsUrl
 *
 * @author gogym
 */
@Getter
@Setter
public class LiveKitConfig {

    /**
     * LiveKit Server HTTP 地址（服务端 API 调用），如 http://127.0.0.1:7880
     */
    private String host = "http://127.0.0.1:7880";

    /**
     * LiveKit API Key
     */
    private String apiKey = "devkey";

    /**
     * LiveKit API Secret
     */
    private String apiSecret = "secret";

    /**
     * 下发给客户端的 SFU 连接地址，如 wss://im.example.com:7880
     */
    private String wsUrl = "ws://127.0.0.1:7880";

    /**
     * 接入 token 有效期（秒）
     */
    private int tokenTtlSeconds = 3600;
}
