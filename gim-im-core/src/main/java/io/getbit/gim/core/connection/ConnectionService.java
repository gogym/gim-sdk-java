package io.getbit.gim.core.connection;

import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.connection.channel.ChannelManager;
import io.getbit.gim.core.routing.UserRouteService;
import io.getbit.gim.protocol.codec.DeviceType;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * ConnectionService.java
 * <p>
 * 连接管理服务
 * 提供服务端主动管理用户连接的能力，如踢人下线、查询在线状态等
 * <p>
 * 使用场景：
 * 1. 管理后台踢人下线（封禁、强制登出）
 * 2. 多端互踢策略
 * 3. 业务层自定义连接管理逻辑
 *
 * @author gogym
 */
@Slf4j
public class ConnectionService {

    private final ChannelManager channelManager;
    private final UserRouteService userRouteService;
    private final IMServerFacade facade;

    public ConnectionService(ChannelManager channelManager,
                             UserRouteService userRouteService,
                             IMServerFacade facade) {
        this.channelManager = channelManager;
        this.userRouteService = userRouteService;
        this.facade = facade;
    }

    /**
     * 踢指定用户所有设备下线
     * 先解绑通道映射，再关闭连接，最后触发下线事件
     *
     * @param userId 用户ID
     * @return 是否找到并关闭了连接
     */
    public boolean kickUser(String userId) {
        Map<DeviceType, Channel> deviceChannels = channelManager.getChannels(userId);
        if (deviceChannels.isEmpty()) {
            log.debug("踢人下线: 用户不在线, userId={}", userId);
            return false;
        }

        // 先关闭所有设备的连接
        int closedCount = 0;
        for (Map.Entry<DeviceType, Channel> entry : deviceChannels.entrySet()) {
            Channel channel = entry.getValue();
            if (channel != null && channel.isActive()) {
                // 先解绑通道映射
                channelManager.unbind(userId, entry.getKey());
                // 再关闭连接
                channel.close();
                closedCount++;
            }
        }

        // 所有设备已解绑，注销路由并触发下线事件
        if (closedCount > 0) {
            userRouteService.unregister(userId);
            facade.fireUserOffline(userId);
            log.info("踢人下线: userId={}, 关闭设备数={}/{}", userId, closedCount, deviceChannels.size());
        }
        return closedCount > 0;
    }

    /**
     * 踢指定用户指定设备下线
     *
     * @param userId 用户ID
     * @param device 设备类型
     * @return 是否找到并关闭了连接
     */
    public boolean kickUser(String userId, DeviceType device) {
        Map<DeviceType, Channel> deviceChannels = channelManager.getChannels(userId);
        Channel channel = deviceChannels.get(device);

        if (channel == null || !channel.isActive()) {
            log.debug("踢人下线: 设备不在线, userId={}, device={}", userId, device);
            return false;
        }

        // 先解绑指定设备的通道映射
        channelManager.unbind(userId, device);
        // 再关闭连接
        channel.close();

        // 检查用户是否还有其他在线设备
        var remainingChannels = channelManager.getChannels(userId);
        if (remainingChannels.isEmpty()) {
            userRouteService.unregister(userId);
            facade.fireUserOffline(userId);
        }

        log.info("踢人下线: userId={}, device={}", userId, device);
        return true;
    }

    /**
     * 判断用户是否在线（任意设备）
     *
     * @param userId 用户ID
     * @return 是否在线
     */
    public boolean isOnline(String userId) {
        // 先查本地
        if (!channelManager.getChannels(userId).isEmpty()) {
            return true;
        }
        // 再查路由（可能在其他节点）
        return userRouteService.getServerId(userId) != null;
    }

    /**
     * 获取用户当前在线设备数
     *
     * @param userId 用户ID
     * @return 在线设备数
     */
    public int getOnlineDeviceCount(String userId) {
        return channelManager.getChannels(userId).size();
    }
}
