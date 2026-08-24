package io.getbit.gim.protocol.codec;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

/**
 * PacketCodec.java
 *
 * @description: Packet 编解码工具类
 * 提供 Packet 的创建、Body 解析等便捷方法
 */
public class PacketCodec {

    private PacketCodec() {
    }

    // ====================== Packet 创建 ======================

    /**
     * 创建 Packet
     *
     * @param cmd      命令类型
     * @param sequence 客户端序号
     * @param body     业务消息体
     * @return Packet
     */
    public static ImProto.Packet create(int cmd, long sequence, GeneratedMessage body) {
        ImProto.Packet.Builder builder = ImProto.Packet.newBuilder()
                .setCmd(cmd)
                .setSequence(sequence)
                .setTimestamp(System.currentTimeMillis());

        if (body != null) {
            builder.setBody(body.toByteString());
        }

        return builder.build();
    }

    /**
     * 创建 Packet（带 requestId）
     *
     * @param cmd       命令类型
     * @param sequence  客户端序号
     * @param requestId 请求唯一ID
     * @param body      业务消息体
     * @return Packet
     */
    public static ImProto.Packet create(int cmd, long sequence, String requestId, GeneratedMessage body) {
        ImProto.Packet.Builder builder = ImProto.Packet.newBuilder()
                .setCmd(cmd)
                .setSequence(sequence)
                .setTimestamp(System.currentTimeMillis());

        if (requestId != null) {
            builder.setRequestId(requestId);
        }
        if (body != null) {
            builder.setBody(body.toByteString());
        }

        return builder.build();
    }

    /**
     * 创建无 body 的 Packet（如心跳）
     *
     * @param cmd 命令类型
     * @return Packet
     */
    public static ImProto.Packet createEmpty(int cmd) {
        return ImProto.Packet.newBuilder()
                .setCmd(cmd)
                .setTimestamp(System.currentTimeMillis())
                .build();
    }

    // ====================== Body 解析 ======================

    public static ImProto.BindRequest parseBindRequest(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.BindRequest.parseFrom(packet.getBody());
    }

    public static ImProto.ChatMessage parseChatMessage(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.ChatMessage.parseFrom(packet.getBody());
    }

    public static ImProto.ServerAck parseServerAck(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.ServerAck.parseFrom(packet.getBody());
    }

    public static ImProto.DeliveryAck parseDeliveryAck(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.DeliveryAck.parseFrom(packet.getBody());
    }

    public static ImProto.ReadReceipt parseReadReceipt(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.ReadReceipt.parseFrom(packet.getBody());
    }

    public static ImProto.MsgRecallRequest parseMsgRecallRequest(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.MsgRecallRequest.parseFrom(packet.getBody());
    }

    public static ImProto.MsgRecallNotify parseMsgRecallNotify(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.MsgRecallNotify.parseFrom(packet.getBody());
    }

    public static ImProto.RtcSignal parseRtcSignal(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.RtcSignal.parseFrom(packet.getBody());
    }

    public static ImProto.RtcGroup parseRtcGroup(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.RtcGroup.parseFrom(packet.getBody());
    }

    public static ImProto.Heartbeat parseHeartbeat(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.Heartbeat.parseFrom(packet.getBody());
    }

    public static ImProto.FriendRequestNotify parseFriendRequestNotify(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.FriendRequestNotify.parseFrom(packet.getBody());
    }

    public static ImProto.FriendStatusNotify parseFriendStatusNotify(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.FriendStatusNotify.parseFrom(packet.getBody());
    }

    public static ImProto.OnlineStatusNotify parseOnlineStatusNotify(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.OnlineStatusNotify.parseFrom(packet.getBody());
    }

    public static ImProto.GroupMemberNotify parseGroupMemberNotify(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.GroupMemberNotify.parseFrom(packet.getBody());
    }

    public static ImProto.GroupNotify parseGroupNotify(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.GroupNotify.parseFrom(packet.getBody());
    }

    public static ImProto.GroupJoinRequestNotify parseGroupJoinRequestNotify(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.GroupJoinRequestNotify.parseFrom(packet.getBody());
    }

    public static ImProto.KickNotify parseKickNotify(ImProto.Packet packet) throws InvalidProtocolBufferException {
        return ImProto.KickNotify.parseFrom(packet.getBody());
    }

    // ====================== 响应构建快捷方法 ======================

    public static ImProto.Packet buildBindResp(long sequence, String serverId) {
        ImProto.BindResponse body = ImProto.BindResponse.newBuilder()
                .setCode(0)
                .setMessage("success")
                .setServerId(serverId)
                .build();
        return create(Cmd.BIND_RESP, sequence, body);
    }

