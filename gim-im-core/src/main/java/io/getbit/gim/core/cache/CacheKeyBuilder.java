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

package io.getbit.gim.core.cache;

/**
 * CacheKeyBuilder.java
 * <p>
 * IM 模块 Redis 缓存 Key 构建器
 * 统一管理所有 IM 缓存的 key 前缀和格式，避免各模块硬编码 key
 *
 * @author gogym
 */
public class CacheKeyBuilder {

    private static final String PREFIX = "im:";

    // ====================== 用户路由 ======================

    /**
     * 用户所在节点ID（用于集群路由）
     * 对应 UserRouteService 中的 gim_route:{userId}
     */
    public static String userRoute(String userId) {
        return PREFIX + "user_route:" + userId;
    }

    // ====================== 健康检查 ======================

    /**
     * Redis 连接探测 key
     */
    public static String healthProbe() {
        return PREFIX + "health_probe";
    }

    // ====================== WebRTC 通话 ======================

    /**
     * 1:1 通话会话 key（值为会话 JSON 快照，不含节点本地 Channel）
     */
    public static String rtcCall(String callId) {
        return PREFIX + "rtc:call:" + callId;
    }

    /**
     * 用户通话占用占位 key（值为 callId，用于忙线互斥）
     */
    public static String rtcBusy(String userId) {
        return PREFIX + "rtc:busy:" + userId;
    }
}
