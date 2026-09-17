package com.example.im.spi;

import io.getbit.gim.core.spi.ImRedisAdapter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 适配器实现 — 基于 Spring Data Redis（Lettuce）
 * <p>
 * SDK 通过此接口操作 Redis，用于：
 * <ul>
 *   <li>用户路由缓存（userId → serverId）</li>
 *   <li>连接信息存储</li>
 *   <li>集群消息发布</li>
 *   <li>1:1 通话会话元数据与忙线占位（集群可见）</li>
 * </ul>
 * <p>
 * 你可以替换为 Jedis / Redisson 等任意 Redis 客户端实现。
 *
 * @author gogym
 */
@Component
public class RedisAdapterImpl implements ImRedisAdapter {

    private final StringRedisTemplate redisTemplate;

    public RedisAdapterImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void setex(String key, int seconds, String value) {
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public boolean setnx(String key, String value, int seconds) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, value, seconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void del(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }
}
