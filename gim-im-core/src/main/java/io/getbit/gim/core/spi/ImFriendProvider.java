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

package io.getbit.gim.core.spi;

import java.util.Collections;
import java.util.List;

/**
 * ImFriendProvider.java
 *
 * SPI接口：好友关系提供者
 * 使用方实现此接口来提供好友关系查询能力
 * 用于单聊好友校验、在线状态同步等
 *
 * @author gogym
 */
public interface ImFriendProvider {

    /**
     * 判断两个用户是否为好友关系
     *
     * @param userId   用户ID
     * @param friendId 对方用户ID
     * @return true=是好友
     */
    boolean isFriend(String userId, String friendId);

    /**
     * 获取用户的好友列表
     *
     * @param userId 用户ID
     * @return 好友userId列表
     */
    List<String> getFriendIds(String userId);

    /**
     * 查询用户在线时应通知的好友ID列表
     * 默认返回全部好友，使用方可按需覆盖（如只通知双向好友）
     *
     * @param userId 用户ID
     * @return 需要通知的好友userId列表
     */
    default List<String> getOnlineNotifyFriendIds(String userId) {
        return getFriendIds(userId);
    }
}
