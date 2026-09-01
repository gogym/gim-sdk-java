package io.getbit.gim.webrtc;

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
