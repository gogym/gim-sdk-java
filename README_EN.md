# GIM IM SDK

[中文](README.md) | **English**

A high-performance, production-ready Java IM SDK built on **Netty + Protobuf**, covering 1:1 chat, group chat, message routing, delivery ACK, heartbeat keep-alive and WebRTC audio/video signaling.

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

## Brief Introduction

GIM IM SDK is a production-ready Java IM server-side SDK. Long-connection management, Protobuf codec, message routing, reliable delivery, WebRTC signaling and group-call room lifecycle are all built-in; you only plug in your own Redis, token verifier, ID generator and friend/group providers through SPI.

- **Out of the box**: adding `gim-im-starter` gives you a complete Netty long-connection IM server
- **SPI decoupling**: core capabilities are extended through 7 SPI interfaces, with zero hard binding to any specific middleware
- **Cluster ready**: cross-node routing and session sync based on Redis Pub/Sub, scales out horizontally by nature
- **Audio/video signaling**: built-in 1:1 call state machine + group video Mesh/SFU hybrid architecture
- **Fully open source**: released under the **Apache License 2.0**

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                  Consumer business code                       │
│      ImEventListener / ImSingleCallListener / Business API   │
├──────────────────────────────────────────────────────────────┤
│                  SPI extension layer                         │
│   ImRedisAdapter │ ImTokenVerifier │ ImIdGenerator           │
│   ImFriendProvider │ ImGroupMemberProvider                   │
│   ImRedisSubscriber │ ImGroupCallListener ...                │
├──────────────────────────────────────────────────────────────┤
│                  Business capability layer                   │
│   1:1 chat · Group chat · Recall · Read receipt · Presence   │
│   Heartbeat · ACK · Auto resend                              │
│   RTC 1:1 sessions · RTC group-call rooms (Mesh / SFU)       │
├──────────────────────────────────────────────────────────────┤
│                  Routing & session layer                     │
│   ChannelManager (local userId ↔ Channel)                    │
│   UserRouteService (cross-node lookup, Redis-backed)         │
│   ClusterMessageRouter (Redis Pub/Sub forwarding)            │
├──────────────────────────────────────────────────────────────┤
│                  Protocol & transport layer                  │
│   Protobuf binary envelope (Packet / Cmd)                    │
│   Netty 4 Pipeline: PacketCodec → AuthHandler → Dispatcher   │
└──────────────────────────────────────────────────────────────┘
```

### Modules

| Module | Description |
|--------|-------------|
| **gim-im-protocol** | Protobuf protocol definition and codec utilities: `Packet`, `Cmd`, `ContentType`, `DeviceType`, `PacketCodec` |
| **gim-im-core** | Core engine: Netty long-connection server, connection auth, message routing, ACK/heartbeat, group/friend notification, SPI abstractions |
| **gim-im-webrtc** | WebRTC signaling module: 1:1 call session management, group-call room management (Mesh + SFU), LiveKit reference adapter |
| **gim-im-starter** | Spring Boot one-click starter (core + webrtc) with auto-configuration, including Actuator health indicator |
| **example** | Full integration examples: Spring Boot / non-Spring / cluster / group-call, four typical scenarios |

---

## Features

### 1. Streamlined signaling protocol (Packet + Cmd)

All messages use a single Protobuf binary envelope `Packet`: `cmd + sequence + requestId + timestamp + body`. Business semantics live in `body` and are deserialized into the corresponding message per cmd (`ChatMessage`, `ServerAck`, `RtcSignal`, etc.).

| Cmd range | Purpose |
|-----------|---------|
| 1 ~ 9 | Connection management: BIND_REQ / BIND_RESP / HEARTBEAT / KICK_NOTIFY |
| 10 ~ 19 | Chat messages: 1:1 / group / SERVER_ACK / DELIVERY_ACK / READ_RECEIPT / MSG_RECALL |
| 20 ~ 29 | Presence: ONLINE_STATUS_NOTIFY |
| 30 ~ 39 | Friend notifications: FRIEND_REQUEST / FRIEND_STATUS |
| 40 ~ 49 | Group notifications: GROUP_MEMBER / GROUP / GROUP_JOIN_REQUEST |
| 50 ~ 59 | WebRTC signaling: RTC_SIGNAL (1:1) / RTC_GROUP (group) |

**Why a single envelope?** The routing and protocol layers only care about `cmd` and never need to understand the business body; adding new semantics only requires a new cmd + message, with zero changes to the SDK kernel.

### 2. SPI-first, zero middleware binding

The SDK does not depend on any specific Redis client, JWT library or ID generator. All external dependencies are defined as SPI interfaces; you provide implementations in your own project as Spring beans or Builder parameters:

| SPI | Required | Description |
|-----|----------|-------------|
| `ImRedisAdapter` | **Required** for cluster / shared sessions | Redis read/write and Pub/Sub publish adapter; `setnx` provides atomic busy-line occupation |
| `ImTokenVerifier` | **Required** | Verifies the token and extracts userId during first-packet authentication |
| `ImIdGenerator` | **Required** | Generates message / call / group roomId IDs |
| `ImRedisSubscriber` | **Required** for cluster | Redis Pub/Sub subscription for cross-node message forwarding |
| `ImFriendProvider` | **Required** for friend check / presence notify | Provides friend relations and notification targets |
| `ImGroupMemberProvider` | **Required** for group chat | Group member list / mute check / membership decision |
| `ImEventListener` | Optional | Online/offline, offline messages, message callbacks, recall, delivery failure events |
| `ImSingleCallListener` | Optional | 1:1 call business events (call detail records, duration billing) |
| `ImGroupCallListener` | Optional | Group-call business events (room lifecycle, members joining/leaving) |
| `SfuAdapter` | Needed for group video SFU mode | Integrates external SFUs such as LiveKit / Jitsi / mediasoup (built-in LiveKit reference implementation) |

```java
// 3-step integration: register SPI beans + configure + start
@Component
public class RedisAdapterImpl implements ImRedisAdapter { /* ... */ }

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);   // SDK auto-configuration
    }
}
```

### 3. Reliable long-connection delivery

- **SERVER_ACK**: the server replies with a `ServerAck` carrying the assigned `serverMsgId` as soon as a message arrives; clients can match idempotently by `clientRequestId`.
- **DELIVERY_ACK**: after the receiver client confirms receipt it sends back a `DeliveryAck`, showing "delivered" to the sender.
- **Auto resend**: when `gim.auto-rewrite=true`, the SDK periodically resends un-ACKed messages per `re-write-num / re-write-delay`; `MessageAckTracker` tracks in-flight state.
- **Heartbeat & idle detection**: `enable-heart-beat` + `heart-beat-interval` are driven by the Netty `IdleStateHandler`; timeouts close the connection and trigger `ImEventListener.onUserOffline`.

### 4. Cluster mode: Redis-driven routing and forwarding

In single-node mode routing is entirely local in-memory; with `gim.enable-cluster=true` enabled:

```
ClientA ──▶ Node1 ──┐
                    ├──▶ Redis Pub/Sub (im:cluster:broadcast) ──▶ Node2 ──▶ ClientB
                    │
                    └──▶ Redis Hash (im:route:{userId}) stores userId → serverId
