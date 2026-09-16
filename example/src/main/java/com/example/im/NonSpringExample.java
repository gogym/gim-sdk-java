package com.example.im;

import io.getbit.gim.core.bootstrap.GimBootstrap;
import io.getbit.gim.core.bootstrap.StartContext;
import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.spi.*;
import io.getbit.gim.protocol.codec.DeviceType;
import io.getbit.gim.protocol.codec.ImProto;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 非 Spring 环境使用示例
 * <p>
 * ═══════════════════════════════════════════════════════
 * 适用场景：
 * ═══════════════════════════════════════════════════════
 * <ul>
 *   <li>纯 Java 应用（无 Spring 框架）</li>
 *   <li>其他框架（Quarkus / Micronaut / Vert.x 等）</li>
 *   <li>单元测试 / 嵌入式使用</li>
 *   <li>需要完全控制组件生命周期的场景</li>
 * </ul>
 * <p>
 * ═══════════════════════════════════════════════════════
 * 核心依赖（仅需引入 core 模块，零 Spring 依赖）：
 * ═══════════════════════════════════════════════════════
 * <pre>{@code
 * <dependency>
 *     <groupId>io.getbit</groupId>
 *     <artifactId>gim-im-core</artifactId>
 *     <version>${gim.version}</version>
 * </dependency>
 * }</pre>
 * <p>
 * ═══════════════════════════════════════════════════════
 * 注意：此文件仅作为代码示例参考，不会在项目中运行。
 * 实际运行请使用 {@link ExampleApplication}（Spring Boot 方式）。
 * 集群模式请参考 {@link ClusterExample}。
 * ═══════════════════════════════════════════════════════
 *
 * @author gogym
 */
@Slf4j
public class NonSpringExample {


    /**
     * 方式 1：使用 StartContext（推荐）
     * 自动管理 NettyServer + ClusterMessageRouter 的启停
     */
    public static void startWithServerContext() {
        // 1. 构建配置
        GimProperties config = GimProperties.builder()
                .nettyPort(3333)
                .nettyWorkerThreads(0)          // 0 = 自动检测 CPU 核心数
                .enableHeartBeat(true)
                .heartBeatInterval(30)
                .enableCluster(false)
                .build();

        // 2. 通过 Builder 组装所有组件并启动
        StartContext ctx = GimBootstrap.builder()
                .config(config)
                .tokenVerifier(new MyTokenVerifier())
                .redisAdapter(new MyRedisAdapter())
                .idGenerator(new MyIdGenerator())
                .redisSubscriber(new MyRedisSubscriber())     // 集群模式必须
                .addEventListener(new MyEventListener())
                .buildWithServer();

        // 3. 启动（集群路由订阅 + Netty 监听）
        ctx.start();

        // 4. 应用关闭时停止
        Runtime.getRuntime().addShutdownHook(new Thread(ctx::stop));

        log.info("IM Server started, port={}", config.getNetty().getPort());
    }

    /**
     * 方式 2：只获取 IMServerFacade（自行管理 NettyServer）
     * 适用于需要更细粒度控制的场景
     */
    public static void startWithFacadeOnly() {
        GimProperties config = GimProperties.builder()
                .nettyPort(3333)
                .build();

        // 只构建门面，不创建 Server
        IMServerFacade facade = GimBootstrap.builder()
                .config(config)
                .tokenVerifier(new MyTokenVerifier())
                .redisAdapter(new MyRedisAdapter())
                .idGenerator(new MyIdGenerator())
                .build();

        // 自行获取 Facade 中的组件
        log.info("在线用户数: {}", facade.getChannelManager().getOnlineUserCount());
    }

    // ==================== SPI 示例实现 ====================

    /**
     * Token 验证器示例
     */
    static class MyTokenVerifier implements ImTokenVerifier {
        @Override
        public String verifyAndExtractUserId(String token) {
            // TODO: 替换为 JWT 解析或其他安全方案
            if (token == null || token.isEmpty()) {
                return null;
            }
            try {
                Long.parseLong(token);
                return token;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * Redis 适配器示例（需替换为真实 Redis 客户端）
     */
    static class MyRedisAdapter implements ImRedisAdapter {
        @Override
        public void setex(String key, int seconds, String value) {
            // TODO: 对接真实 Redis
        }

        @Override
        public String get(String key) {
            return null; // TODO
        }

        @Override
        public void del(String key) {
            // TODO
        }

        @Override
        public void publish(String channel, String message) {
            // TODO
        }
    }

    /**
     * ID 生成器示例
     */
    static class MyIdGenerator implements ImIdGenerator {
        private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

        @Override
        public String generateMsgId() {
            // TODO: 生产环境请使用雪花算法
            return String.valueOf(seq.incrementAndGet());
        }
    }

    /**
     * Redis 订阅器示例（集群模式必须实现）
     */
    static class MyRedisSubscriber implements ImRedisSubscriber {
        @Override
        public void subscribe(String channel, java.util.function.Consumer<String> callback) {
            // TODO: 对接真实 Redis Pub/Sub
        }

        @Override
        public void unsubscribe() {
        }

        @Override
        public boolean isSubscribed() {
            return false;
        }
    }

    /**
     * 事件监听器示例（可选）
     */
    static class MyEventListener implements ImEventListener {
        @Override
        public void onUserOnline(String userId, DeviceType device, String serverId) {
            log.info("用户上线: userId={}, device={}", userId, device);
        }

        @Override
        public void onUserOffline(String userId) {
            log.info("用户下线: userId={}", userId);
        }

        @Override
        public void onOfflineMessage(ImProto.Packet packet, String receiverId, String reason) {
            log.info("离线消息: receiverId={}, cmd={}", receiverId, packet.getCmd());
            // TODO: 触发 APNs / FCM 推送
        }
    }
}
