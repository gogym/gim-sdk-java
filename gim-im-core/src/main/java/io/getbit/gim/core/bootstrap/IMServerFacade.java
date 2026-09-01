package io.getbit.gim.core.bootstrap;

import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.connection.ConnectionService;
import io.getbit.gim.core.connection.channel.ChannelManager;
import io.getbit.gim.core.connection.auth.ConnectionAuthHandler;
import io.getbit.gim.core.connection.health.ImNodeHealthIndicator;
import io.getbit.gim.core.notify.friend.FriendNotifyService;
import io.getbit.gim.core.notify.group.GroupNotifyService;
import io.getbit.gim.core.message.handler.BaseHandler;
import io.getbit.gim.core.message.handler.DefaultMessageDispatcher;
import io.getbit.gim.core.message.handler.MessageDispatcher;
import io.getbit.gim.core.routing.ClusterMessageRouter;
import io.getbit.gim.core.routing.UserRouteService;
import io.getbit.gim.core.spi.ConnectionCloseListener;
import io.getbit.gim.core.spi.ImEventListener;
import io.getbit.gim.core.spi.ImRedisAdapter;
import io.getbit.gim.core.spi.ImRedisSubscriber;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * IMServerFacade.java
 * <p>
 * IM 服务器统一门面
 * 提供核心组件的统一获取入口
 *
 * @author gogym
 */
public class IMServerFacade {

    private static final Logger logger = LoggerFactory.getLogger(IMServerFacade.class);

    @Getter
    private final GimProperties config;
    @Getter
    private final ChannelManager channelManager;
    @Getter
    private final MessageDispatcher messageDispatcher;
    @Getter
    private final ConnectionAuthHandler authHandler;
    @Getter
    private final UserRouteService userRouteService;
    @Getter
    private final List<ImEventListener> eventListeners;

    /**
     * 好友通知推送服务（可选，未配置 ImFriendProvider 时为 null）
     */
    @Getter
    private final FriendNotifyService friendNotifyService;

    /**
     * 群组通知推送服务（可选，未配置 ImGroupMemberProvider 时为 null）
     */
    @Getter
    private final GroupNotifyService groupNotifyService;

    /**
     * 节点健康指标
     */
    @Getter
    private final ImNodeHealthIndicator healthIndicator;

    /**
     * 连接管理服务
     */
    @Getter
    private final ConnectionService connectionService;

    /**
     * 集群消息路由
     */
    @Getter
    private final ClusterMessageRouter clusterRouter;

    /**
     * 连接关闭监听器（用户全部设备离线时触发，用于模块级资源清理，如群通话掉线清理）
     */
    private final List<ConnectionCloseListener> closeListeners = new CopyOnWriteArrayList<>();

    private IMServerFacade(Builder builder) {
        this.config = builder.config;
        this.channelManager = builder.channelManager;
        this.authHandler = builder.authHandler;
        this.userRouteService = builder.userRouteService;
        this.eventListeners = builder.eventListeners;
        this.friendNotifyService = builder.friendNotifyService;
        this.groupNotifyService = builder.groupNotifyService;
        this.healthIndicator = new ImNodeHealthIndicator(
                channelManager, userRouteService, builder.redisAdapter,
                builder.redisSubscriber, config.isEnableCluster());
        this.connectionService = new ConnectionService(channelManager, userRouteService, this);
        this.clusterRouter = builder.clusterRouter;

        // 通过工厂创建 Handlers 和 Dispatcher（解决循环依赖）
        if (builder.handlerFactory != null) {
            List<BaseHandler> handlers = builder.handlerFactory.apply(this);
            this.messageDispatcher = new DefaultMessageDispatcher(handlers);
        } else {
            this.messageDispatcher = new DefaultMessageDispatcher(Collections.emptyList());
        }

        // 外部模块注册额外 Handler（如 RTC 信令处理器）
        if (builder.postBuildHook != null) {
            List<BaseHandler> extraHandlers = builder.postBuildHook.apply(this);
            if (extraHandlers != null) {
                for (BaseHandler h : extraHandlers) {
                    ((DefaultMessageDispatcher) messageDispatcher).registerHandler(h);
                }
            }
        }

        logger.info("IMServerFacade 初始化完成, serverId={}, cluster={}",
                config.getServerId(), config.isEnableCluster());
    }

