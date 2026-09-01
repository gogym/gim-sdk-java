package com.example.im;

import com.google.gson.Gson;
import io.getbit.gim.protocol.codec.Cmd;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.protocol.codec.PacketCodec;
import io.getbit.gim.webrtc.dto.GroupCallRequestDto;

/**
 * GroupCallExample.java
 *
 * 群视频通话（混合架构：Mesh + SFU）信令流程示例
 *
 * 服务端能力由 gim-im-starter 自动装配提供，业务方无需编写服务端代码。
 * 本示例演示客户端如何构造群通话生命周期信令（cmd=51, RtcGroup），
 * 以及各阶段服务端的回应，供客户端实现参考。
 *
 * 信令时序（signalType 见 ImProto.proto 枚举注释）：
 *
 *   发起人(A)                服务端                   被邀成员(B/C)
 *      |--- groupCallRequest(9) -->|                       |
 *      |<-- roomState(16) ---------|  (房间快照，发起人回执) |
 *      |                           |--- groupCallInvite(10) -->|
 *      |                           |<-- groupCallJoin(11) ------|  (B接受)
 *      |<-- participantNotify(15) -|   (join 通知)   |<-- roomState(16) --|
 *      |<== RTC_SIGNAL / RtcGroup 扇出 offer/answer/ICE (Mesh 模式) ==>|
 *      |                           |<-- groupCallLeave(13) ------|  (B退出)
 *      |<-- participantNotify(15) -|   (leave 通知)              |
 *      |--- groupCallEnd(14) ----->|  (仅发起人可结束)            |
 *      |                           |--- participantNotify(15, ended) -->|
 *
 * 媒体架构：
 * - Mesh（≤8 人）：成员间 P2P 直连，offer/answer/ICE 走现有 RTC_SIGNAL/RtcGroup 扇出
 * - SFU（>8 人，支持 20+）：roomState 中携带 sfuToken/sfuUrl，客户端用 LiveKit SDK 连接 SFU 收发媒体
 *
 * @author gogym
 */
public class GroupCallExample {

    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        // ==================== 1. 发起群通话（客户端 A → 服务端） ====================
        // groupId 必填；payload 必须携带 callType（audio/video）；
        // inviteeIds 可选，为空时服务端邀请群内全部活跃成员
        GroupCallRequestDto request = new GroupCallRequestDto();
        request.setCallType("video");
        request.setInviteeIds(java.util.List.of("user-b", "user-c"));

        ImProto.RtcGroup groupCallRequest = ImProto.RtcGroup.newBuilder()
                .setSignalType(9)               // groupCallRequest
                .setSenderId("user-a")
                .setGroupId("group-001")
                .setCallId(java.util.UUID.randomUUID().toString()) // 客户端生成，服务端沿用
                .setPayload(GSON.toJson(request))
                .build();

        System.out.println("[A→S] groupCallRequest: "
                + PacketCodec.create(Cmd.RTC_GROUP, 1, groupCallRequest));

        // ==================== 2. 接受邀请（客户端 B → 服务端） ====================
        // roomId 来自服务端下发的 groupCallInvite(10) 信令
        ImProto.RtcGroup groupCallJoin = ImProto.RtcGroup.newBuilder()
                .setSignalType(11)              // groupCallJoin
                .setSenderId("user-b")
                .setGroupId("group-001")
                .setRoomId("room-uuid-from-invite")
                .build();
        System.out.println("[B→S] groupCallJoin: "
                + PacketCodec.create(Cmd.RTC_GROUP, 2, groupCallJoin));
        // 服务端回应 roomState(16)：房间快照 + 成员列表；
        // Mesh 模式附带 turnInfo（TURN/STUN 凭据），SFU 模式附带 sfuToken/sfuUrl

        // ==================== 3. 媒体信令（Mesh 模式，成员间） ====================
        // 加入后，客户端按房间快照对其他 joined 成员逐个建连：
        // offer/answer/ICE 可走 cmd=50 RTC_SIGNAL（点对点）或 cmd=51 RtcGroup（群扇出），
        // 与 1:1 通话的信令类型（1~8）完全一致，payload 为 SDP/ICE JSON
        ImProto.RtcSignal offer = ImProto.RtcSignal.newBuilder()
                .setSignalType(1)               // offer
                .setSenderId("user-b")
                .setReceiverId("user-a")
                .setCallId("call-uuid")
                .setPayload("{\"sdp\":\"v=0 ...\"}")
                .build();
        System.out.println("[B→S] offer(扇出至房间成员): "
                + PacketCodec.create(Cmd.RTC_SIGNAL, 3, offer));

        // ==================== 4. 退出 / 结束 ====================
        ImProto.RtcGroup leave = ImProto.RtcGroup.newBuilder()
                .setSignalType(13)              // groupCallLeave
                .setSenderId("user-b")
                .setGroupId("group-001")
                .setRoomId("room-uuid-from-invite")
                .setPayload("{\"reason\":\"hangup\"}")
                .build();
        System.out.println("[B→S] groupCallLeave: "
                + PacketCodec.create(Cmd.RTC_GROUP, 4, leave));

        ImProto.RtcGroup end = ImProto.RtcGroup.newBuilder()
                .setSignalType(14)              // groupCallEnd（仅发起人可调用）
                .setSenderId("user-a")
                .setGroupId("group-001")
                .setRoomId("room-uuid-from-invite")
                .build();
        System.out.println("[A→S] groupCallEnd: "
                + PacketCodec.create(Cmd.RTC_GROUP, 5, end));

        // ==================== 5. 服务端主动触发的信令 ====================
        // - 成员掉线：服务端通过 ConnectionCloseListener 清理并广播 participantNotify(15, leave/timeout)
        // - 邀请超时（invite-timeout-seconds）：RINGING 房间自动结束并广播 participantNotify(15, ended/timeout)
        // - 空房间回收（empty-room-ttl-seconds）：全员离开后延迟销毁房间
    }
}
