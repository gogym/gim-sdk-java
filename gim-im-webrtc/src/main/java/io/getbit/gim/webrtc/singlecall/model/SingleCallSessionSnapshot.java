package io.getbit.gim.webrtc.singlecall.model;

import lombok.Data;

/**
 * SingleCallSessionSnapshot.java
 *
 * 1:1 通话会话的 Redis 序列化快照
 * 刻意排除 {@link SingleCallSession} 中的 Channel 等节点本地字段，
 * 仅保留可跨节点还原的会话元数据（status 以枚举名字符串存储）
 *
 * @author gogym
 */
@Data
public class SingleCallSessionSnapshot {

    private String callId;
    private String callerId;
    private String calleeId;
    private String callType;
    private String status;
    private long createTime;
    private long connectTime;
    private long endTime;
}
