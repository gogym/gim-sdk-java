package io.getbit.gim.webrtc.dto;

import lombok.Data;

/**
 * GroupMemberInfoDto.java
 *
 * 群通话成员快照信息
 *
 * @author gogym
 */
@Data
public class GroupMemberInfoDto {

    private String userId;

    /**
     * 成员状态：invited / joined / left / rejected
     */
    private String status;

    /**
     * 摄像头开关状态（null 表示未上报）
     */
    private Boolean camera;

    /**
     * 麦克风开关状态（null 表示未上报）
     */
    private Boolean mic;

    public GroupMemberInfoDto() {
    }

    public GroupMemberInfoDto(String userId, String status) {
        this.userId = userId;
        this.status = status;
    }
}
