package io.getbit.gim.webrtc.util;

import com.google.gson.Gson;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.webrtc.dto.*;
import io.getbit.gim.webrtc.enums.GroupSignalType;
import io.getbit.gim.webrtc.enums.SingleSignalType;
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
     * 校验群通话生命周期信令（signalType 9~17）的 payload
     * groupCallRequest(9) 需携带 callType，mediaState(17) 需携带 camera/mic 至少一项，
     * 其余信令允许空 payload（房间定位依赖 proto roomId 字段）
     *
     * @return true 校验通过，false 校验失败
     */
    public static boolean validateGroupLifecyclePayload(int signalType, String payload, String senderId) {
        GroupSignalType type = GroupSignalType.fromCode(signalType);
        if (type == null) {
            log.warn("RTC未知群通话信令类型: signalType={}, from={}", signalType, senderId);
            return false;
        }
        switch (type) {
            case GROUP_CALL_REQUEST:
                GroupCallRequestDto request = parseDto(payload, GroupCallRequestDto.class, signalType, senderId);
                return request != null && isNotBlank(request.getCallType());
            case MEDIA_STATE:
                // 群内成员媒体开关：camera/mic 至少一项非 null（null 表示该项未变化）
                GroupMediaStateDto media = parseDto(payload, GroupMediaStateDto.class, signalType, senderId);
                return media != null && (media.getCamera() != null || media.getMic() != null);
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
     * 公共校验逻辑（1:1 通话与群内媒体信令 1~8）：根据 signalType 使用对应 DTO 反序列化校验 payload
     *
     * @return true 校验通过，false 校验失败
     */
    private static boolean validatePayload(String payload, int type, String senderId) {
        SingleSignalType signalType = SingleSignalType.fromCode(type);
        if (signalType == null) {
            log.warn("RTC未知信令类型: signalType={}, from={}", type, senderId);
            return false;
        }
        switch (signalType) {
            case OFFER:
            case ANSWER:
                RtcSdpDto sdp = parseDto(payload, RtcSdpDto.class, type, senderId);
                return sdp != null && isNotBlank(sdp.getSdp());
            case ICE_CANDIDATE:
                RtcIceCandidateDto ice = parseDto(payload, RtcIceCandidateDto.class, type, senderId);
                return ice != null && isNotBlank(ice.getCandidate())
                        && isNotBlank(ice.getSdpMid()) && ice.getSdpMLineIndex() != null;
            case CALL_REQUEST:
                SingleCallRequestDto call = parseDto(payload, SingleCallRequestDto.class, type, senderId);
                return call != null && isNotBlank(call.getCallType());
            case CALL_ACCEPT:
                // callAccept 无必需字段，允许空 payload
                return true;
            case CALL_REJECT:
                SingleCallRejectDto reject = parseDto(payload, SingleCallRejectDto.class, type, senderId);
                return reject != null && isNotBlank(reject.getReason());
            case CALL_CANCEL:
                SingleCallCancelDto cancel = parseDto(payload, SingleCallCancelDto.class, type, senderId);
                return cancel != null && isNotBlank(cancel.getReason());
            case CALL_HANGUP:
                SingleCallHangupDto hangup = parseDto(payload, SingleCallHangupDto.class, type, senderId);
                return hangup != null && isNotBlank(hangup.getReason());
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