```

- **UserRouteService** writes `userId → serverId` into Redis (TTL aligned with heartbeat) to locate which server a user is on across nodes.
- **ClusterMessageRouter** broadcasts the Packet to the target node via `ImRedisAdapter.publish`; the target node consumes it through `ImRedisSubscriber` and delivers it to the local Channel.
- **1:1 call sessions** and **busy-line exclusion** are both stored in Redis: `im:rtc:call:{callId}`, `im:rtc:busy:{userId}`, with `setnx` guaranteeing atomic occupation.

### 5. WebRTC calls: 1:1 + group video (Mesh + SFU)

The SDK ships complete RTC signaling handling and lifecycle management; you only implement `ImSingleCallListener` / `ImGroupCallListener` to receive call-detail events.

| Feature | 1:1 call | Group video call |
|---------|----------|------------------|
| Session/room management | `SingleCallSessionManager` | `GroupCallSessionManager` |
| Busy-line exclusion | ✅ Standalone + cluster Redis atomic occupation | ✅ Group/1:1 mutual exclusion |
| Server-generated callId | Generated by `ImIdGenerator` when the client omits it, returned to the caller via `CALL_ACK=9` | Same, plus a generated `roomId` for SFU |
| Ringing/invite timeout | `ring-timeout-seconds` auto-ends the call + `callCancel(reason=timeout)` | `invite-timeout-seconds` |
| Disconnect cleanup | All devices offline → peer receives `callHangup(reason=disconnect)` | Member disconnect auto-leaves room, empty rooms recycled by TTL |
| Media mode | Pure signaling relay, P2P direct connection | **Mesh** (≤8 members) P2P / **SFU** (20+ members) via LiveKit etc. |
| Session renewal | TALKING state rolls over the Redis TTL, long calls are not mistakenly cleared | Same |

Signaling number conventions (`Cmd` and `signalType`):

```
Cmd=50 (RTC_SIGNAL)     1:1 call signaling
Cmd=51 (RTC_GROUP)      Group video signaling
signalType 1~8          Generic media signaling: offer / answer / ice / callRequest / Accept / Reject / Cancel / Hangup
signalType 9            callAck (server to caller, fills in callId)
signalType 20~27        Group-call lifecycle: groupCallRequest / Invite / Join / Reject / Leave / End / participantNotify / roomState
signalType 100          mediaState (camera/microphone toggle, shared by 1:1 and group)
```

### 6. Messaging: 1:1 chat · group chat · recall · read receipt

- **1:1 chat**: optional friend check (via `ImFriendProvider.isFriend`), routed to all online devices of the peer; when no route exists, `ImEventListener.onOfflineMessage` triggers the offline-push hook.
- **Group chat**: `ImGroupMemberProvider.getGroupMemberUserIds` fetches members → iterate and route/deliver; mute check supported (`checkCanSendMessage`).
- **Recall**: `MSG_RECALL_REQ` validates msgId and sender identity, then pushes `MSG_RECALL_NOTIFY` to the peer or the whole group, and fires `onMessageRecalled` for business persistence.
- **Read receipt**: `READ_RECEIPT` in 1:1 chat is automatically routed to the original sender; in group chat it goes through the `onReadReceipt` callback for business handling.

### 7. More features

- **Same-device-type kick-out**: `DeviceType` (mobile / desktop / web / pad) — same type kicks the old session, different types coexist.
- **Health check**: built-in `ImNodeHealthIndicator`, exposed via Spring Boot Actuator `/actuator/health`.
- **Graceful start/stop**: `GimLifecycle` implements `SmartLifecycle`, stopping Netty / cluster router / session managers in order on container shutdown.
- **JDK 11 compatible**: `maven.compiler.release=11`, runs on JDK 11 / 17 / 21.
- **Zero hard Spring dependency**: the core / webrtc / protocol modules do not depend on Spring; `GimBootstrap` provides a manual assembly entry point.
- **Lombok-powered**: `@Data` / `@Slf4j` / `@Getter` used extensively; configuration classes support `@ConfigurationProperties` with the `gim.*` prefix.

---

## Quick Start

### 1. Add the Maven dependency

```xml
<dependency>
    <groupId>io.getbit</groupId>
    <artifactId>gim-im-starter</artifactId>
    <version>1.0.1-SNAPSHOT</version>
