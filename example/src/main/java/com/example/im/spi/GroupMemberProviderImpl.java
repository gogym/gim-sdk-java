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

import io.getbit.gim.core.spi.ImGroupMemberProvider;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 群组成员提供者实现示例
 * <p>
 * SDK 在群聊消息投递时通过此接口获取群成员列表和禁言状态。
 * 生产环境中应对接数据库或缓存查询群成员。
 * <p>
 * 核心方法：
 * <ul>
 *   <li>{@code getGroupMemberUserIds(groupId)} — 获取群活跃成员列表，用于消息路由</li>
 *   <li>{@code checkCanSendMessage(groupId, userId)} — 禁言检查（全员禁言 + 个人禁言）</li>
 *   <li>{@code isGroupMember(groupId, userId)} — 判断是否为群成员</li>
 * </ul>
 *
 * @author gogym
 */
@Component
public class GroupMemberProviderImpl implements ImGroupMemberProvider {

    // TODO: 注入群成员 Repository
    // private final ImGroupMemberRepository groupMemberRepository;

    @Override
    public List<String> getGroupMemberUserIds(String groupId) {
        // TODO: 从数据库/缓存查询群活跃成员 userId 列表
        // return groupMemberRepository.findActiveMemberUserIds(groupId)
        //         .stream().map(String::valueOf).toList();
        return Collections.emptyList();
    }

    @Override
    public String checkCanSendMessage(String groupId, String userId) {
        // TODO: 检查全员禁言 + 个人禁言
        // ImGroup group = groupRepository.findByGroupId(groupId);
        // ImGroupMember member = groupMemberRepository.findMember(groupId, userId);
        // if (group.getMuteAll() == 1 && member.getRole() < 1) return "全员禁言中";
        // if (member.getIsMuted() == 1) return "你已被禁言";
        return null; // null 表示可以发送
    }

    @Override
    public boolean isGroupMember(String groupId, String userId) {
        // TODO: 查询是否群成员
        // return groupMemberRepository.isMember(groupId, Long.parseLong(userId));
        return true;
    }
}
