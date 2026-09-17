package io.getbit.gim.webrtc.dto;

import lombok.Data;

/**
 * SingleCallCancelDto.java
 *
 * 1:1 通话取消呼叫 DTO
 * 用于 callCancel 信令（signalType=7）
 *
 * @author gogym
 */
@Data
public class SingleCallCancelDto {

    /**
     * 取消原因（如 cancel）
     */
    private String reason;
}