</dependency>
```

### 2. Configure `application.yml`

```yaml
gim:
  netty:
    port: 3333              # IM long-connection port
    boss-threads: 1
    worker-threads: 0       # 0 = auto-detect CPU cores
    backlog: 512
  enable-heart-beat: true
  heart-beat-interval: 30   # heartbeat interval (seconds)
  enable-cluster: false     # standalone/cluster switch
  auto-rewrite: false       # whether un-ACKed messages are auto-resent
  re-write-num: 3
  re-write-delay: 1000
  msg:
    ack-timeout-seconds: 10
    max-retries: 3
  rtc-call:
    enabled: true           # 1:1 call session management
    ring-timeout-seconds: 60
  rtc-group-call:
    enabled: true           # group video
    mode: auto              # auto / mesh / sfu
    mesh-max-members: 8
    invite-timeout-seconds: 60
    empty-room-ttl-seconds: 30
    sfu-provider: none      # none / livekit
```

### 3. Implement the SPI

```java
@Component
public class TokenVerifierImpl implements ImTokenVerifier {
    @Override
    public String verifyAndExtractUserId(String token) {
        // TODO: replace with JWT / OAuth2 / custom signature scheme
        // returning null means authentication failed
        return parseUserId(token);
    }
}

@Component
public class IdGeneratorImpl implements ImIdGenerator {
    @Override
    public String generateMsgId() {
        return snowflake.nextIdStr();   // snowflake algorithm
    }
}

@Component
public class RedisAdapterImpl implements ImRedisAdapter {
    private final StringRedisTemplate redis;
    // implement setex / get / setnx / del / publish
}
```

### 4. Start the application

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        // SDK is auto-configured via GimAutoConfiguration / WebRtcAutoConfiguration
        // The Netty server is started/stopped by GimLifecycle through SmartLifecycle
    }
}
```

### 5. Non-Spring environments (manual assembly)

