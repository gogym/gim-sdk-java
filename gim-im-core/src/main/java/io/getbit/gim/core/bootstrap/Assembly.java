package io.getbit.gim.core.bootstrap;

import io.getbit.gim.core.routing.ClusterMessageRouter;

/**
 * Assembly.java
 *
 * GimBootstrap 内部组装结果（包级私有）
 *
 * @author gogym
 */
class Assembly {

    final IMServerFacade facade;
    final ClusterMessageRouter clusterRouter;

    Assembly(IMServerFacade facade, ClusterMessageRouter clusterRouter) {
        this.facade = facade;
        this.clusterRouter = clusterRouter;
    }
}