    public static ImProto.Packet buildBindFailResp(long sequence, int code, String message) {
        ImProto.BindResponse body = ImProto.BindResponse.newBuilder()
                .setCode(code)
                .setMessage(message != null ? message : "bind failed")
                .build();
        return create(Cmd.BIND_RESP, sequence, body);
    }

    public static ImProto.Packet buildHeartbeatResp(long sequence) {
        ImProto.HeartbeatResponse body = ImProto.HeartbeatResponse.newBuilder()
                .setServerTime(System.currentTimeMillis())
                .build();
        return create(Cmd.HEARTBEAT_RESP, sequence, body);
    }

    public static ImProto.Packet buildServerAck(String clientRequestId, String serverMsgId, long sequence) {
        ImProto.ServerAck body = ImProto.ServerAck.newBuilder()
                .setClientRequestId(clientRequestId != null ? clientRequestId : "")
                .setServerMsgId(serverMsgId != null ? serverMsgId : "")
                .setCode(0)
                .setServerTime(System.currentTimeMillis())
                .build();
        return create(Cmd.SERVER_ACK, sequence, body);
    }

    public static ImProto.Packet buildServerAckFail(String clientRequestId, int code, long sequence) {
        ImProto.ServerAck body = ImProto.ServerAck.newBuilder()
                .setClientRequestId(clientRequestId != null ? clientRequestId : "")
                .setCode(code)
                .setServerTime(System.currentTimeMillis())
                .build();
        return create(Cmd.SERVER_ACK, sequence, body);
    }

    // ====================== 通知消息构建 ======================

    public static ImProto.Packet buildFriendRequestNotifyPacket(String fromUserId, String toUserId,
                                                                 String nickname, String avatar, String message,
                                                                 String requestId, String logId) {
        ImProto.FriendRequestNotify.Builder builder = ImProto.FriendRequestNotify.newBuilder()
                .setFromUserId(fromUserId)
                .setToUserId(toUserId)
                .setMessage(message != null ? message : "")
                .setRequestId(requestId != null ? requestId : "")
                .setLogId(logId != null ? logId : "");
        if (nickname != null) { builder.setNickname(nickname); }
        if (avatar != null) { builder.setAvatar(avatar); }
        return create(Cmd.FRIEND_REQUEST_NOTIFY, 0, builder.build());
    }

    public static ImProto.Packet buildFriendStatusNotifyPacket(String userId, String toUserId, int status) {
        ImProto.FriendStatusNotify body = ImProto.FriendStatusNotify.newBuilder()
                .setUserId(userId)
                .setToUserId(toUserId)
                .setStatus(status)
                .build();
        return create(Cmd.FRIEND_STATUS_NOTIFY, 0, body);
    }

    public static ImProto.Packet buildOnlineStatusNotifyPacket(String userId, int status) {
        ImProto.OnlineStatusNotify body = ImProto.OnlineStatusNotify.newBuilder()
                .setUserId(userId)
                .setStatus(status)
                .build();
        return create(Cmd.ONLINE_STATUS_NOTIFY, 0, body);
    }

    public static ImProto.Packet buildGroupMemberNotifyPacket(String groupId, int action,
                                                               String userId, String operatorId) {
        ImProto.GroupMemberNotify body = ImProto.GroupMemberNotify.newBuilder()
                .setGroupId(groupId)
                .setAction(action)
                .setUserId(userId != null ? userId : "")
                .setOperatorId(operatorId != null ? operatorId : "")
                .build();
        return create(Cmd.GROUP_MEMBER_NOTIFY, 0, body);
    }

    public static ImProto.Packet buildGroupNotifyPacket(String groupId, int action,
                                                         String operatorId, String targetUserId,
                                                         String content) {
        ImProto.GroupNotify.Builder builder = ImProto.GroupNotify.newBuilder()
                .setGroupId(groupId)
                .setAction(action)
                .setOperatorId(operatorId != null ? operatorId : "");
        if (targetUserId != null) { builder.setTargetUserId(targetUserId); }
        if (content != null) { builder.setContent(content); }
        return create(Cmd.GROUP_NOTIFY, 0, builder.build());
    }

    public static ImProto.Packet buildGroupJoinRequestNotifyPacket(String groupId, String userId,
                                                                    String operatorId, int status,
                                                                    String message) {
        ImProto.GroupJoinRequestNotify.Builder builder = ImProto.GroupJoinRequestNotify.newBuilder()
                .setGroupId(groupId)
                .setUserId(userId != null ? userId : "")
                .setStatus(status);
        if (operatorId != null) { builder.setOperatorId(operatorId); }
        if (message != null) { builder.setMessage(message); }
        return create(Cmd.GROUP_JOIN_REQUEST_NOTIFY, 0, builder.build());
    }

