package io.getbit.gim.webrtc.dto;

import lombok.Data;

/**
 * SingleCallHangupDto.java
 *
 * 1:1 通话挂断 DTO
 * 用于 callHangup 信令（signalType=8）
 *
 * @author gogym
 */
@Data
public class SingleCallHangupDto {

    /**
     * 挂断原因
     * normal-正常挂断, timeout-超时, error-错误
     */
    private String reason;
}
