# GIM IM SDK

[**中文**](README.md) | [English](README_EN.md)

高性能、开箱即用的 Java IM 即时通讯 SDK，基于 **Netty + Protobuf** 构建，支持单聊、群聊、消息路由、ACK 可靠投递、心跳保活、WebRTC 音视频信令等能力。

A high-performance, cluster-ready IM SDK for Java built on **Netty + Protobuf**, covering 1:1 chat, group chat, routing, delivery ACK, heartbeat and WebRTC signaling.

---

Copyright 2026 gogym

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

---

## 简介 / Brief Introduction

GIM IM SDK 是一套面向生产环境的 Java IM 服务端 SDK：把长连接、协议编解码、消息路由、可靠投递、WebRTC 信令、群通话房间管理这些"IM 通用底座"沉淀到 SDK 内部，业务方只需通过 **SPI** 接入自己的 Redis、Token、ID 生成、好友/群组数据源即可。

GIM IM SDK is a production-ready Java IM server-side SDK. Long-connection management, Protobuf codec, message routing, reliable delivery, WebRTC signaling and group-call room lifecycle are all built-in; you only plug in your own Redis, token verifier, ID generator and friend/group providers through SPI.

- **开箱即用**：引入 `gim-im-starter` 即获得完整的 Netty 长连接 IM 服务
- **SPI 解耦**：核心能力通过 7 个 SPI 接口扩展，零硬绑定具体中间件
- **集群就绪**：基于 Redis Pub/Sub 的跨节点路由与会话同步，天然水平扩展
- **音视频信令**：内置 1:1 通话状态机 + 群视频 Mesh/SFU 混合架构
- **完全开源**：基于 **Apache License 2.0** 协议

---

