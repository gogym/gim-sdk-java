package io.getbit.gim.core.connection.health;

import io.getbit.gim.core.cache.CacheKeyBuilder;
import io.getbit.gim.core.connection.channel.ChannelManager;
import io.getbit.gim.core.connection.server.NettyServer;
import io.getbit.gim.core.message.ack.MessageAckTracker;
import io.getbit.gim.core.routing.UserRouteService;
import io.getbit.gim.core.spi.ImRedisAdapter;
import io.getbit.gim.core.spi.ImRedisSubscriber;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ImNodeHealthIndicator.java
 * <p>
 * IM节点健康指标
 * 检查 Netty 服务、Redis 连接、集群订阅状态，并提供运行时统计信息
 *
 * @author gogym
 */
public class ImNodeHealthIndicator {

    private final ChannelManager channelManager;
    private final UserRouteService userRouteService;
    private final ImRedisAdapter redisAdapter;
    private final ImRedisSubscriber redisSubscriber;
    private final boolean clusterEnabled;

    /**
     * NettyServer 延迟注入（在 GimBootstrap 中创建后设置）
     * -- SETTER --
     * 延迟注入 NettyServer（由 GimBootstrap 在创建 NettyServer 后调用）
     */
    @Setter
    private volatile NettyServer nettyServer;

    /**
     * MessageAckTracker 延迟注入（可选）
     * -- SETTER --
     * 延迟注入 MessageAckTracker（由 GimBootstrap 组装后调用）
     */
    @Setter
    private volatile MessageAckTracker messageAckTracker;

    public ImNodeHealthIndicator(ChannelManager channelManager,
                                 UserRouteService userRouteService,
                                 ImRedisAdapter redisAdapter,
                                 ImRedisSubscriber redisSubscriber,
                                 boolean clusterEnabled) {
        this.channelManager = channelManager;
        this.userRouteService = userRouteService;
        this.redisAdapter = redisAdapter;
        this.redisSubscriber = redisSubscriber;
        this.clusterEnabled = clusterEnabled;
    }

    /**
     * 获取完整健康指标数据
     */
    public Map<String, Object> getHealthDetails() {
        Map<String, Object> details = new LinkedHashMap<>();

        // Netty 服务状态
        boolean nettyOk = checkNetty();
        details.put("netty", nettyOk ? "UP" : "DOWN");

        // Redis 连接状态：仅集群模式依赖 Redis，单机模式标记 N/A 且不参与综合健康判定
        // （集群模式下 redisAdapter 必非空，已在 GimBootstrap 启动校验保证）
        boolean redisOk;
        if (!clusterEnabled) {
            details.put("redis", "N/A");
            redisOk = true;
        } else {
            redisOk = checkRedis();
            details.put("redis", redisOk ? "UP" : "DOWN");
        }

        // 集群订阅状态
        if (clusterEnabled) {
            boolean clusterOk = checkCluster();
            details.put("cluster", clusterOk ? "UP" : "DOWN");
        }

        // 运行时统计
        details.put("onlineUsers", channelManager.getOnlineUserCount());
        details.put("totalConnections", channelManager.getTotalConnectionCount());
        details.put("localRouteCacheSize", userRouteService.getLocalCacheSize());

        // 消息ACK监控
        if (messageAckTracker != null) {
            details.put("pendingAckCount", messageAckTracker.getPendingCount());
        }

        // 综合状态
        boolean allUp = nettyOk && redisOk && (!clusterEnabled || checkCluster());
        details.put("status", allUp ? "UP" : "DEGRADED");

        return details;
    }

    /**
     * 检查 Netty 服务是否正在运行
     */
    public boolean checkNetty() {
        return nettyServer != null && nettyServer.isRunning();
    }

    /**
     * 检查 Redis 连接是否正常
     * 通过一次读写探测验证连通性
     */
    public boolean checkRedis() {
        if (redisAdapter == null) {
            return false;
        }
        try {
            String probeKey = CacheKeyBuilder.healthProbe();
            redisAdapter.setex(probeKey, 10, "ok");
            String result = redisAdapter.get(probeKey);
            return "ok".equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查集群订阅是否正常
     */
    public boolean checkCluster() {
        return redisSubscriber != null && redisSubscriber.isSubscribed();
    }
}