    /**
     * IMServerFacade 构建器
     */
    public static class Builder {
        private GimProperties config;
        private ChannelManager channelManager;
        private ConnectionAuthHandler authHandler;
        private UserRouteService userRouteService;
        private List<ImEventListener> eventListeners = Collections.emptyList();
        private FriendNotifyService friendNotifyService;
        private GroupNotifyService groupNotifyService;
        private ImRedisAdapter redisAdapter;
        private ImRedisSubscriber redisSubscriber;
        private ClusterMessageRouter clusterRouter;
        private Function<IMServerFacade, List<BaseHandler>> handlerFactory;
        private Function<IMServerFacade, List<BaseHandler>> postBuildHook;

        public Builder config(GimProperties config) {
            this.config = config;
            return this;
        }

        public Builder channelManager(ChannelManager channelManager) {
            this.channelManager = channelManager;
            return this;
        }

        public Builder authHandler(ConnectionAuthHandler authHandler) {
            this.authHandler = authHandler;
            return this;
        }

        public Builder userRouteService(UserRouteService userRouteService) {
            this.userRouteService = userRouteService;
            return this;
        }

        public Builder eventListeners(List<ImEventListener> eventListeners) {
            this.eventListeners = eventListeners != null ? eventListeners : Collections.emptyList();
            return this;
        }

        public Builder friendNotifyService(FriendNotifyService friendNotifyService) {
            this.friendNotifyService = friendNotifyService;
            return this;
        }

        public Builder groupNotifyService(GroupNotifyService groupNotifyService) {
            this.groupNotifyService = groupNotifyService;
            return this;
        }

        public Builder redisAdapter(ImRedisAdapter redisAdapter) {
            this.redisAdapter = redisAdapter;
            return this;
        }

        public Builder redisSubscriber(ImRedisSubscriber redisSubscriber) {
            this.redisSubscriber = redisSubscriber;
            return this;
        }

        public Builder clusterRouter(ClusterMessageRouter clusterRouter) {
            this.clusterRouter = clusterRouter;
            return this;
        }

        /**
         * 设置消息处理器工厂
         * 工厂接收 IMServerFacade 参数，返回 Handler 列表
         * 用于解决 Handler → Facade → Dispatcher 的循环依赖
         */
        public Builder handlerFactory(Function<IMServerFacade, List<BaseHandler>> handlerFactory) {
            this.handlerFactory = handlerFactory;
            return this;
        }

        /**
         * 设置后置 Handler 注册钩子
         * 用于外部模块（如 WebRTC）在 Dispatcher 创建后注册额外 Handler
         */
        public Builder postBuildHook(Function<IMServerFacade, List<BaseHandler>> postBuildHook) {
            this.postBuildHook = postBuildHook;
            return this;
        }

        public IMServerFacade build() {
            return new IMServerFacade(this);
        }
    }

    /**
     * 触发用户上线事件
     */
    public void fireUserOnline(String userId, io.getbit.gim.protocol.codec.DeviceType device) {
        if (eventListeners != null) {
            for (ImEventListener listener : eventListeners) {
                try {
                    listener.onUserOnline(userId, device, config.getServerId());
                } catch (Exception e) {
                    logger.error("事件监听器回调异常: onUserOnline", e);
                }
            }
        }

        // 绑定成功后，同步好友在线状态 + 通知好友上线
        if (friendNotifyService != null) {
            friendNotifyService.syncFriendsOnlineStatus(userId);
            friendNotifyService.notifyUserOnline(userId);
        }
    }

    /**
     * 注册连接关闭监听器
     * 在用户全部设备离线时触发（与 fireUserOffline 同一时机）
     */
    public void registerCloseListener(ConnectionCloseListener listener) {
        if (listener != null) {
            closeListeners.add(listener);
        }
    }

    /**
     * 触发用户下线事件
     */
    public void fireUserOffline(String userId) {
        if (eventListeners != null) {
            for (ImEventListener listener : eventListeners) {
                try {
                    listener.onUserOffline(userId);
                } catch (Exception e) {
                    logger.error("事件监听器回调异常: onUserOffline", e);
                }
            }
        }

        // 通知好友下线
        if (friendNotifyService != null) {
            friendNotifyService.notifyUserOffline(userId);
        }

        // 通知连接关闭监听器（模块级资源清理）
        for (ConnectionCloseListener listener : closeListeners) {
            try {
                listener.onConnectionClosed(userId);
            } catch (Exception e) {
                logger.error("连接关闭监听器回调异常: onConnectionClosed, userId={}", userId, e);
            }
        }
    }
}
