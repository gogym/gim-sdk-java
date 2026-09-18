package io.getbit.gim.core.config.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * RtcGroupCallProperties.java
 *
 * 群视频通话（混合架构）配置
 * 纯 POJO，由 starter 层绑定 gim.rtc-group-call.* 配置项
 *
 * @author gogym
 */
@Getter
@Setter
public class RtcGroupCallProperties {

    /**
     * 是否启用群通话（signalType 20~27 生命周期信令处理）
     */
    private boolean enabled = true;

    /**
     * 媒体模式选择策略：auto（按人数自动选择）/ mesh / sfu
     */
    private String mode = "auto";

    /**
     * 单个群通话房间同时在线人数上限（发起人 + 受邀成员），超过则不再邀请/拒绝加入
     */
    private int maxMembers = 9;

    /**
     * Mesh 模式人数上限（auto 模式下超过该人数自动切换 SFU）
     */
    private int meshMaxMembers = 8;

    /**
     * 邀请超时时间（秒）：RINGING 房间超过该时长无人加入则自动结束
     */
    private int inviteTimeoutSeconds = 60;

    /**
     * 空房间回收时间（秒）：全员离开后延迟销毁房间，避免闪断重连导致房间丢失
     */
    private int emptyRoomTtlSeconds = 30;

    /**
     * SFU 提供方：none（不启用 SFU）/ livekit（内置参考实现）
     */
    private String sfuProvider = "none";

    /**
     * LiveKit Server HTTP 地址（服务端 API 调用），如 http://127.0.0.1:7880
     */
    private String sfuHost;

    private String sfuApiKey;

    private String sfuApiSecret;

    /**
     * 下发给客户端的 SFU 连接地址，如 wss://im.example.com:7880
     */
    private String sfuWsUrl;

    /**
     * SFU 接入 token 有效期（秒）
     */
    private int sfuTokenTtlSeconds = 3600;
}
