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
