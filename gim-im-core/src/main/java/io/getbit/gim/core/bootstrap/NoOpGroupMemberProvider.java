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

package io.getbit.gim.core.bootstrap;

import io.getbit.gim.core.spi.ImGroupMemberProvider;

import java.util.Collections;
import java.util.List;

/**
 * NoOpGroupMemberProvider.java
 *
 * ImGroupMemberProvider 空实现（未配置群成员提供者时的默认占位，包级私有）
 *
 * @author gogym
 */
class NoOpGroupMemberProvider implements ImGroupMemberProvider {

    @Override
    public List<String> getGroupMemberUserIds(String groupId) {
        return Collections.emptyList();
    }
}
