package io.getbit.gim.core.config.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * RtcCallProperties.java
 *
 * 1:1 通话服务端会话管理配置
 * 纯 POJO，由 starter 层绑定 gim.rtc-call.* 配置项
 *
 * @author gogym
 */
@Getter
@Setter
public class RtcCallProperties {

    /**
     * 是否启用 1:1 通话生命周期管理（忙线互斥 / 振铃超时 / 掉线清理）
     * 关闭后 RtcSingleHandler 退化为纯信令转发
     */
    private boolean enabled = true;

    /**
     * 振铃超时时间（秒）：CALLING 状态超过该时长无应答，服务端自动结束会话并通知主叫
     */
    private int ringTimeoutSeconds = 60;

    /**
     * 通话会话 TTL（秒）：Redis 会话元数据与 TALKING 态占用 key 的过期时间
     * 兼作异常情况（节点宕机等）下的会话自动回收兜底
     */
    private int sessionTtlSeconds = 7200;
}
