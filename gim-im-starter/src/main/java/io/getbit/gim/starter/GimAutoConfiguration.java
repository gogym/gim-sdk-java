package io.getbit.gim.starter;

import io.getbit.gim.core.bootstrap.GimBootstrap;
import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.connection.server.NettyServer;
import io.getbit.gim.core.message.handler.BaseHandler;
import io.getbit.gim.core.spi.*;
import io.getbit.gim.webrtc.groupcall.GroupCallService;
import io.getbit.gim.webrtc.groupcall.GroupCallSessionManager;
import io.getbit.gim.webrtc.handler.RtcGroupHandler;
import io.getbit.gim.webrtc.handler.RtcSignalHandler;
import io.getbit.gim.webrtc.sfu.TurnCredentialService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GimAutoConfiguration.java
 *
 * GIM SDK Spring Boot 自动配置
 * 使用方只需引入 gim-im-starter 依赖 + 提供 SPI 实现即可自动启动
 * 配置 gim.enable=false 可禁用 IM 服务器启动（默认 true）：
 * 各组件 Bean 仍然注册（业务代码可注入 IMServerFacade），只是不监听端口、不接入集群
 *
 * @author gogym
 */
@Configuration
@EnableConfigurationProperties(GimSpringProperties.class)
public class GimAutoConfiguration {

    // ==================== 核心组件 ====================

    @Bean
    @ConditionalOnMissingBean
    public GimProperties gimProperties(GimSpringProperties springProperties) {
        return springProperties.toCoreProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public GimBootstrap.StartContext gimStartContext(GimProperties config,
                                                      ImTokenVerifier tokenVerifier,
                                                      ImRedisAdapter redisAdapter,
                                                      ImIdGenerator idGenerator,
                                                      ObjectProvider<ImRedisSubscriber> redisSubscriberProvider,
                                                      ObjectProvider<ImGroupMemberProvider> groupMemberProviderProvider,
                                                      ObjectProvider<ImFriendProvider> friendProviderProvider,
                                                      ObjectProvider<List<ImEventListener>> eventListenersProvider,
                                                      ObjectProvider<GroupCallSessionManager> groupCallManagerProvider,
                                                      ObjectProvider<TurnCredentialService> turnCredentialServiceProvider) {
        GimBootstrap.Builder builder = GimBootstrap.builder()
                .config(config)
                .tokenVerifier(tokenVerifier)
                .redisAdapter(redisAdapter)
                .idGenerator(idGenerator);

        ImRedisSubscriber subscriber = redisSubscriberProvider.getIfAvailable();
        if (subscriber != null) {
            builder.redisSubscriber(subscriber);
        }

        ImGroupMemberProvider groupProvider = groupMemberProviderProvider.getIfAvailable();
        if (groupProvider != null) {
            builder.groupMemberProvider(groupProvider);
        }

        ImFriendProvider friendProvider = friendProviderProvider.getIfAvailable();
        if (friendProvider != null) {
            builder.friendProvider(friendProvider);
        }

        List<ImEventListener> listeners = eventListenersProvider.getIfAvailable(Collections::emptyList);
        if (!listeners.isEmpty()) {
            builder.eventListeners(listeners);
        }

        // RTC Handler 后置注册钩子
        ImGroupMemberProvider rtcGroupProvider = groupMemberProviderProvider.getIfAvailable();
        GroupCallSessionManager groupCallManager = groupCallManagerProvider.getIfAvailable();
        TurnCredentialService turnCredentialService = turnCredentialServiceProvider.getIfAvailable();
        builder.postBuildHook(facade -> {
            List<BaseHandler> rtcHandlers = new ArrayList<>();
            // 单聊信令转发 + 通话建立信令注入 TURN 凭证（服务未配置 turn 时为 null，退化为纯转发）
            rtcHandlers.add(new RtcSignalHandler(facade, turnCredentialService));
            if (rtcGroupProvider != null) {
                GroupCallService groupCallService = null;
                if (groupCallManager != null) {
                    // 群通话生命周期信令处理 + 掉线清理（用户全部设备离线时回收房间成员）
                    groupCallService = new GroupCallService(facade, groupCallManager, rtcGroupProvider, turnCredentialService);
                    facade.registerCloseListener(groupCallManager::onDisconnect);
                }
                rtcHandlers.add(new RtcGroupHandler(facade, rtcGroupProvider, groupCallService));
            }
            return rtcHandlers;
        });

        return builder.buildWithServer();
    }

    @Bean
    @ConditionalOnMissingBean
    public IMServerFacade imServerFacade(GimBootstrap.StartContext startContext) {
        return startContext.getFacade();
    }

    @Bean
    @ConditionalOnMissingBean
    public NettyServer nettyServer(GimBootstrap.StartContext startContext) {
        return startContext.getNettyServer();
    }

    // ==================== Netty 生命周期适配 ====================

    /**
     * gim.enable=false 时不注册该生命周期 Bean，IM 服务器不会启动；
     * 其余组件 Bean 正常存在，业务代码仍可注入 IMServerFacade 发送消息等
     */
    @Bean
    @ConditionalOnProperty(prefix = "gim", name = "enable", havingValue = "true", matchIfMissing = true)
    public SmartLifecycle nettyServerLifecycle(GimBootstrap.StartContext startContext) {
        return new NettyServerLifecycleAdapter(startContext);
    }
}
