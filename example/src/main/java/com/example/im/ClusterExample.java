package com.example.im;

import io.getbit.gim.core.bootstrap.GimBootstrap;
import io.getbit.gim.core.bootstrap.StartContext;
import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.spi.*;
import io.getbit.gim.protocol.codec.DeviceType;
import io.getbit.gim.protocol.codec.ImProto;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 集群模式完整示例
 * <p>
 * ═══════════════════════════════════════════════════════
 * 集群架构说明：
 * ═══════════════════════════════════════════════════════
 * <pre>
 *                        ┌─────────────────┐
 *                        │     Redis       │
 *                        │  Pub/Sub + 路由  │
 *                        └───┬─────────┬───┘
 *                            │         │
 *               ┌────────────┘         └────────────┐
 *               ▼                                   ▼
 *   ┌───────────────────┐             ┌───────────────────┐
 *   │   Node server-01  │             │   Node server-02  │
 *   │   Netty: 3333     │             │   Netty: 3334     │
 *   │   HTTP: 8081      │             │   HTTP: 8082      │
 *   │                   │             │                   │
 *   │  用户 A, B, C     │             │  用户 D, E, F     │
 *   └───────────────────┘             └───────────────────┘
 * </pre>
 * <p>
 * ═══════════════════════════════════════════════════════
 * 集群工作原理：
 * ═══════════════════════════════════════════════════════
 * <ol>
 *   <li>每个节点启动时订阅自己的 Redis channel: {@code gim_node:{serverId}}</li>
 *   <li>用户连接时，在 Redis 中注册路由: {@code gim_route:{userId} → serverId}（TTL 5min，心跳续期）</li>
 *   <li>发送消息时，先查路由判断目标用户在哪个节点：
 *       <ul>
 *         <li>本地节点 → 直接通过 Channel 投递</li>
 *         <li>远程节点 → PUBLISH 到目标节点的 Redis channel，由目标节点本地投递</li>
 *       </ul>
 *   </li>
 * </ol>
 * <p>
 * ═══════════════════════════════════════════════════════
 * 集群模式必须实现的 SPI：
 * ═══════════════════════════════════════════════════════
 * <ul>
 *   <li>{@code ImRedisAdapter}      — Redis 操作（路由缓存 + 消息发布）</li>
 *   <li>{@code ImRedisSubscriber}   — Redis Pub/Sub 订阅（跨节点消息接收）</li>
 * </ul>
 * <p>
 * ═══════════════════════════════════════════════════════
 * 注意：此文件仅作为代码示例参考，不会在项目中运行。
 * ═══════════════════════════════════════════════════════
 *
 * @author gogym
 */
@Slf4j
public class ClusterExample {


    // ==================== 方式 1：非 Spring 环境启动集群节点 ====================

    /**
     * 启动一个集群节点（非 Spring 环境）
     * <p>
     * 实际部署时，每个 JVM 进程调用此方法，传入不同的 serverId 和端口。
     *
     * @param serverId 节点唯一标识（如 "server-01"）
     * @param nettyPort Netty 监听端口
     */
    public static StartContext startClusterNode(String serverId, int nettyPort) {
        // 1. 集群配置
        GimProperties config = GimProperties.builder()
                .serverId(serverId)             // 节点唯一 ID
                .nettyPort(nettyPort)           // 每个节点不同端口
                .enableCluster(true)            // 开启集群模式
                .enableHeartBeat(true)
                .heartBeatInterval(30)          // 心跳间隔（秒），同时用于路由续期
                .build();

        // 2. 构建并启动
        StartContext ctx = GimBootstrap.builder()
                .config(config)
                .tokenVerifier(new ClusterTokenVerifier())
                .redisAdapter(new ClusterRedisAdapter())       // 必须：路由缓存 + 消息发布
                .redisSubscriber(new ClusterRedisSubscriber()) // 必须：跨节点消息接收
                .idGenerator(new ClusterIdGenerator())
                .addEventListener(new ClusterEventListener())
                .buildWithServer();

        // 3. 启动（内部会先订阅 Redis channel，再启动 Netty 监听）
        ctx.start();

        log.info("集群节点已启动: serverId={}, nettyPort={}", serverId, nettyPort);
        return ctx;
    }

