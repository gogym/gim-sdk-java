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

package io.getbit.gim.core.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * GimThreads.java
 * <p>
 * 线程工具：统一创建命名守护线程调度器，
 * 收敛各组件（消息重发/通话超时/会话续期等）重复的
 * "单线程命名守护调度器"样板代码
 *
 * @author gogym
 */
public final class GimThreads {

    private GimThreads() {
    }

    /**
     * 创建单线程命名守护调度器（组件内部定时任务用）
     * 守护线程不阻止 JVM 退出，组件 shutdown 时需自行 shutdownNow
     *
     * @param threadName 线程名（如 "gim-one-to-one-call"）
     */
    public static ScheduledExecutorService singleDaemonScheduler(String threadName) {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }
}
