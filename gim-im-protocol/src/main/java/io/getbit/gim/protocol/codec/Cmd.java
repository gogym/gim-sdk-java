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

package io.getbit.gim.protocol.codec;

/**
 * Cmd.java
 *
 * @description: 命令类型常量定义
 * 所有消息的命令类型统一在此定义，与 Packet.cmd 字段对应
 *
 * 命令编号分配规则：
 * - 1~9:    连接管理（绑定、心跳等）
 * - 10~19:  聊天消息（发送、ACK、已读等）
 * - 20~29:  在线状态
 * - 30~39:  好友通知
 * - 40~49:  群组通知
 * - 50~59:  WebRTC 信令
 *
 * @author gogym
 */
public interface Cmd {

    // ==================== 连接管理 (1-9) ====================

    /** 绑定请求（首包认证） */
    int BIND_REQ = 1;

    /** 绑定响应 */
    int BIND_RESP = 2;

    /** 心跳请求 */
    int HEARTBEAT_REQ = 3;

    /** 心跳响应 */
    int HEARTBEAT_RESP = 4;

    /** 踢人通知（服务端 → 客户端，被踢时发送） */
    int KICK_NOTIFY = 5;

    // ==================== 聊天消息 (10-19) ====================

    /** 单聊消息（客户端 → 服务端） */
    int SINGLE_CHAT_MSG = 10;

    /** 群聊消息（客户端 → 服务端） */
    int GROUP_CHAT_MSG = 11;

    /** 服务端 ACK（服务器确认收到） */
    int SERVER_ACK = 12;

    /** 送达 ACK（接收方确认收到） */
    int DELIVERY_ACK = 13;

    /** 已读回执 */
    int READ_RECEIPT = 14;

    /** 消息撤回请求（客户端 → 服务端） */
    int MSG_RECALL_REQ = 15;

    /** 消息撤回通知（服务端 → 客户端） */
    int MSG_RECALL_NOTIFY = 16;

    // ==================== 在线状态 (20-29) ====================

    /** 在线状态变更通知 */
    int ONLINE_STATUS_NOTIFY = 20;

    // ==================== 好友通知 (30-39) ====================

    /** 好友申请通知 */
    int FRIEND_REQUEST_NOTIFY = 30;

    /** 好友状态变更通知 */
    int FRIEND_STATUS_NOTIFY = 31;

    // ==================== 群组通知 (40-49) ====================

    /** 群成员变更通知 */
    int GROUP_MEMBER_NOTIFY = 40;

    /** 群信息/事件通知（信息变更、公告、禁言、角色变更等） */
    int GROUP_NOTIFY = 41;

    /** 入群申请通知 */
    int GROUP_JOIN_REQUEST_NOTIFY = 42;

    // ==================== WebRTC 信令 (50-59) ====================

    /** WebRTC 单聊信令消息 */
    int RTC_SIGNAL = 50;

    /** WebRTC 群聊信令消息 */
    int RTC_GROUP = 51;
}
