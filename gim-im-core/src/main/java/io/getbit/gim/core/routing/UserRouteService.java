package io.getbit.gim.core.routing;

import io.getbit.gim.core.cache.CacheKeyBuilder;
import io.getbit.gim.core.config.properties.CacheProperties;
import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.spi.ImRedisAdapter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;

/**
 * UserRouteService.java
 *
 * 用户路由服务
 * 维护 userId → serverId 的映射关系
 * 本地缓存 + Redis 二级缓存
 *
 * Redis Key 结构：
 * - gim_route:{userId} → serverId（TTL 5分钟，心跳续期）
 * - Key 统一由 {@link io.getbit.gim.core.cache.CacheKeyBuilder} 管理
 *
 * @author gogym
 */
@Slf4j
public class UserRouteService {


    // key 统一由 CacheKeyBuilder 管理

    /**
     * 路由过期时间（秒）：5 分钟，通过心跳续期
     */
    private static final int ROUTE_EXPIRE_SECONDS = 5 * 60;

    /**
     * 本地缓存：userId → serverId
     */
    private final Cache<String, String> localCache;

    private final GimProperties config;
    private final ImRedisAdapter redisAdapter;

    public UserRouteService(GimProperties config, ImRedisAdapter redisAdapter) {
        this(config, redisAdapter, null);
    }

    public UserRouteService(GimProperties config, ImRedisAdapter redisAdapter, CacheProperties cacheProperties) {
        this.config = config;
        this.redisAdapter = redisAdapter;
        int maxSize = cacheProperties != null ? cacheProperties.getMaxSize() : 100_000;
        int expireSeconds = cacheProperties != null ? cacheProperties.getExpireSeconds() : 300;
        this.localCache = Caffeine.newBuilder()
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize)
                .build();
    }

    // ====================== 路由管理 ======================

    /**
     * 注册用户路由（绑定连接时调用）
     * 单机模式（enable-cluster=false）仅维护本地缓存，集群模式额外写入 Redis 供跨节点查询
     */
    public void register(String userId) {
        String serverId = config.getServerId();
        localCache.put(userId, serverId);
        if (config.isEnableCluster()) {
            redisAdapter.setex(CacheKeyBuilder.userRoute(userId), ROUTE_EXPIRE_SECONDS, serverId);
        }

        log.debug("注册用户路由: userId={}, serverId={}", userId, serverId);
    }

    /**
     * 续期用户路由（心跳时调用）
     * 同步刷新本地缓存，避免单机模式下路由项因写后过期而丢失
     */
    public void renew(String userId) {
        String serverId = config.getServerId();
        localCache.put(userId, serverId);
        if (config.isEnableCluster()) {
            redisAdapter.setex(CacheKeyBuilder.userRoute(userId), ROUTE_EXPIRE_SECONDS, serverId);
        }
    }

    /**
     * 注销用户路由（断开连接时调用）
     */
    public void unregister(String userId) {
        if (config.isEnableCluster()) {
            redisAdapter.del(CacheKeyBuilder.userRoute(userId));
        }
        localCache.invalidate(userId);

        log.debug("注销用户路由: userId={}", userId);
    }

    /**
     * 查询用户所在节点
     */
    public String getServerId(String userId) {
        // 1. 本地缓存
        String cached = localCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }

        // 2. Redis（单机模式路由仅依赖本地缓存，不查 Redis）
        if (!config.isEnableCluster()) {
            return null;
        }
        String key = CacheKeyBuilder.userRoute(userId);
        String serverId = redisAdapter.get(key);

        if (serverId != null) {
            localCache.put(userId, serverId);
        }

        return serverId;
    }

    /**
     * 判断用户是否在当前节点
     */
    public boolean isLocal(String userId) {
        String serverId = getServerId(userId);
        return config.getServerId().equals(serverId);
    }

    /**
     * 判断用户是否在远程节点
     */
    public boolean isRemote(String userId) {
        String serverId = getServerId(userId);
        return serverId != null && !config.getServerId().equals(serverId);
    }

    /**
     * 获取本地缓存大小
     */
    public long getLocalCacheSize() {
        return localCache.estimatedSize();
    }
}