## 整体架构 / Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     使用方业务代码                             │
│      ImEventListener / ImSingleCallListener / 业务 API       │
├──────────────────────────────────────────────────────────────┤
│                  SPI 扩展点层                                  │
│   ImRedisAdapter │ ImTokenVerifier │ ImIdGenerator           │
│   ImFriendProvider │ ImGroupMemberProvider                   │
│   ImRedisSubscriber │ ImGroupCallListener ...                │
├──────────────────────────────────────────────────────────────┤
│                  业务能力层                                    │
│   单聊 · 群聊 · 撤回 · 已读 · 在线通知                        │
│   心跳 · ACK · 自动重发                                        │
│   RTC 1:1 会话 · RTC 群通话房间（Mesh / SFU）                 │
├──────────────────────────────────────────────────────────────┤
│                  路由与会话层                                  │
│   ChannelManager（本机 userId ↔ Channel）                     │
│   UserRouteService（跨节点定位，Redis 加速）                  │
│   ClusterMessageRouter（Redis Pub/Sub 转发）                  │
├──────────────────────────────────────────────────────────────┤
│                  协议与传输层                                  │
│   Protobuf 二进制信封（Packet / Cmd）                         │
│   Netty 4 Pipeline：PacketCodec → AuthHandler → Dispatcher   │
└──────────────────────────────────────────────────────────────┘
```

### 模块组成 / Modules

| 模块 | 说明 |
|------|------|
| **gim-im-protocol** | Protobuf 协议定义与编解码工具：`Packet`、`Cmd`、`ContentType`、`DeviceType`、`PacketCodec` |
| **gim-im-core** | 核心引擎：Netty 长连接服务器、连接认证、消息路由、ACK/心跳、群/好友通知、SPI 抽象 |
| **gim-im-webrtc** | WebRTC 信令模块：1:1 通话会话管理、群通话房间管理（Mesh + SFU）、LiveKit 参考适配 |
| **gim-im-starter** | Spring Boot 一键引入（core + webrtc）自动装配，含 Actuator 健康指标 |
| **example** | 完整对接示例：Spring Boot / 非 Spring / 集群 / 群通话四类典型场景 |

---

## GIM 的特点 / Features

### 一. 精简的信令协议（Packet + Cmd）

所有消息使用统一的 Protobuf 二进制信封 `Packet`：`cmd + sequence + requestId + timestamp + body`。业务语义放在 `body` 里按 cmd 反序列化为对应的 message（`ChatMessage`、`ServerAck`、`RtcSignal` 等）。

| cmd 号段 | 用途 |
|---------|------|
| 1 ~ 9 | 连接管理：BIND_REQ / BIND_RESP / HEARTBEAT / KICK_NOTIFY |
| 10 ~ 19 | 聊天消息：单聊 / 群聊 / SERVER_ACK / DELIVERY_ACK / READ_RECEIPT / MSG_RECALL |
| 20 ~ 29 | 在线状态：ONLINE_STATUS_NOTIFY |
| 30 ~ 39 | 好友通知：FRIEND_REQUEST / FRIEND_STATUS |
| 40 ~ 49 | 群组通知：GROUP_MEMBER / GROUP / GROUP_JOIN_REQUEST |
| 50 ~ 59 | WebRTC 信令：RTC_SIGNAL（1:1） / RTC_GROUP（群） |

**为什么用统一信封？** 路由与协议层只关心 `cmd`，无需理解业务 body；扩展新语义只需增一个 cmd + message，SDK 内核零改动。

### 二. SPI 优先，零中间件绑定

SDK 不直接依赖任何具体的 Redis 客户端、JWT 库或 ID 生成器。所有外部依赖都以 SPI 接口形式定义，业务方在自己的工程里以 Spring Bean 或 Builder 参数注入实现：

| SPI | 是否必需 | 说明 |
|-----|---------|------|
| `ImRedisAdapter` | 集群/会话共享 **必须** | Redis 读写与 Pub/Sub 发布适配；`setnx` 提供原子忙线占位 |
| `ImTokenVerifier` | **必须** | 首包认证时校验 token 并提取 userId |
| `ImIdGenerator` | **必须** | 消息 / 通话 / 群 roomId 生成 |
| `ImRedisSubscriber` | 集群 **必须** | Redis Pub/Sub 订阅，用于跨节点消息转发 |
| `ImFriendProvider` | 好友校验/在线通知 **必须** | 提供好友关系与通知目标 |
| `ImGroupMemberProvider` | 群聊 **必须** | 群成员列表 / 禁言校验 / 成员判定 |
| `ImEventListener` | 可选 | 上下线、离线消息、消息回调、撤回、投递失败等事件 |
| `ImSingleCallListener` | 可选 | 1:1 通话业务事件（话单、时长计费） |
| `ImGroupCallListener` | 可选 | 群通话业务事件（房间生命周期、成员进出） |
| `SfuAdapter` | 群视频 SFU 模式需要 | 对接 LiveKit / Jitsi / mediasoup 等外部 SFU（内置 LiveKit 参考实现） |

```java
// 3 步接入：Bean 化 SPI + 配置 + 启动
@Component
public class RedisAdapterImpl implements ImRedisAdapter { /* ... */ }

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);   // SDK 自动装配
    }
}
```

### 三. 可靠的长连接投递

- **SERVER_ACK**：服务端收到消息后立即回执 `ServerAck`，携带分配的 `serverMsgId`；客户端可依据 `clientRequestId` 幂等匹配。
- **DELIVERY_ACK**：接收方客户端确认收到消息后回传 `DeliveryAck`，向发送方展示"已送达"。
- **自动重发**：`gim.auto-rewrite=true` 时，SDK 内部按 `re-write-num / re-write-delay` 定时重发未 ACK 消息；`MessageAckTracker` 记录 in-flight 状态。
- **心跳与空闲检测**：`enable-heart-beat` + `heart-beat-interval` 由 Netty `IdleStateHandler` 驱动，超时自动断开并触发 `ImEventListener.onUserOffline`。

### 四. 集群模式：Redis 驱动的路由与转发

单机模式下路由完全在节点本地内存；打开 `gim.enable-cluster=true` 后：

```
ClientA ──▶ Node1 ──┐
                    ├──▶ Redis Pub/Sub (im:cluster:broadcast) ──▶ Node2 ──▶ ClientB
                    │
                    └──▶ Redis Hash (im:route:{userId}) 存储 userId → serverId
