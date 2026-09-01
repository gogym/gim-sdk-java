package io.getbit.gim.core.config.properties;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * GimProperties.java
 * <p>
 * GIM SDK 统一配置属性类
 * 纯 POJO，不依赖任何框架
 *
 * @author gogym
 */
@Getter
@Setter
public class GimProperties {

    // ==================== 嵌套配置类 ====================

    /**
     * Netty服务器配置
     */
    private NettyProperties netty = new NettyProperties();

    /**
     * 本地缓存配置
     */
    private CacheProperties cache = new CacheProperties();

    /**
     * 消息发送配置
     */
    private MessageProperties msg = new MessageProperties();

    /**
     * 群视频通话配置（混合架构：Mesh + SFU）
     */
    private RtcGroupCallProperties rtcGroupCall = new RtcGroupCallProperties();

    // ==================== 心跳配置 ====================

    /**
     * 是否开启心跳
     */
    private boolean enableHeartBeat = true;

    /**
     * 心跳间隔（秒）
     */
    private Integer heartBeatInterval = 30;

    // ==================== 集群配置 ====================

    /**
     * 服务器ID（集群环境标识）
     * 如果未配置，将自动生成一个随机ID
     */
    private String serverId;

    /**
     * 获取服务器ID
     * 如果未配置则自动生成随机ID
     */
    public String getServerId() {
        if (serverId == null || serverId.isEmpty()) {
            serverId = "server-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return serverId;
    }

    /**
     * 是否开启集群模式
     */
    private boolean enableCluster = false;

    // ==================== 自动重发配置 ====================

    /**
     * 是否开启自动重发
     */
    private boolean autoRewrite = false;

    /**
     * 重发次数
     */
    private Integer reWriteNum = 3;

    /**
     * 重发间隔（毫秒）
     */
    private Long reWriteDelay = 1000L;

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final GimProperties props = new GimProperties();

        public Builder nettyPort(int port) {
            props.getNetty().setPort(port);
            return this;
        }

        public Builder nettyBossThreads(int threads) {
            props.getNetty().setBossThreads(threads);
            return this;
        }

        public Builder nettyWorkerThreads(int threads) {
            props.getNetty().setWorkerThreads(threads);
            return this;
        }

        public Builder nettyBacklog(int backlog) {
            props.getNetty().setBacklog(backlog);
            return this;
        }

        public Builder cacheMaxSize(int maxSize) {
            props.getCache().setMaxSize(maxSize);
            return this;
        }

        public Builder cacheExpireSeconds(int seconds) {
            props.getCache().setExpireSeconds(seconds);
            return this;
        }

        public Builder enableHeartBeat(boolean enable) {
            props.setEnableHeartBeat(enable);
            return this;
        }

        public Builder heartBeatInterval(int seconds) {
            props.setHeartBeatInterval(seconds);
            return this;
        }

        public Builder serverId(String serverId) {
            props.setServerId(serverId);
            return this;
        }

        public Builder enableCluster(boolean enable) {
            props.setEnableCluster(enable);
            return this;
        }

        public Builder autoRewrite(boolean enable) {
            props.setAutoRewrite(enable);
            return this;
        }

        public Builder reWriteNum(int num) {
            props.setReWriteNum(num);
            return this;
        }

        public Builder reWriteDelay(long delay) {
            props.setReWriteDelay(delay);
            return this;
        }

        public Builder ackTimeoutSeconds(int seconds) {
            props.getMsg().setAckTimeoutSeconds(seconds);
            return this;
        }

        /**
         * 自定义配置（用于 Builder 未覆盖的场景）
         */
        public Builder customize(java.util.function.Consumer<GimProperties> customizer) {
            customizer.accept(props);
            return this;
        }

        public GimProperties build() {
            return props;
        }
    }
}