    /**
     * 演示：在同一 JVM 中启动两个集群节点（仅用于测试）
     * <p>
     * 生产环境中每个节点运行在独立的 JVM/服务器上。
     */
    public static void startLocalCluster() throws InterruptedException {
        // 启动 Node-1
        StartContext node1 = startClusterNode("server-01", 3333);

        // 启动 Node-2
        StartContext node2 = startClusterNode("server-02", 3334);

        // 优雅关闭
        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭集群节点...");
            node1.stop();
            node2.stop();
            latch.countDown();
        }));

        log.info("本地集群已启动: server-01:3333, server-02:3334");
        latch.await();
    }

    // ==================== 方式 2：Spring Boot 环境集群配置 ====================

    /**
     * Spring Boot 环境集群配置说明：
     * <p>
     * 每个节点只需修改 application.yml 中的 server-id 和 netty.port：
     * <pre>
     * # Node-1 的 application.yml
     * gim:
     *   server-id: server-01
     *   netty:
     *     port: 3333
     *   enable-cluster: true
     *
     * # Node-2 的 application.yml
     * gim:
     *   server-id: server-02
     *   netty:
     *     port: 3334
     *   enable-cluster: true
     * </pre>
     * <p>
     * 或通过启动参数覆盖：
     * <pre>
     * java -jar app.jar --gim.server-id=server-01 --gim.netty.port=3333 --gim.enable-cluster=true
     * java -jar app.jar --gim.server-id=server-02 --gim.netty.port=3334 --gim.enable-cluster=true
     * </pre>
     * <p>
     * SPI 实现（RedisAdapterImpl / RedisSubscriberImpl）保持不变，
     * SDK 会自动根据 enable-cluster 配置决定是否启用集群路由。
     */

    // ==================== 集群 SPI 实现示例 ====================

    /**
     * 集群模式下的 Token 验证器
     */
    static class ClusterTokenVerifier implements ImTokenVerifier {
        @Override
        public String verifyAndExtractUserId(String token) {
            // TODO: 生产环境使用 JWT / OAuth2 等
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
     * 集群模式下的 Redis 适配器（必须实现）
     * <p>
     * 用途：
     * <ul>
     *   <li>用户路由缓存：{@code gim_route:{userId} → serverId}（TTL 5min）</li>
     *   <li>集群消息发布：PUBLISH 到 {@code gim_node:{serverId}}</li>
     * </ul>
     */
    static class ClusterRedisAdapter implements ImRedisAdapter {
        // TODO: 替换为真实 Redis 客户端（Jedis / Lettuce / Redisson）
        // private final Jedis jedis;

        @Override
        public void setex(String key, int seconds, String value) {
            // 设置用户路由映射，带 TTL
            // jedis.setex(key, seconds, value);
        }

        @Override
        public String get(String key) {
            // 查询用户所在节点
            // return jedis.get(key);
            return null;
        }

        @Override
        public void del(String key) {
            // 用户下线时删除路由
            // jedis.del(key);
        }

        @Override
        public void publish(String channel, String message) {
            // 跨节点消息转发：PUBLISH 到目标节点的 channel
            // jedis.publish(channel, message);
        }
    }

    /**
     * 集群模式下的 Redis 订阅器（必须实现）
     * <p>
     * 每个节点启动时订阅自己的 channel: {@code gim_node:{serverId}}
     * 收到消息后在本地投递给目标用户。
     */
    static class ClusterRedisSubscriber implements ImRedisSubscriber {
        // TODO: 替换为真实 Redis Pub/Sub 实现
        private volatile boolean subscribed = false;

        @Override
        public void subscribe(String channel, java.util.function.Consumer<String> callback) {
            // 订阅本节点的 channel，收到消息后回调 ClusterMessageRouter.onClusterMessage()
            // jedis.subscribe(callback, channel);
            subscribed = true;
            log.info("已订阅集群频道: {}", channel);
        }

        @Override
        public void unsubscribe() {
            subscribed = false;
            log.info("已取消集群频道订阅");
        }

        @Override
        public boolean isSubscribed() {
            return subscribed;
        }
    }

    /**
     * 消息 ID 生成器（集群环境建议使用雪花算法，保证全局唯一）
     */
    static class ClusterIdGenerator implements ImIdGenerator {
        private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

        @Override
        public String generateMsgId() {
            // TODO: 生产环境请使用雪花算法（Snowflake）
            // 集群中每个节点生成不冲突的 ID
            return String.valueOf(seq.incrementAndGet());
        }
    }

    /**
     * 集群事件监听器
     */
    static class ClusterEventListener implements ImEventListener {
        @Override
        public void onUserOnline(String userId, DeviceType device, String serverId) {
            log.info("[集群] 用户上线: userId={}, device={}, node={}", userId, device, serverId);
        }

        @Override
        public void onUserOffline(String userId) {
            log.info("[集群] 用户下线: userId={}", userId);
        }

        @Override
        public void onOfflineMessage(ImProto.Packet packet, String receiverId, String reason) {
            log.info("[集群] 离线消息: receiverId={}, cmd={}, reason={}",
                    receiverId, packet.getCmd(), reason);
            // TODO: 触发 APNs / FCM 推送通知
        }

        @Override
        public void onMessageDeliveryFailed(ImProto.Packet packet, String receiverId, String reason) {
            log.info("[集群] 消息投递失败: receiverId={}, cmd={}, reason={}", receiverId, packet.getCmd(), reason);
        }
    }
}
