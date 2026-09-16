package io.getbit.gim.webrtc.sfu;

import lombok.Getter;

/**
 * SfuToken.java
 *
 * SFU 接入凭证（不可变对象）
 * 由 SfuAdapter 实现方签发，客户端用它连接 SFU 收发媒体流
 *
 * @author gogym
 */
@Getter
public final class SfuToken {

    /**
     * 接入 token（如 LiveKit JWT access token）
     */
    private final String token;

    /**
     * SFU 连接地址（如 LiveKit 的 wss:// 地址）
     */
    private final String url;

    public SfuToken(String token, String url) {
        this.token = token;
        this.url = url;
    }
}
