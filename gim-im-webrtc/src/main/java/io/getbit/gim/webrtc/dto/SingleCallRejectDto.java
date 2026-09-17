package io.getbit.gim.webrtc.dto;

import lombok.Data;

/**
 * SingleCallRejectDto.java
 *
 * 1:1 通话拒绝 DTO
 * 用于 callReject 信令（signalType=6）
 * 群通话 reject/leave 的 reason 解析亦复用此 DTO
 *
 * @author gogym
 */
@Data
public class SingleCallRejectDto {

    /**
     * 拒绝原因（如 reject、busy）
     */
    private String reason;
}
