package io.getbit.gim.starter;

import io.getbit.gim.core.config.properties.CacheProperties;
import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.config.properties.MessageProperties;
import io.getbit.gim.core.config.properties.NettyProperties;
import io.getbit.gim.core.config.properties.RtcGroupCallProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GimSpringProperties.java
 *
 * Spring Boot 配置属性绑定
 * 将 application.yml 中 "gim" 前缀的配置映射到 core 的 GimProperties
 *
 * @author gogym
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gim")
public class GimSpringProperties {

    /** 总开关：是否启动 IM 服务器（false 时不监听端口、不接入集群，组件 Bean 仍注册） */
    private boolean enable = true;

    /** Netty服务器配置 */
    private NettyProperties netty = new NettyProperties();

    /** 本地缓存配置 */
    private CacheProperties cache = new CacheProperties();

    /** 消息发送配置 */
    private MessageProperties msg = new MessageProperties();

    /** 群视频通话配置（混合架构：Mesh + SFU） */
    private RtcGroupCallProperties rtcGroupCall = new RtcGroupCallProperties();

    /** 是否开启心跳 */
    private boolean enableHeartBeat = true;

    /** 心跳间隔（秒） */
    private Integer heartBeatInterval = 30;

    /** 服务器ID（集群环境标识） */
    private String serverId;

    /** 是否开启集群模式 */
    private boolean enableCluster = false;

    /** 是否开启自动重发 */
    private boolean autoRewrite = false;

    /** 重发次数 */
    private Integer reWriteNum = 3;

    /** 重发间隔（毫秒） */
    private Long reWriteDelay = 1000L;

    /**
     * 转换为 core 模块的 GimProperties
     */
    public GimProperties toCoreProperties() {
        GimProperties props = new GimProperties();
        props.setNetty(this.netty);
        props.setCache(this.cache);
        props.setMsg(this.msg);
        props.setRtcGroupCall(this.rtcGroupCall);
        props.setEnableHeartBeat(this.enableHeartBeat);
        props.setHeartBeatInterval(this.heartBeatInterval);
        props.setServerId(this.serverId);
        props.setEnableCluster(this.enableCluster);
        props.setAutoRewrite(this.autoRewrite);
        props.setReWriteNum(this.reWriteNum);
        props.setReWriteDelay(this.reWriteDelay);
        return props;
    }
}