```java
GimProperties config = GimProperties.builder()
        .nettyPort(3333)
        .enableCluster(false)
        .build();

StartContext ctx = GimBootstrap.builder()
        .config(config)
        .tokenVerifier(myTokenVerifier)
        .idGenerator(myIdGenerator)
        .redisAdapter(myRedisAdapter)                 // optional in standalone mode
        .friendProvider(myFriendProvider)             // required for group chat / friend check
        .groupMemberProvider(myGroupMemberProvider)
        .addEventListener(myEventListener)
        .buildWithServer();

ctx.start();
```

> For more examples see `ExampleApplication` / `NonSpringExample` / `ClusterExample` / `GroupCallExample` in the `example` module.

---

## Documentation

Full documentation is published on the GitHub Wiki:

- [Wiki Home (EN)](https://github.com/gogym/gim-sdk-java/wiki/Home-EN)
- [Quick Start (EN)](https://github.com/gogym/gim-sdk-java/wiki/Quick-Start)
- [Architecture Overview (EN)](https://github.com/gogym/gim-sdk-java/wiki/Architecture-Overview)
- [Core Concept: Packet & Protocol Codec (EN)](https://github.com/gogym/gim-sdk-java/wiki/Core-Concept-Packet-and-Codec)
- [Core Concept: Connection & Authentication (EN)](https://github.com/gogym/gim-sdk-java/wiki/Core-Concept-Connection-and-Auth)
- [Core Concept: SPI Extensions (EN)](https://github.com/gogym/gim-sdk-java/wiki/Core-Concept-SPI-Extensions)
- [Messaging: 1:1 & Group Chat (EN)](https://github.com/gogym/gim-sdk-java/wiki/Messaging-Single-and-Group-Chat)
- [Messaging: Heartbeat & Reliable Delivery (EN)](https://github.com/gogym/gim-sdk-java/wiki/Messaging-Heartbeat-and-Reliable-Delivery)
- [Messaging: Recall, Read Receipt & Presence (EN)](https://github.com/gogym/gim-sdk-java/wiki/Messaging-Recall-Read-Receipt-Online-Status)
- [Cluster Deployment & Redis Integration (EN)](https://github.com/gogym/gim-sdk-java/wiki/Cluster-Deployment-and-Redis)
- [WebRTC: 1:1 Call (EN)](https://github.com/gogym/gim-sdk-java/wiki/WebRTC-One-to-One-Call)
- [WebRTC: Group Video Call (Mesh + SFU) (EN)](https://github.com/gogym/gim-sdk-java/wiki/WebRTC-Group-Video-Call)
- [Configuration Reference: GimProperties (EN)](https://github.com/gogym/gim-sdk-java/wiki/Configuration-Reference-GimProperties)
- [Health Check & Operations (EN)](https://github.com/gogym/gim-sdk-java/wiki/Health-Check-and-Ops)

Chinese documentation: [Wiki 首页](https://github.com/gogym/gim-sdk-java/wiki/Home) and the other pages on the [Wiki](https://github.com/gogym/gim-sdk-java/wiki).

---

## Build

```bash
# JDK 17 (the SDK itself targets JDK 11; the example demo module uses JDK 17)
mvn clean install -DskipTests

# Build the protocol module alone and trigger protoc
mvn -pl gim-im-protocol -am clean install

# Run unit tests (the WebRTC module ships group/1:1 session tests)
mvn test -pl gim-im-webrtc
```

---

## Why GIM

1. **Hand-rolling a Netty + Protobuf IM is hard**: connection management, protocol codec, heartbeat, ACK, cluster routing and WebRTC signaling can each cost weeks of pitfall-filling.
2. **Existing solutions are either SaaS black boxes or too heavy**: GIM takes a "thin shell + SPI" approach, letting you plug in the Redis / JWT / snowflake stack you already know, with every code path transparent.
3. **Audio/video signaling is repetitive work**: the state machines, timeout cleanup, disconnect handling and SFU token issuing for 1:1 and group calls are highly templated — let the SDK do it once.
4. **Readability first**: packages are split by business domain (connection / message / routing / notify / webrtc/singlecall / webrtc/groupcall), no file exceeds a thousand lines, comment density matches mainstream open-source projects, friendly for customization and teaching.

---

## Issues & Feedback

- [GitHub Issues](https://github.com/gogym/gim-sdk-java/issues)

---

## License

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

Full terms in the [LICENSE](LICENSE) file at the project root.

---

## Author

**gogym**

- GitHub：<https://github.com/gogym>
- Email：<189155278@qq.com>
