package io.getbit.gim.core.bootstrap;

import io.getbit.gim.core.connection.server.NettyServer;
import io.getbit.gim.core.routing.ClusterMessageRouter;
import lombok.Getter;

/**
 * StartContext.java
 *
 * 启动上下文：包含 facade 和 nettyServer，统一管理启停
 *
 * @author gogym
 */
@Getter
public class StartContext {

    private final IMServerFacade facade;
    private final NettyServer nettyServer;
    private final ClusterMessageRouter clusterRouter;

    public StartContext(IMServerFacade facade, NettyServer nettyServer, ClusterMessageRouter clusterRouter) {
        this.facade = facade;
        this.nettyServer = nettyServer;
        this.clusterRouter = clusterRouter;
    }

    /**
     * 启动 IM 服务器（包括集群路由订阅和 Netty 监听）
     */
    public void start() {
        clusterRouter.start();
        nettyServer.start();
    }

    /**
     * 停止 IM 服务器
     */
    public void stop() {
        nettyServer.stop();
        clusterRouter.stop();
    }
}
