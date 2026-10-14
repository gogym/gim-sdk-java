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

/**
 * ImIdGenerator.java
 *
 * SPI接口：消息ID生成器
 * 使用方可对接雪花算法、UUID、数据库序列等
 *
 * @author gogym
 */
public interface ImIdGenerator {

    /**
     * 生成全局唯一消息ID
     *
     * @return 消息ID字符串
     */
    String generateMsgId();
}
