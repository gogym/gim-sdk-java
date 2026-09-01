package io.getbit.gim.webrtc.handler;

import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.message.handler.BaseHandler;
import io.getbit.gim.core.spi.ImGroupMemberProvider;
import io.getbit.gim.protocol.codec.Cmd;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.protocol.codec.PacketCodec;
import io.getbit.gim.webrtc.util.RtcSignalValidator;
import io.netty.channel.Channel;

import java.util.List;

/**
 * RtcGroupHandler.java
 *
 * WebRTC 群聊信令处理器（cmd=51）
 *
 * 职责分拆：
 * 1. signalType 1~8（媒体信令 offer/answer/ICE 等）：扇出转发给群内所有成员（排除发送者），
 *    Mesh 模式下成员间 P2P 建连使用
 * 2. signalType 9~16（群通话生命周期信令）：委派给 GroupCallService 处理
 *
 * 扇出策略：
 * 1. 解析 RtcGroup，获取群成员列表
 * 2. 为每个成员构建 RtcSignal（单聊信令），设置 toUserId = memberId
 * 3. 逐人路由：本地 → 直接投递，远程 → Redis（复用 RTC_SIGNAL 集群分支）
 * 4. 媒体信令离线直接丢弃（offer/ICE 为强时效信令，离线投递无意义且会污染客户端状态）
 *
 * @author gogym
 */
public class RtcGroupHandler extends BaseHandler {

    private final ImGroupMemberProvider groupMemberProvider;

    /**
     * 群通话生命周期服务（signalType 9~16），未启用群通话时为 null
     */
    private final GroupCallService groupCallService;

    public RtcGroupHandler(IMServerFacade facade,
                           ImGroupMemberProvider groupMemberProvider) {
        this(facade, groupMemberProvider, null);
    }

    public RtcGroupHandler(IMServerFacade facade,
                           ImGroupMemberProvider groupMemberProvider,
                           GroupCallService groupCallService) {
        super(facade);
        this.groupMemberProvider = groupMemberProvider;
        this.groupCallService = groupCallService;
    }

    @Override
    public int cmd() {
        return Cmd.RTC_GROUP;
    }

    @Override
    public void handle(ImProto.Packet packet, Channel channel, String userId) {
        try {
            ImProto.RtcGroup rtcGroup = PacketCodec.parseRtcGroup(packet);
            String groupId = rtcGroup.getGroupId();

            // 群通话生命周期信令（signalType 9~16）委派给 GroupCallService
            if (rtcGroup.getSignalType() >= GroupCallService.SIGNAL_GROUP_CALL_REQUEST) {
                if (groupCallService != null) {
                    groupCallService.handle(packet, channel, userId, rtcGroup);
                } else {
                    logger.debug("群通话未启用，忽略生命周期信令: signalType={}, userId={}",
                            rtcGroup.getSignalType(), userId);
                }
                return;
            }

            if (groupId.isEmpty()) {
                logger.warn("RTC群聊信令缺少群组ID: signalType={}, from={}", rtcGroup.getSignalType(), userId);
                return;
            }

            // 使用 DTO 反序列化校验 payload 格式
            if (!RtcSignalValidator.validateGroupPayload(rtcGroup)) {
                return;
            }

            // 获取群成员列表
            List<String> memberUserIds = groupMemberProvider.getGroupMemberUserIds(groupId);
            if (memberUserIds == null || memberUserIds.isEmpty()) {
                logger.warn("RTC群聊信令: 群 {} 无活跃成员", groupId);
                return;
            }

            int deliveredCount = 0;
            int offlineCount = 0;

            for (String memberId : memberUserIds) {
                // 跳过发送者
                if (memberId.equals(userId)) {
                    continue;
                }

                // 将群聊信令转换为单聊信令（设置 receiverId），复用现有路由基础设施
                ImProto.RtcSignal memberSignal = ImProto.RtcSignal.newBuilder()
                        .setSignalType(rtcGroup.getSignalType())
                        .setSenderId(rtcGroup.getSenderId())
                        .setReceiverId(memberId)
                        .setPayload(rtcGroup.getPayload())
                        .setCallId(rtcGroup.getCallId())
                        .build();
                ImProto.Packet fwdPacket = PacketCodec.create(Cmd.RTC_SIGNAL, 0, memberSignal);

                boolean delivered = routeToUser(memberId, fwdPacket);
                if (delivered) {
                    deliveredCount++;
                } else {
                    // 媒体信令为强时效信令，离线丢弃（不触发离线回调，避免过期 offer/ICE 污染客户端状态）
                    offlineCount++;
                }
            }

            logger.debug("RTC群聊信令路由完成: signalType={}, from={}, group={}, members={}, delivered={}, offline={}",
                    rtcGroup.getSignalType(), userId, groupId, memberUserIds.size(), deliveredCount, offlineCount);

        } catch (Exception e) {
            logger.error("RTC群聊信令处理失败, userId={}", userId, e);
        }
    }
}
