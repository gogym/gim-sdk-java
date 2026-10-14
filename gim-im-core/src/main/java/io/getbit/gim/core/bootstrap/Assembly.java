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
