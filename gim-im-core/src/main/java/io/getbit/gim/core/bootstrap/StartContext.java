/*
 * Copyright 2026 gogym
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
