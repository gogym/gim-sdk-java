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

    /**
     * 接听后连接超时（秒）：CONNECTING 状态超过该时长仍未收到 offer（未进入 TALKING），
     * 服务端自动结束会话并通知双方，
     * 兜底接听后客户端异常退出未发挂断信令导致对端无限等待的场景
     */
    private int connectTimeoutSeconds = 30;
}