    public static ImProto.Packet buildMsgRecallNotifyPacket(String msgId, String conversationId,
                                                              String operatorId, int chatType) {
        ImProto.MsgRecallNotify body = ImProto.MsgRecallNotify.newBuilder()
                .setMsgId(msgId != null ? msgId : "")
                .setConversationId(conversationId != null ? conversationId : "")
                .setOperatorId(operatorId != null ? operatorId : "")
                .setChatType(chatType)
                .build();
        return create(Cmd.MSG_RECALL_NOTIFY, 0, body);
    }

    public static ImProto.Packet buildRtcSignalPacket(int signalType, String fromUserId,
                                                       String toUserId, String callId, String payload) {
        ImProto.RtcSignal.Builder builder = ImProto.RtcSignal.newBuilder()
                .setSignalType(signalType)
                .setFromUserId(fromUserId != null ? fromUserId : "")
                .setToUserId(toUserId != null ? toUserId : "");
        if (callId != null) { builder.setCallId(callId); }
        if (payload != null) { builder.setPayload(payload); }
        return create(Cmd.RTC_SIGNAL, 0, builder.build());
    }

    public static ImProto.Packet buildRtcGroupPacket(int signalType, String fromUserId,
                                                      String groupId, String callId, String payload) {
        ImProto.RtcGroup.Builder builder = ImProto.RtcGroup.newBuilder()
                .setSignalType(signalType)
                .setFromUserId(fromUserId != null ? fromUserId : "")
                .setGroupId(groupId != null ? groupId : "");
        if (callId != null) { builder.setCallId(callId); }
        if (payload != null) { builder.setPayload(payload); }
        return create(Cmd.RTC_GROUP, 0, builder.build());
    }

    public static ImProto.Packet buildKickNotify(int code, String message) {
        ImProto.KickNotify body = ImProto.KickNotify.newBuilder()
                .setCode(code)
                .setMessage(message != null ? message : "")
                .build();
        return create(Cmd.KICK_NOTIFY, 0, body);
    }

    // ====================== JSON 反序列化 ======================

    public static ImProto.Packet packetFromJson(String json) throws InvalidProtocolBufferException {
        ImProto.Packet.Builder builder = ImProto.Packet.newBuilder();
        JsonFormat.parser().merge(json, builder);
        return builder.build();
    }

    // ====================== 通用解析 ======================

    public static GeneratedMessage parseBody(ImProto.Packet packet) throws InvalidProtocolBufferException {
        if (packet.getBody() == null || packet.getBody().isEmpty()) {
            return null;
        }

        return switch (packet.getCmd()) {
            case Cmd.BIND_REQ -> ImProto.BindRequest.parseFrom(packet.getBody());
            case Cmd.BIND_RESP -> ImProto.BindResponse.parseFrom(packet.getBody());
            case Cmd.HEARTBEAT_REQ -> ImProto.Heartbeat.parseFrom(packet.getBody());
            case Cmd.HEARTBEAT_RESP -> ImProto.HeartbeatResponse.parseFrom(packet.getBody());
            case Cmd.SINGLE_CHAT_MSG, Cmd.GROUP_CHAT_MSG -> ImProto.ChatMessage.parseFrom(packet.getBody());
            case Cmd.SERVER_ACK -> ImProto.ServerAck.parseFrom(packet.getBody());
            case Cmd.DELIVERY_ACK -> ImProto.DeliveryAck.parseFrom(packet.getBody());
            case Cmd.READ_RECEIPT -> ImProto.ReadReceipt.parseFrom(packet.getBody());
            case Cmd.MSG_RECALL_REQ -> ImProto.MsgRecallRequest.parseFrom(packet.getBody());
            case Cmd.MSG_RECALL_NOTIFY -> ImProto.MsgRecallNotify.parseFrom(packet.getBody());
            case Cmd.ONLINE_STATUS_NOTIFY -> ImProto.OnlineStatusNotify.parseFrom(packet.getBody());
            case Cmd.FRIEND_REQUEST_NOTIFY -> ImProto.FriendRequestNotify.parseFrom(packet.getBody());
            case Cmd.FRIEND_STATUS_NOTIFY -> ImProto.FriendStatusNotify.parseFrom(packet.getBody());
            case Cmd.GROUP_MEMBER_NOTIFY -> ImProto.GroupMemberNotify.parseFrom(packet.getBody());
            case Cmd.GROUP_NOTIFY -> ImProto.GroupNotify.parseFrom(packet.getBody());
            case Cmd.GROUP_JOIN_REQUEST_NOTIFY -> ImProto.GroupJoinRequestNotify.parseFrom(packet.getBody());
            case Cmd.RTC_SIGNAL -> ImProto.RtcSignal.parseFrom(packet.getBody());
            case Cmd.RTC_GROUP -> ImProto.RtcGroup.parseFrom(packet.getBody());
            case Cmd.KICK_NOTIFY -> ImProto.KickNotify.parseFrom(packet.getBody());
            default -> null;
        };
    }
}