```

- **UserRouteService** 将 `userId → serverId` 写入 Redis（TTL 与心跳对齐），跨节点查询用户所在服务器。
- **ClusterMessageRouter** 通过 `ImRedisAdapter.publish` 将 Packet 广播到目标节点，目标节点用 `ImRedisSubscriber` 消费并投递到本地 Channel。
- **1:1 通话会话** 与 **忙线互斥** 均写入 Redis：`im:rtc:call:{callId}`、`im:rtc:busy:{userId}`，`setnx` 保证原子占位。

### 五. WebRTC 通话：1:1 + 群视频（Mesh + SFU）

SDK 内置完整的 RTC 信令处理与生命周期管理，业务方只需实现 `ImSingleCallListener` / `ImGroupCallListener` 即可拿到话单事件。

| 特性 | 1:1 通话 | 群视频通话 |
|------|---------|-----------|
| 会话/房间管理 | `SingleCallSessionManager` | `GroupCallSessionManager` |
| 忙线互斥 | ✅ 单机 + 集群 Redis 原子占位 | ✅ 群/1:1 双向互斥 |
| 服务端 callId | 客户端未传时由 `ImIdGenerator` 生成，`CALL_ACK=9` 回传主叫 | 同左，另生成 `roomId` 用于 SFU |
| 振铃/邀请超时 | `ring-timeout-seconds` 自动结束 + `callCancel(reason=timeout)` | `invite-timeout-seconds` |
| 掉线清理 | 全设备离线 → 对端下发 `callHangup(reason=disconnect)` | 成员掉线自动出房，空房 TTL 回收 |
| 媒体模式 | 纯信令转发，P2P 直连 | **Mesh**（≤8 人）P2P / **SFU**（20+ 人）对接 LiveKit 等 |
| 会话续期 | TALKING 状态滚动续期 Redis TTL，长通话不误清 | 同左 |

信令编号约定（`Cmd` 与 `signalType`）：

```
Cmd=50 (RTC_SIGNAL)     1:1 单聊信令
Cmd=51 (RTC_GROUP)      群视频信令
signalType 1~8          通用媒体信令：offer / answer / ice / callRequest / Accept / Reject / Cancel / Hangup
signalType 9            callAck（服务端 → 主叫，回填 callId）
signalType 20~27        群通话生命周期：groupCallRequest / Invite / Join / Reject / Leave / End / participantNotify / roomState
signalType 100          mediaState（摄像头/麦克风开关，1:1 与群共用）
```

### 六. 消息能力：单聊 · 群聊 · 撤回 · 已读

- **单聊**：可选好友校验（走 `ImFriendProvider.isFriend`），路由到对端所有在线设备；无路由时通过 `ImEventListener.onOfflineMessage` 触发离线推送钩子。
- **群聊**：`ImGroupMemberProvider.getGroupMemberUserIds` 拉取成员 → 遍历路由投递；支持禁言校验（`checkCanSendMessage`）。
- **撤回**：`MSG_RECALL_REQ` 校验 msgId 与发送者身份后向对方或全群下发 `MSG_RECALL_NOTIFY`，并触发 `onMessageRecalled` 供业务落库。
- **已读**：`READ_RECEIPT` 单聊自动路由给原发送者，群聊走 `onReadReceipt` 回调由业务处理。

### 七. 其他特性

- **多端互踢**：`DeviceType`（mobile / desktop / web / pad）同类型互踢、跨类型共存。
- **健康检查**：内置 `ImNodeHealthIndicator`，接入 Spring Boot Actuator `/actuator/health`。
- **优雅启停**：`GimLifecycle` 实现 `SmartLifecycle`，容器关闭时按序停止 Netty / 集群路由 / 会话管理器。
- **JDK 11 兼容**：`maven.compiler.release=11`，可在 JDK 11 / 17 / 21 环境运行。
- **零 Spring 硬依赖**：core / webrtc / protocol 三个模块不依赖 Spring，`GimBootstrap` 提供手动装配入口。
- **Lombok 简化**：`@Data` / `@Slf4j` / `@Getter` 广泛使用，配置类支持 `@ConfigurationProperties` 前缀 `gim.*`。

---

## 快速开始 / Quick Start

### 1. Maven 引入

```xml
<dependency>
    <groupId>io.getbit</groupId>
    <artifactId>gim-im-starter</artifactId>
    <version>1.0.1-SNAPSHOT</version>
</dependency>
```

### 2. 配置 `application.yml`

```yaml
gim:
  netty:
    port: 3333              # IM 长连接端口
    boss-threads: 1
    worker-threads: 0       # 0 = 自动检测 CPU 核心数
    backlog: 512
  enable-heart-beat: true
  heart-beat-interval: 30   # 心跳间隔（秒）
  enable-cluster: false     # 单机/集群开关
  auto-rewrite: false       # 未 ACK 消息是否自动重发
  re-write-num: 3
  re-write-delay: 1000
  msg:
    ack-timeout-seconds: 10
    max-retries: 3
  rtc-call:
    enabled: true           # 1:1 通话会话管理
    ring-timeout-seconds: 60
  rtc-group-call:
    enabled: true           # 群视频
    mode: auto              # auto / mesh / sfu
    mesh-max-members: 8
    invite-timeout-seconds: 60
    empty-room-ttl-seconds: 30
    sfu-provider: none      # none / livekit
