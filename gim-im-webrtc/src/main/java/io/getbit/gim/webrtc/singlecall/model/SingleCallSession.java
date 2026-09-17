package io.getbit.gim.webrtc.singlecall.model;

import io.getbit.gim.webrtc.enums.SingleCallSessionStatus;
import io.netty.channel.Channel;
import lombok.Data;

@Data
public class SingleCallSession {
    private String callId;
    private String callerId;
    private String calleeId;
    private String callType;
    private SingleCallSessionStatus status;
    private Channel callerChannel;
    private Channel calleeChannel;
    private long createTime;
    private long connectTime;
    private long endTime;

    public long getDuration() {
        if (endTime > 0) {
            return endTime - connectTime;
        }
        if (connectTime > 0) {
            return System.currentTimeMillis() - connectTime;
        }
        return 0;
    }
    public long getDurationSeconds() { return getDuration() / 1000; }
    public boolean isParticipant(String userId) { return userId != null && (userId.equals(callerId) || userId.equals(calleeId)); }

    /**
     * 获取对方用户ID
     *
     * @param userId 本方用户ID（非参与者时返回 null）
     */
    public String getPeerId(String userId) {
        if (userId == null) {
            return null;
        }
        if (userId.equals(callerId)) {
            return calleeId;
        }
        if (userId.equals(calleeId)) {
            return callerId;
        }
        return null;
    }
    public Channel getChannelByUser(String userId) {
        if (userId == null) {
            return null;
        }
        if (userId.equals(callerId)) {
            return callerChannel;
        }
        if (userId.equals(calleeId)) {
            return calleeChannel;
        }
        return null;
    }
}
