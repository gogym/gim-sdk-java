package io.getbit.gim.webrtc.util;

import com.google.gson.Gson;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.webrtc.dto.*;
import io.getbit.gim.webrtc.enums.RtcSignalType;
import lombok.extern.slf4j.Slf4j;

/**
 * RtcSignalValidator.java
 * <p>
 * WebRTC 信令 payload 校验器
 * 根据 signalType 使用对应 DTO 反序列化校验 payload 格式
 *
 * @author gogym
 */
@Slf4j
public class RtcSignalValidator {

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
     * 校验群通话生命周期信令（signalType 9~16）的 payload
     * 仅 groupCallRequest(9) 需携带 callType，其余信令允许空 payload（房间定位依赖 proto roomId 字段）
     *
     * @return true 校验通过，false 校验失败
     */
    public static boolean validateGroupLifecyclePayload(int signalType, String payload, String senderId) {
        RtcSignalType type = RtcSignalType.fromCode(signalType);
        if (type == null) {
            log.warn("RTC未知群通话信令类型: signalType={}, from={}", signalType, senderId);
            return false;
        }
        switch (type) {
            case GROUP_CALL_REQUEST:
                GroupCallRequestDto request = parseDto(payload, GroupCallRequestDto.class, signalType, senderId);
                return request != null && isNotBlank(request.getCallType());
            case GROUP_CALL_INVITE:
            case GROUP_CALL_JOIN:
            case GROUP_CALL_REJECT:
            case GROUP_CALL_LEAVE:
            case GROUP_CALL_END:
            case PARTICIPANT_NOTIFY:
            case ROOM_STATE:
                return true;
            default:
                log.warn("RTC非法群通话信令类型: signalType={}, from={}", signalType, senderId);
                return false;
        }
    }

    /**
     * 公共校验逻辑：根据 signalType 使用对应 DTO 反序列化校验 payload
     *
     * @return true 校验通过，false 校验失败
     */
    private static boolean validatePayload(String payload, int type, String senderId) {
        RtcSignalType signalType = RtcSignalType.fromCode(type);
        if (signalType == null) {
            log.warn("RTC未知信令类型: signalType={}, from={}", type, senderId);
            return false;
        }
        switch (signalType) {
            case OFFER:
            case ANSWER:
                WebRtcSdpDto sdp = parseDto(payload, WebRtcSdpDto.class, type, senderId);
                return sdp != null && isNotBlank(sdp.getSdp());
            case ICE_CANDIDATE:
                WebRtcIceCandidateDto ice = parseDto(payload, WebRtcIceCandidateDto.class, type, senderId);
                return ice != null && isNotBlank(ice.getCandidate())
                        && isNotBlank(ice.getSdpMid()) && ice.getSdpMLineIndex() != null;
            case CALL_REQUEST:
                WebRtcCallDto call = parseDto(payload, WebRtcCallDto.class, type, senderId);
                return call != null && isNotBlank(call.getCallType());
            case CALL_ACCEPT:
                // callAccept 无必需字段，允许空 payload
                return true;
            case CALL_REJECT:
                WebRtcRejectDto reject = parseDto(payload, WebRtcRejectDto.class, type, senderId);
                return reject != null && isNotBlank(reject.getReason());
            case CALL_CANCEL:
                WebRtcCancelDto cancel = parseDto(payload, WebRtcCancelDto.class, type, senderId);
                return cancel != null && isNotBlank(cancel.getReason());
            case CALL_HANGUP:
                WebRtcHangupDto hangup = parseDto(payload, WebRtcHangupDto.class, type, senderId);
                return hangup != null && isNotBlank(hangup.getReason());
            case MEDIA_STATE:
                // camera/mic 至少一项非 null（null 表示该项未变化）
                WebRtcMediaStateDto media = parseDto(payload, WebRtcMediaStateDto.class, type, senderId);
                return media != null && (media.getCamera() != null || media.getMic() != null);
            default:
                return false;
        }
    }

    /**
     * 使用 Gson 将 payload 反序列化为指定 DTO 类型
     *
     * @return DTO 实例，解析失败返回 null
     */
    private static <T> T parseDto(String payload, Class<T> clazz, int type, String senderId) {
        if (payload == null || payload.isEmpty()) {
            log.warn("RTC信令payload为空: signalType={}, from={}", type, senderId);
            return null;
        }
        try {
            T dto = GSON.fromJson(payload, clazz);
            if (dto == null) {
                log.warn("RTC信令payload解析为空: signalType={}, from={}", type, senderId);
            }
            return dto;
        } catch (Exception e) {
            log.warn("RTC信令payload解析失败: signalType={}, from={}, error={}",
                    type, senderId, e.getMessage());
            return null;
        }
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isEmpty();
    }
}
