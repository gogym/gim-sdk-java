package io.getbit.gim.starter;

import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.config.properties.RtcGroupCallProperties;
import io.getbit.gim.core.spi.ImRedisAdapter;
import io.getbit.gim.webrtc.groupcall.config.GroupCallConfig;
import io.getbit.gim.webrtc.groupcall.GroupCallSessionManager;
import io.getbit.gim.webrtc.singlecall.SingleCallSessionManager;
import io.getbit.gim.webrtc.sfu.LiveKitConfig;
import io.getbit.gim.webrtc.sfu.LiveKitSfuAdapter;
import io.getbit.gim.webrtc.sfu.SfuAdapter;
import io.getbit.gim.webrtc.sfu.TurnConfig;
import io.getbit.gim.webrtc.sfu.TurnCredentialService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebRtcAutoConfiguration.java
 * <p>
 * WebRTC 模块 Spring Boot 自动配置
 * 包含 1:1 通话、TURN 凭据与群通话（混合架构：Mesh + SFU）组件
 * 服务器启停由 gim.enable 控制（见 GimAutoConfiguration 的 nettyServerLifecycle）
 *
 * @author gogym
 */
@Configuration
public class WebRtcAutoConfiguration {

    /**
     * 1:1 通话会话生命周期管理器
     * 由 SingleCallSessionManager 依据 gim.enable-cluster 自行决定存储层：集群模式用 Redis（跨节点可见），单机模式退化为本地会话表；
     * 关闭时通过 shutdown 释放调度线程与本地状态（Redis 状态由 TTL 兜底回收，TALKING 会话由续期任务滚动续期）
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public SingleCallSessionManager singleCallSessionManager(GimProperties gimProperties,
                                                     ObjectProvider<ImRedisAdapter> redisAdapterProvider) {
        return new SingleCallSessionManager(gimProperties, redisAdapterProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "gim.webrtc.turn")
    public TurnConfig turnConfig() {
        return new TurnConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public TurnCredentialService turnCredentialService(TurnConfig turnConfig) {
        return new TurnCredentialService(turnConfig);
    }

    // ==================== 群通话（混合架构：Mesh + SFU） ====================

    /**
     * SFU 适配器：配置 gim.rtc-group-call.sfu-provider=livekit 时启用内置 LiveKit 参考实现；
     * 使用方可自定义 SfuAdapter Bean 对接任意 SFU（Jitsi / mediasoup / 自研）
     */
    @Bean
    @ConditionalOnMissingBean(SfuAdapter.class)
    @ConditionalOnProperty(prefix = "gim.rtc-group-call", name = "sfu-provider", havingValue = "livekit")
    public SfuAdapter liveKitSfuAdapter(GimProperties gimProperties) {
        RtcGroupCallProperties p = gimProperties.getRtcGroupCall();
        LiveKitConfig config = new LiveKitConfig();
        if (p.getSfuHost() != null) {
            config.setHost(p.getSfuHost());
        }
        if (p.getSfuApiKey() != null) {
            config.setApiKey(p.getSfuApiKey());
        }
        if (p.getSfuApiSecret() != null) {
            config.setApiSecret(p.getSfuApiSecret());
        }
        if (p.getSfuWsUrl() != null) {
            config.setWsUrl(p.getSfuWsUrl());
        }
        if (p.getSfuTokenTtlSeconds() > 0) {
            config.setTokenTtlSeconds(p.getSfuTokenTtlSeconds());
        }
        return new LiveKitSfuAdapter(config);
    }

    /**
     * 群通话房间生命周期管理器
     * 关闭时通过 shutdown 释放调度线程与房间状态
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "gim.rtc-group-call", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GroupCallSessionManager groupCallSessionManager(GimProperties gimProperties,
                                                           ObjectProvider<SfuAdapter> sfuAdapterProvider,
                                                           ObjectProvider<SingleCallSessionManager> singleCallProvider) {
        RtcGroupCallProperties p = gimProperties.getRtcGroupCall();

        GroupCallConfig config = new GroupCallConfig();
        config.setMode(p.getMode());
        config.setMaxMembers(p.getMaxMembers());
        config.setMeshMaxMembers(p.getMeshMaxMembers());
        config.setInviteTimeoutSeconds(p.getInviteTimeoutSeconds());
        config.setEmptyRoomTtlSeconds(p.getEmptyRoomTtlSeconds());

        return new GroupCallSessionManager(
                config,
                sfuAdapterProvider.getIfAvailable(),
                singleCallProvider.getIfAvailable());
    }
}
