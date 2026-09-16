package io.getbit.gim.core.connection.channel;

import io.netty.channel.Channel;
import lombok.Getter;

/**
 * BindResult.java
 *
 * 通道绑定结果：旧连接及其连接信息（无旧连接时 ChannelManager.bind 返回 null）
 *
 * @author gogym
 */
@Getter
public class BindResult {

    /**
     * 被替换的旧连接（同设备互踢）
     */
    private final Channel oldChannel;

    /**
     * 旧连接的 ConnectionInfo（含 deviceId，供互踢判定）
     */
    private final ConnectionInfo oldInfo;

    public BindResult(Channel oldChannel, ConnectionInfo oldInfo) {
        this.oldChannel = oldChannel;
        this.oldInfo = oldInfo;
    }

}
