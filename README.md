# GIM IM SDK

高性能、开箱即用的 IM 即时通讯 SDK，基于 **Netty + Protobuf** 构建，支持单聊、群聊、消息路由、ACK 确认、心跳检测、WebRTC 信令等功能。

## 特性

- **开箱即用** — 引入 `gim-im-starter` 即可获得 IM 长连接服务能力
- **SPI 扩展** — 通过 7 个 SPI 接口灵活对接你的 Redis、Token 验证、ID 生成、MQ 等
- **高性能长连接** — 基于 Netty 4 + Protobuf 二进制协议，支持心跳检测、ACK 确认、自动重发
- **丰富消息能力** — 支持单聊、群聊、消息撤回、已读回执、投递确认、RTC 信令
- **群视频通话** — 混合架构：小群（≤8 人）Mesh P2P 直连，大群（20+ 人）对接 SFU，服务端统一管理房间生命周期、媒体开关同步与话单回调
- **1:1 通话会话管理** — 服务端忙线互斥、振铃超时自动取消、掉线清理、通话话单回调，会话元数据支持 Redis 集群可见
- **集群模式** — 通过 Redis Pub/Sub 实现跨节点消息路由，水平扩展
- **健康检查** — 内置 Spring Boot Actuator 健康指标，方便运维监控

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 11+（SDK 编译目标 11，example 演示工程使用 17） |
| Spring Boot | 2.7.18（starter 自动装配；Boot 3.x 要求 JDK 17+） |
| Netty | 4.1.117.Final |
| Protobuf | 4.32.0 |
| Caffeine | 3.1.8 |
| Jedis | 4.4.8 |

## 模块结构

```
gim-im-sdk
├── gim-im-protocol          # Protobuf 协议定义 & 编解码（Cmd、ContentType、DeviceType、PacketCodec）
├── gim-im-core              # 核心引擎：Netty 服务器、连接认证、消息路由、ACK、心跳、SPI 扩展点
├── gim-im-webrtc            # WebRTC 信令处理 & TURN 凭证服务
├── gim-im-starter           # 一键引入（core + webrtc）
└── example                  # 完整对接使用示例
```

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.getbit</groupId>
    <artifactId>gim-im-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 实现 SPI 接口

SDK 定义了 **7 个 SPI 接口**，使用方只需实现并注册为 Spring Bean：

| SPI 接口 | 说明 | 是否必须 |
|----------|------|---------|
| `ImRedisAdapter` | Redis 操作适配器（set/get/del/publish） | **必须** |
| `ImTokenVerifier` | Token 验证（连接握手时校验身份） | **必须** |
| `ImIdGenerator` | 消息 ID 生成器（雪花算法等） | **必须** |
| `ImMessageBroker` | 消息中间件适配器（异步入库、离线推送） | 可选（默认 NoOp 空实现） |
| `ImEventListener` | 事件监听（上下线、离线消息、投递失败） | 可选（所有方法有默认空实现） |
| `ImRedisSubscriber` | Redis Pub/Sub 订阅（集群模式需要） | 集群模式必须 |
| `ImUserContextResolver` | 用户上下文解析（获取当前登录用户） | 可选（默认从 `X-User-Id` 请求头获取） |

**示例**（以 Redis 适配器为例）：

```java
@Component
public class RedisAdapterImpl implements ImRedisAdapter {

    private final StringRedisTemplate redisTemplate;

    public RedisAdapterImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void setex(String key, int seconds, String value) {
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void del(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }

    @Override
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }
}
```

> 完整示例请参考 `example` 模块。

### 3. 配置 application.yml

```yaml
gim:
  netty:
    port: 3333              # IM 长连接端口
    boss-threads: 1
    worker-threads: 0       # 0 = 自动检测 CPU 核心数
    backlog: 512            # 连接队列大小
  enable-heart-beat: true
  heart-beat-interval: 30   # 心跳间隔（秒）
  enable-offline: false
  enable-cluster: false
  # server-id: server-01    # 集群节点ID（不配置则自动生成）
  auto-rewrite: false       # 是否开启自动重发
  re-write-num: 3           # 重发次数
  re-write-delay: 1000      # 重发间隔（毫秒）
  msg:
    store-topic: im-message-store      # 消息入库 MQ Topic
    offline-topic: im-message-offline  # 离线消息 MQ Topic
    ack-timeout-seconds: 10            # ACK 超时时间
    max-retries: 3                     # 最大重发次数

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### 4. 启动应用

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 1:1 通话（服务端会话管理）

1:1 通话默认为信令转发模式；开启会话管理后（默认开启），服务端理解通话生命周期，提供以下能力：

- **忙线互斥** — 主叫/被叫任一方已在 1:1 或群通话中，`callRequest` 被拦截并向主叫回 `callReject(reason=busy)`
- **服务端 callId** — 客户端未携带 `callId` 时由服务端生成（`ImIdGenerator`）并回填，回传双方
- **振铃超时** — 振铃超过 `ring-timeout-seconds` 无应答，服务端自动结束会话并向主叫下发 `callCancel(reason=timeout)`
- **掉线清理** — 用户全部设备离线时结束其进行中的通话，并向对端下发 `callHangup(reason=disconnect)`
- **话单回调** — 实现 `ImSingleCallListener` 并注册为 Spring Bean，即可收到通话建立/结束事件（含时长与结束原因）

集群部署时，会话元数据经 `ImRedisAdapter` 存入 Redis（`im:rtc:call:{callId}` / `im:rtc:busy:{userId}`），跨节点可见；建议实现 `setnx` 方法以获得原子忙线占位（未实现时降级为 GET+SETEX，存在极小竞态窗口）。

会话状态不做固定时长驱逐：TALKING 会话由续期任务按 `session-ttl-seconds/4` 周期滚动续期（Redis 重写 / 本地刷新），长通话不会因 TTL 被误清理；未配置 Redis 时退化为本地内存态，行为一致。

### 配置示例

```yaml
gim:
  rtc-call:
    enabled: true                # 是否启用（false 时退化为纯信令转发）
    ring-timeout-seconds: 60     # 振铃超时（秒）
    session-ttl-seconds: 7200    # 会话 Redis TTL（秒）
