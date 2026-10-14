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

import io.getbit.gim.protocol.codec.DeviceType;
import io.getbit.gim.protocol.codec.ImProto;

/**
 * ImEventListener.java
 *
 * SPI接口：IM事件监听器
 * 使用方可实现此接口来接收用户上下线、消息投递失败、离线消息等事件
 * 用于业务扩展（如推送在线状态、记录登录日志、触发离线推送等）
 *
 * @author gogym
 */
public interface ImEventListener {

    /**
     * 用户上线（绑定连接成功时触发）
     *
     * @param userId   用户ID
     * @param device   设备类型
     * @param serverId 所在节点ID
     */
    default void onUserOnline(String userId, DeviceType device, String serverId) {}

    /**
     * 用户下线（所有设备断开连接时触发）
     *
     * @param userId 用户ID
     */
    default void onUserOffline(String userId) {}

    /**
     * 消息投递失败（接收者离线且无路由时触发）
     *
     * @param packet     投递失败的消息包
     * @param receiverId 接收者ID
     * @param reason     失败原因
     */
    default void onMessageDeliveryFailed(ImProto.Packet packet, String receiverId, String reason) {}

    /**
     * 离线消息回调（接收方不在线时触发）
     * 使用方可实现此方法来触发离线推送（APNs/FCM）或入库待拉取
     * 通过 packet.getCmd() 可区分消息类型（聊天消息、好友通知、群通知、信令等）
     *
     * @param packet     离线消息包（统一信封，按 cmd 解析 body）
     * @param receiverId 接收者ID
     * @param reason     离线原因（OFFLINE / ROUTE_NOT_FOUND / ACK_TIMEOUT）
     */
    default void onOfflineMessage(ImProto.Packet packet, String receiverId, String reason) {}

    /**
     * 消息撤回回调
     * 使用方可实现此方法来更新 DB 中消息状态（如标记为已撤回）
     * 通过 packet body 可解析 MsgRecallRequest 获取 msgId、conversationId 等
     *
     * @param packet 撤回请求包（cmd=MSG_RECALL_REQ，body 为 MsgRecallRequest）
     */
    default void onMessageRecalled(ImProto.Packet packet) {}

    /**
     * 已读回执回调
     * 当无法自动路由已读回执时（如群聊场景），通过此回调通知使用方处理
     * 通过 packet body 可解析 ReadReceipt 获取 conversationId、lastReadMsgId 等
     *
     * @param packet 已读回执包（cmd=READ_RECEIPT，body 为 ReadReceipt）
     */
    default void onReadReceipt(ImProto.Packet packet) {}

    /**
     * 消息回调（所有正常投递的消息均触发，无论接收方是否在线）
     * 使用方可实现此方法进行消息持久化（写入 DB / 发送到 MQ 等）
     * 通过 packet.getCmd() 可区分消息类型（聊天消息、好友通知、群通知、信令等）
     *
     * @param packet 消息包（统一信封，按 cmd 解析 body）
     */
    default void onReceivedMessage(ImProto.Packet packet) {}
}
