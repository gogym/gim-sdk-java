package io.getbit.gim.webrtc.util;

import com.google.gson.Gson;
import io.getbit.gim.webrtc.dto.*;
import io.getbit.gim.protocol.codec.ImProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RtcSignalValidator.java
 *
 * WebRTC 信令 payload 校验器
 * 根据 signalType 使用对应 DTO 反序列化校验 payload 格式
 *
 * @author gogym
 */
public class RtcSignalValidator {

    private static final Logger logger = LoggerFactory.getLogger(RtcSignalValidator.class);

    // signalType 枚举（与客户端 RtcSignalType 保持一致）
    private static final int SIGNAL_OFFER = 1;
    private static final int SIGNAL_ANSWER = 2;
    private static final int SIGNAL_ICE_CANDIDATE = 3;
    private static final int SIGNAL_CALL_REQUEST = 4;
    private static final int SIGNAL_CALL_ACCEPT = 5;
    private static final int SIGNAL_CALL_REJECT = 6;
    private static final int SIGNAL_CALL_CANCEL = 7;
    private static final int SIGNAL_CALL_HANGUP = 8;

    private static final Gson GSON = new Gson();

    /**
     * 校验 RtcSignal 信令的 payload
     *
     * @return true 校验通过，false 校验失败
     */
    public static boolean validatePayload(ImProto.RtcSignal signal) {
        return validatePayload(signal.getPayload(), signal.getSignalType(), signal.getSenderId());
    }

    /**
     * 校验 RtcGroup 信令的 payload（与 RtcSignal 使用相同的 DTO 校验规则）
     *
     * @return true 校验通过，false 校验失败
     */
    public static boolean validateGroupPayload(ImProto.RtcGroup groupSignal) {
        return validatePayload(groupSignal.getPayload(), groupSignal.getSignalType(), groupSignal.getSenderId());
    }

    /**
     * 公共校验逻辑：根据 signalType 使用对应 DTO 反序列化校验 payload
     *
     * @return true 校验通过，false 校验失败
     */
    private static boolean validatePayload(String payload, int type, String senderId) {
        return switch (type) {
            case SIGNAL_OFFER, SIGNAL_ANSWER -> {
                WebRtcSdpDto dto = parseDto(payload, WebRtcSdpDto.class, type, senderId);
                yield dto != null && isNotBlank(dto.getSdp());
            }
            case SIGNAL_ICE_CANDIDATE -> {
                WebRtcIceCandidateDto dto = parseDto(payload, WebRtcIceCandidateDto.class, type, senderId);
                yield dto != null && isNotBlank(dto.getCandidate())
                        && isNotBlank(dto.getSdpMid()) && dto.getSdpMLineIndex() != null;
            }
            case SIGNAL_CALL_REQUEST -> {
                WebRtcCallDto dto = parseDto(payload, WebRtcCallDto.class, type, senderId);
                yield dto != null && isNotBlank(dto.getCallType());
            }
            case SIGNAL_CALL_ACCEPT -> {
                // callAccept 无必需字段，允许空 payload
                yield true;
            }
            case SIGNAL_CALL_REJECT -> {
                WebRtcRejectDto dto = parseDto(payload, WebRtcRejectDto.class, type, senderId);
                yield dto != null && isNotBlank(dto.getReason());
            }
            case SIGNAL_CALL_CANCEL -> {
                WebRtcCancelDto dto = parseDto(payload, WebRtcCancelDto.class, type, senderId);
                yield dto != null && isNotBlank(dto.getReason());
            }
            case SIGNAL_CALL_HANGUP -> {
                WebRtcHangupDto dto = parseDto(payload, WebRtcHangupDto.class, type, senderId);
                yield dto != null && isNotBlank(dto.getReason());
            }
            default -> {
                logger.warn("RTC未知信令类型: signalType={}, from={}", type, senderId);
                yield false;
            }
        };
    }

    /**
     * 使用 Gson 将 payload 反序列化为指定 DTO 类型
     *
     * @return DTO 实例，解析失败返回 null
     */
    private static <T> T parseDto(String payload, Class<T> clazz, int type, String senderId) {
        if (payload == null || payload.isEmpty()) {
            logger.warn("RTC信令payload为空: signalType={}, from={}", type, senderId);
            return null;
        }
        try {
            T dto = GSON.fromJson(payload, clazz);
            if (dto == null) {
                logger.warn("RTC信令payload解析为空: signalType={}, from={}", type, senderId);
            }
            return dto;
        } catch (Exception e) {
            logger.warn("RTC信令payload解析失败: signalType={}, from={}, error={}",
                    type, senderId, e.getMessage());
            return null;
        }
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isEmpty();
    }
}