```

## 群视频通话（混合架构：Mesh + SFU）

SDK 内置群通话房间生命周期管理，服务端能力由 `gim-im-starter` 自动装配，业务方只需配置即可：

- **Mesh 模式**（≤ `mesh-max-members` 人，默认 8）：成员间 P2P 直连，服务端只做信令协调，零媒体服务器成本
- **SFU 模式**（>8 人，支持 20+）：SDK 负责房间协调与接入凭证签发（内置 LiveKit 参考实现，可通过 `SfuAdapter` SPI 对接任意 SFU），媒体流由外部 SFU 承载
- 模式选择：`mode: auto` 时按人数自动切换，也支持固定 `mesh` / `sfu`

### 信令流程（cmd=51 RtcGroup，signalType 20~27，mediaState=100 与 1:1 共用）

| signalType | 名称 | 方向 | 说明 |
|-----------|------|------|------|
| 20 | groupCallRequest | 客户端 → 服务端 | 发起群通话，payload 携带 callType、可选 inviteeIds |
| 21 | groupCallInvite | 服务端 → 成员 | 逐成员下发邀请 |
| 22 | groupCallJoin | 客户端 → 服务端 | 加入房间，回应 roomState(27) |
| 23 | groupCallReject | 客户端 → 服务端 | 拒绝邀请 |
| 24 | groupCallLeave | 客户端 → 服务端 | 主动退出 |
| 25 | groupCallEnd | 客户端 → 服务端 | 发起人结束全员通话 |
| 26 | participantNotify | 服务端 → 客户端 | 成员变更通知（join/leave/reject/media/ended） |
| 27 | roomState | 服务端 → 客户端 | 房间快照 + 成员列表（含摄像头/麦克风开关）+ SFU token / TURN 凭据 |
| 100 | mediaState | 客户端 → 服务端 | 成员摄像头/麦克风开关上报（独立高位编号、与 1:1 共用），服务端广播给其他在通话成员 |

Mesh 模式下的媒体信令（offer/answer/ICE，signalType 1~8）与 1:1 通话完全一致，由 `RtcGroupHandler` 扇出转发；掉线清理、邀请超时、空房间回收均由服务端自动处理。

### 业务事件（话单）

实现 `ImGroupCallListener` 并注册为 Spring Bean，即可收到群通话业务事件（与 1:1 的 `ImSingleCallListener` 对称）：

| 事件 | 触发时机 |
|------|---------|
| `onCallStart` | 房间创建成功，携带 callId / groupId / roomId / 发起人 / callType / mesh\|sfu 模式 |
| `onCallEnd` | 通话结束，携带**通话时长**与结束原因（`ended` 发起人结束 / `timeout` 邀请超时 / `empty` 空房回收） |
| `onMemberJoin` | 成员入房（幂等重连不重复触发） |
| `onMemberLeave` | 成员离房，原因 `leave` 主动退出 / `disconnect` 掉线清理 |

### 配置示例

```yaml
gim:
  rtc-group-call:
    enabled: true                # 是否启用群通话
    mode: auto                   # auto / mesh / sfu
    mesh-max-members: 8          # Mesh 人数上限
    invite-timeout-seconds: 60   # 邀请超时
    empty-room-ttl-seconds: 30   # 空房间回收
    sfu-provider: none           # none / livekit
    # SFU 配置（sfu-provider: livekit 时启用）：
    # sfu-host: http://127.0.0.1:7880
    # sfu-api-key: devkey
    # sfu-api-secret: secret
    # sfu-ws-url: wss://im.example.com:7880
    # sfu-token-ttl-seconds: 3600
```

> 完整客户端信令构造示例参考 `example/src/main/java/com/example/im/GroupCallExample.java`。

## 示例项目

`example` 模块提供了完整的对接示例，包含所有 SPI 接口的参考实现：

```
example/src/main/java/com/example/im/
├── ExampleApplication.java        # 启动类
├── GroupCallExample.java          # 群视频通话信令流程示例
└── spi/
    ├── RedisAdapterImpl.java      # Redis 适配器
    ├── RedisSubscriberImpl.java   # Redis Pub/Sub 订阅
    ├── TokenVerifierImpl.java     # Token 验证
    ├── IdGeneratorImpl.java       # ID 生成器
    ├── SingleCallListenerImpl.java # 1:1 通话话单回调示例
    ├── GroupCallListenerImpl.java    # 群通话话单回调示例
    └── ImEventListenerImpl.java   # IM 事件监听
```

## 构建

```bash
mvn clean install -DskipTests
```

## 许可证

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
