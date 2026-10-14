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
 * ConnectionCloseListener.java
 *
 * 连接关闭监听器 SPI（SDK 内部扩展点）
 * 用户全部设备离线（连接关闭链路）时触发，用于模块级资源清理。
 * 典型用途：WebRTC 群通话掉线清理（GroupCallSessionManager.onDisconnect）。
 *
 * 触发时机与 ImEventListener.onUserOffline 一致：用户最后一个在线设备断开时。
 *
 * @author gogym
 */
public interface ConnectionCloseListener {

    /**
     * 用户全部设备已离线时回调
     *
     * @param userId 离线用户ID
     */
    void onConnectionClosed(String userId);
}
