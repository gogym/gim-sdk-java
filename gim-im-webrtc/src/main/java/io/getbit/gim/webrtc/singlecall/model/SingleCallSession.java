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

import io.getbit.gim.webrtc.enums.SingleCallSessionStatus;
import io.netty.channel.Channel;
import lombok.Data;

/**
 * 1:1 通话会话模型
 * <p>
 * 承载主叫/被叫、通话状态、通道引用与关键时间戳，供服务端会话管理与话单回调使用。
 *
 * @author gogym
 */
@Data
public class SingleCallSession {
    private String callId;
    private String callerId;
    private String calleeId;
    private String callType;
    private SingleCallSessionStatus status;
    private Channel callerChannel;
    private Channel calleeChannel;
    private long createTime;
    private long connectTime;
    private long endTime;

    public long getDuration() {
        if (endTime > 0) {
            return endTime - connectTime;
        }
        if (connectTime > 0) {
            return System.currentTimeMillis() - connectTime;
        }
        return 0;
    }
    public long getDurationSeconds() { return getDuration() / 1000; }
    public boolean isParticipant(String userId) { return userId != null && (userId.equals(callerId) || userId.equals(calleeId)); }

    /**
     * 获取对方用户ID
     *
     * @param userId 本方用户ID（非参与者时返回 null）
     */
    public String getPeerId(String userId) {
        if (userId == null) {
            return null;
        }
        if (userId.equals(callerId)) {
            return calleeId;
        }
        if (userId.equals(calleeId)) {
            return callerId;
        }
        return null;
    }
    public Channel getChannelByUser(String userId) {
        if (userId == null) {
            return null;
        }
        if (userId.equals(callerId)) {
            return callerChannel;
        }
        if (userId.equals(calleeId)) {
            return calleeChannel;
        }
        return null;
    }
}
