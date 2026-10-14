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

import io.getbit.gim.core.spi.ImFriendProvider;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * FriendProviderImpl.java
 *
 * 好友关系提供者示例实现
 *
 * TODO: 实际项目中应对接数据库/缓存查询好友关系
 *
 * @author gogym
 */
@Component
public class FriendProviderImpl implements ImFriendProvider {

    // TODO: 注入好友关系 Repository
    // private final ImFriendRepository friendRepository;

    @Override
    public boolean isFriend(String userId, String friendId) {
        // TODO: 查询数据库判断是否为好友
        // return friendRepository.isFriend(Long.parseLong(userId), Long.parseLong(friendId));
        return true; // 示例默认允许
    }

    @Override
    public List<String> getFriendIds(String userId) {
        // TODO: 查询好友列表
        // return friendRepository.findFriendIds(Long.parseLong(userId))
        //         .stream().map(String::valueOf).toList();
        return Collections.emptyList(); // 示例返回空
    }
}
