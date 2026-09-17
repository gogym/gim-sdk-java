package io.getbit.gim.webrtc.groupcall.model;

import io.getbit.gim.webrtc.enums.GroupCallMemberStatus;
import io.netty.channel.Channel;
import lombok.Data;

/**
 * GroupCallMember.java
 *
 * 群通话房间成员
 *
 * @author gogym
 */
@Data
public class GroupCallMember {

    private String userId;

    private GroupCallMemberStatus status;

    /**
     * 加入通话时间（毫秒，未加入为 0）
     */
    private long joinTime;

    /**
     * 摄像头开关状态（null 表示未上报，由 mediaState(100) 信令更新）
     */
    private volatile Boolean camera;

    /**
     * 麦克风开关状态（null 表示未上报，由 mediaState(100) 信令更新）
     */
    private volatile Boolean mic;

    /**
     * 成员最近一次绑定的在线连接（掉线后由 onDisconnect 清理）
     */
    private transient volatile Channel channel;

    public GroupCallMember(String userId, GroupCallMemberStatus status) {
        this.userId = userId;
        this.status = status;
    }

    public boolean isJoined() {
        return status == GroupCallMemberStatus.JOINED;
    }
}
