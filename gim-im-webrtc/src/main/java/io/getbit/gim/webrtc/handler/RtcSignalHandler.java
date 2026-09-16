package io.getbit.gim.webrtc.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.message.handler.BaseHandler;
import io.getbit.gim.protocol.codec.Cmd;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.protocol.codec.PacketCodec;
import io.getbit.gim.webrtc.sfu.TurnCredentialService;
import io.getbit.gim.webrtc.util.RtcSignalValidator;
import io.netty.channel.Channel;

import java.util.Map;

/**
 * RtcSignalHandler.java
 * WebRTC 单聊信令处理器
 * 在两端之间转发 WebRTC 信令（offer/answer/ICE candidate 等）
 * 通话建立信令（callRequest/callAccept）转发前注入 TURN 临时凭证，
 * 双方客户端据此构建 ICE 服务器（未注入时客户端回退公共 STUN，跨 NAT 场景无法连通）
 *
 * @author gogym
 */
public class RtcSignalHandler extends BaseHandler {

    /** 通话建立类信令类型（与客户端 RtcSignalType 保持一致，参见 RtcSignalValidator） */
    private static final int SIGNAL_CALL_REQUEST = 4;
    private static final int SIGNAL_CALL_ACCEPT = 5;

    /** TURN 凭证在 payload 中的字段名（客户端 parseTurnFromPayload 按此解析） */
    private static final String TURN_PAYLOAD_KEY = "turn";

    private static final Gson GSON = new Gson();

    /** TURN 凭证生成服务（可能未装配，为 null 时退化为纯转发） */
    private final TurnCredentialService turnCredentialService;

    public RtcSignalHandler(IMServerFacade facade) {
        this(facade, null);
    }

    public RtcSignalHandler(IMServerFacade facade, TurnCredentialService turnCredentialService) {
        super(facade);
        this.turnCredentialService = turnCredentialService;
    }

    @Override
    public int cmd() {
        return Cmd.RTC_SIGNAL;
    }

    @Override
    public void handle(ImProto.Packet packet, Channel channel, String userId) {
        try {
            ImProto.RtcSignal signal = PacketCodec.parseRtcSignal(packet);
            String targetId = signal.getReceiverId();

            if (targetId.isEmpty()) {
                logger.warn("RTC信令缺少目标用户: signalType={}, from={}", signal.getSignalType(), userId);
                return;
            }

            // 使用 DTO 反序列化校验 payload 格式
            if (!RtcSignalValidator.validatePayload(signal)) {
                return;
            }

            // 通话建立信令（callRequest/callAccept）转发前注入 TURN 临时凭证，
            // 被叫从 callRequest、主叫从 callAccept 各自解析凭证构建 ICE 服务器
            if (turnCredentialService != null
                    && (signal.getSignalType() == SIGNAL_CALL_REQUEST
                        || signal.getSignalType() == SIGNAL_CALL_ACCEPT)) {
                signal = injectTurnCredential(signal);
            }

            // 转发信令到目标用户（本地/远程）
            ImProto.Packet fwdPacket = PacketCodec.create(Cmd.RTC_SIGNAL, 0, signal);
            boolean delivered = routeToUser(targetId, fwdPacket);

            if (!delivered) {
                logger.debug("RTC信令目标用户离线: signalType={}, to={}", signal.getSignalType(), targetId);
                fireOfflineMessage(fwdPacket, targetId, "OFFLINE");
            }

            logger.debug("RTC信令转发: signalType={}, from={}, to={}, delivered={}",
                    signal.getSignalType(), userId, targetId, delivered);

        } catch (Exception e) {
            logger.error("RTC信令处理失败, userId={}", userId, e);
        }
    }

    /**
     * 向信令 payload 注入 TURN 临时凭证
     * payload 为空时新建对象，否则在原对象上追加 turn 字段；
     * 生成/注入失败时保持原 payload 转发（降级为客户端回退 STUN）
     *
     * @return 注入后的信令（protobuf 不可变，返回重建副本）
     */
    private ImProto.RtcSignal injectTurnCredential(ImProto.RtcSignal signal) {
        try {
            Map<String, Object> turnInfo = turnCredentialService.generateTurnInfo();
            if (turnInfo == null) {
                logger.warn("TURN凭证生成失败, 保持原payload转发: signalType={}", signal.getSignalType());
                return signal;
            }
            String originPayload = signal.getPayload();
            JsonObject payload = originPayload.isEmpty()
                    ? new JsonObject()
                    : JsonParser.parseString(originPayload).getAsJsonObject();
            payload.add(TURN_PAYLOAD_KEY, GSON.toJsonTree(turnInfo));
            logger.debug("TURN凭证已注入: signalType={}, turnUrl={}",
                    signal.getSignalType(), turnInfo.get("turnUrl"));
            return signal.toBuilder().setPayload(GSON.toJson(payload)).build();
        } catch (Exception e) {
            logger.warn("TURN凭证注入失败, 保持原payload转发: signalType={}, error={}",
                    signal.getSignalType(), e.getMessage());
            return signal;
        }
    }
}
