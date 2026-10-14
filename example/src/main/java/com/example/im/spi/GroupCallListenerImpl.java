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

package com.example.im.spi;

import io.getbit.gim.webrtc.enums.GroupCallMode;
import io.getbit.gim.webrtc.groupcall.listener.ImGroupCallListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 群通话事件回调示例 — 话单打印
 * <p>
 * 实现 ImGroupCallListener 并注册为 Spring Bean，
 * SDK 自动收集所有该类型 Bean，在通话发起/结束、成员进出时回调。
 * 回调异常已被 SDK 隔离，不影响通话流程。
 *
 * @author gogym
 */
@Slf4j
@Component
public class GroupCallListenerImpl implements ImGroupCallListener {

    @Override
    public void onCallStart(String callId, String groupId, String roomId, String initiatorId,
                            String callType, GroupCallMode mode) {
        log.info("[群话单] 群通话发起: callId={}, group={}, room={}, initiator={}, type={}, mode={}",
                callId, groupId, roomId, initiatorId, callType, mode);
    }

    @Override
    public void onCallEnd(String callId, String groupId, String roomId, String initiatorId,
                          String callType, GroupCallMode mode, long durationSeconds, String endReason) {
        log.info("[群话单] 群通话结束: callId={}, group={}, room={}, initiator={}, type={}, mode={}, duration={}s, reason={}",
                callId, groupId, roomId, initiatorId, callType, mode, durationSeconds, endReason);
        // 实际业务可在此持久化通话记录 / 计费等
    }

    @Override
    public void onMemberJoin(String callId, String roomId, String userId) {
        log.info("[群话单] 成员加入: callId={}, room={}, userId={}", callId, roomId, userId);
    }

    @Override
    public void onMemberLeave(String callId, String roomId, String userId, String reason) {
        log.info("[群话单] 成员离开: callId={}, room={}, userId={}, reason={}", callId, roomId, userId, reason);
    }
}