```

### 3. 实现 SPI

```java
@Component
public class TokenVerifierImpl implements ImTokenVerifier {
    @Override
    public String verifyAndExtractUserId(String token) {
        // TODO: 换成 JWT / OAuth2 / 自定义签名方案
        // 返回 null 表示认证失败
        return parseUserId(token);
    }
}

@Component
public class IdGeneratorImpl implements ImIdGenerator {
    @Override
    public String generateMsgId() {
        return snowflake.nextIdStr();   // 雪花算法
    }
}

@Component
public class RedisAdapterImpl implements ImRedisAdapter {
    private final StringRedisTemplate redis;
    // 实现 setex / get / setnx / del / publish
}
```

### 4. 启动应用

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        // SDK 通过 GimAutoConfiguration / WebRtcAutoConfiguration 自动装配
        // Netty 服务器由 GimLifecycle 通过 SmartLifecycle 启停
    }
}
```

### 5. 非 Spring 环境（手动装配）

```java
GimProperties config = GimProperties.builder()
        .nettyPort(3333)
        .enableCluster(false)
        .build();

StartContext ctx = GimBootstrap.builder()
        .config(config)
        .tokenVerifier(myTokenVerifier)
        .idGenerator(myIdGenerator)
        .redisAdapter(myRedisAdapter)                 // 单机模式可省略
        .friendProvider(myFriendProvider)             // 群聊/好友校验必须
        .groupMemberProvider(myGroupMemberProvider)
        .addEventListener(myEventListener)
        .buildWithServer();

ctx.start();
```

> 更多示例请参见 `example` 模块下的 `ExampleApplication` / `NonSpringExample` / `ClusterExample` / `GroupCallExample`。

---

## 文档索引 / Documentation

完整文档位于仓库 `wiki/` 目录，同步发布到 GitHub Wiki：

- English documentation: see [README_EN.md](README_EN.md) and the `wiki/en/` directory ([Wiki Home (EN)](wiki/en/Home.md))
- [Wiki 首页](wiki/Home.md)
- [快速开始](wiki/快速开始.md)
- [架构概览](wiki/架构概览.md)
- [核心概念：Packet 与协议编解码](wiki/核心概念：Packet与协议编解码.md)
- [核心概念：连接与认证](wiki/核心概念：连接与认证.md)
- [核心概念：SPI 扩展点](wiki/核心概念：SPI扩展点.md)
- [消息能力：单聊与群聊](wiki/消息能力：单聊与群聊.md)
- [消息能力：心跳与可靠投递](wiki/消息能力：心跳与可靠投递.md)
- [消息能力：撤回、已读与在线状态](wiki/消息能力：撤回、已读与在线状态.md)
- [集群部署与 Redis 集成](wiki/集群部署与Redis集成.md)
- [WebRTC：1:1 通话](wiki/WebRTC：1对1通话.md)
- [WebRTC：群视频通话（Mesh + SFU）](wiki/WebRTC：群视频通话.md)
- [配置参考：GimProperties](wiki/配置参考：GimProperties.md)
- [健康检查与运维](wiki/健康检查与运维.md)

---

## 构建 / Build

```bash
# JDK 17（本项目 SDK 编译目标 JDK 11，example 演示模块使用 JDK 17）
mvn clean install -DskipTests

# 单独编译协议模块并触发 protoc
mvn -pl gim-im-protocol -am clean install

# 运行单元测试（WebRTC 模块自带群/1:1 会话测试）
mvn test -pl gim-im-webrtc
```

---

## 为什么写这个 SDK / Why GIM

1. **Netty + Protobuf 手写 IM 门槛高**：连接管理、协议编解码、心跳、ACK、集群路由、WebRTC 信令，每一项都能踩坑数周。
2. **市面方案要么偏 SaaS 黑盒、要么过重**：本 SDK 走"薄壳 + SPI"路线，让你用熟悉的 Redis / JWT / 雪花算法接入，代码路径全部透明。
3. **音视频信令重复劳动**：1:1 与群通话的状态机、超时清理、掉线联动、SFU 凭证签发高度模板化，交给 SDK 一次搞定。
4. **可读优先**：包按业务域拆分（connection / message / routing / notify / webrtc/singlecall / webrtc/groupcall），单文件不超过千行、注释密度对齐主流开源项目，方便二开与教学。

---

## Bug 反馈与建议 / Issues

- [GitHub Issues](https://github.com/gogym/gim-sdk-java/issues)

---

## 许可证 / License

```
Copyright 2026 gogym

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

完整条款见项目根目录 [LICENSE](LICENSE)。

---

## 作者 / Author

**gogym**

- GitHub：<https://github.com/gogym>
- Email：<189155278@qq.com>
