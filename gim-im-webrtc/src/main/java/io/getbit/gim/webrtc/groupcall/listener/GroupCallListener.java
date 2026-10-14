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

package io.getbit.gim.webrtc.groupcall.listener;

import io.getbit.gim.webrtc.groupcall.model.GroupCallMember;
import io.getbit.gim.webrtc.groupcall.model.GroupCallRoom;

/**
 * GroupCallListener.java
 *
 * 群通话事件监听器（SDK 内部使用）
 * 由 GroupCallService 实现并注册到 GroupCallSessionManager，
 * 用于将管理器内部触发的事件（掉线清理、超时结束）转化为信令广播。
 * 注意：回调在管理器锁外触发，实现方可安全调用管理器方法。
 *
 * @author gogym
 */
public interface GroupCallListener {

    /**
     * 成员连接断开且已从房间移除时触发（等价于被动 leave）
     *
     * @param room   所在房间（可能已无加入成员）
     * @param member 被移除的成员
     */
    default void onMemberDisconnected(GroupCallRoom room, GroupCallMember member) {
    }

    /**
     * 邀请超时：RINGING 房间超过 inviteTimeoutSeconds 无人加入，房间被自动结束时触发
     *
     * @param room 已结束的房间
     */
    default void onInviteTimeout(GroupCallRoom room) {
    }

    /**
     * 空房间回收：全员离开且超过 emptyRoomTtlSeconds 无人在房时触发
     * 该路径不走 endRoom 流程，需单独通知上层补发业务事件
     *
     * @param room 被回收的房间
     */
    default void onRoomRecycled(GroupCallRoom room) {
    }
}
