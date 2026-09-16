package io.getbit.gim.core.connection.channel;

import io.getbit.gim.protocol.codec.DeviceType;
import lombok.Value;

/**
 * ConnectionInfo.java
 *
 * 连接信息记录
 * 记录用户连接所在的节点、用户ID、通道、设备类型和连接时间
 * 不可变对象，字段一经创建不再修改
 *
 * @author gogym
 */
@Value
public class ConnectionInfo {

    /**
     * 所在 IM 节点 ID（集群模式用，单机可为 null）
     */
    String nodeId;

    /**
     * 用户 ID
     */
    String userId;

    /**
     * 设备类型
     */
    DeviceType device;

    /**
     * 设备唯一标识（客户端持久化 UUID，用于区分同设备重连与异设备顶号）
     */
    String deviceId;

    /**
     * 连接建立时间（毫秒时间戳）
     */
    long connectedAt;

    /**
     * 创建 ConnectionInfo（自动填充连接时间）
     */
    public static ConnectionInfo of(String nodeId, String userId, DeviceType device, String deviceId) {
        return new ConnectionInfo(nodeId, userId, device, deviceId, System.currentTimeMillis());
    }
}
