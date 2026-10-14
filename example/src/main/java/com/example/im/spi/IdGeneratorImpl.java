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

import io.getbit.gim.core.spi.ImIdGenerator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * ID 生成器实现 — 基于雪花算法（简化版）
 * <p>
 * SDK 用此接口生成消息ID和群组ID。
 * 你可以替换为雪花算法、UUID、数据库序列等。
 *
 * @author gogym
 */
@Component
public class IdGeneratorImpl implements ImIdGenerator {

    // ===== 生产环境请使用真正的雪花算法实现（如 Huutool IdUtil、美团 Leaf 等）=====

    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis());

    @Override
    public String generateMsgId() {
        // 简化实现：时间戳 + 自增序列
        // 生产环境请替换为:
        //   return IdUtil.getSnowflakeNextIdStr();
        return String.valueOf(sequence.incrementAndGet());
    }
}
