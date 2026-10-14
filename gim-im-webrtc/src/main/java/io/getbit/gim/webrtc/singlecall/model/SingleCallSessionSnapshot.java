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

package io.getbit.gim.webrtc.singlecall.model;

import lombok.Data;

/**
 * SingleCallSessionSnapshot.java
 *
 * 1:1 通话会话的 Redis 序列化快照
 * 刻意排除 {@link SingleCallSession} 中的 Channel 等节点本地字段，
 * 仅保留可跨节点还原的会话元数据（status 以枚举名字符串存储）
 *
 * @author gogym
 */
@Data
public class SingleCallSessionSnapshot {

    private String callId;
    private String callerId;
    private String calleeId;
    private String callType;
    private String status;
    private long createTime;
    private long connectTime;
    private long endTime;
}
