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

package io.getbit.gim.webrtc.groupcall.model;

import io.getbit.gim.webrtc.enums.GroupCallMemberStatus;
import io.netty.channel.Channel;
import lombok.Data;

/**
 * GroupCallMember.java
 *
 * 群通话房间成员
 *
 * @author gogym
 */
@Data
public class GroupCallMember {

    private String userId;

    private GroupCallMemberStatus status;

    /**
     * 加入通话时间（毫秒，未加入为 0）
     */
    private long joinTime;

    /**
     * 摄像头开关状态（null 表示未上报，由 mediaState(100) 信令更新）
     */
    private volatile Boolean camera;

    /**
     * 麦克风开关状态（null 表示未上报，由 mediaState(100) 信令更新）
     */
    private volatile Boolean mic;

    /**
     * 成员最近一次绑定的在线连接（掉线后由 onDisconnect 清理）
     */
    private transient volatile Channel channel;

    public GroupCallMember(String userId, GroupCallMemberStatus status) {
        this.userId = userId;
        this.status = status;
    }

    public boolean isJoined() {
        return status == GroupCallMemberStatus.JOINED;
    }
}
