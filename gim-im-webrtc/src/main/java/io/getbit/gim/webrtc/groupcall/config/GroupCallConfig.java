package io.getbit.gim.webrtc.groupcall.config;

import lombok.Getter;
import lombok.Setter;

/**
 * GroupCallConfig.java
 *
 * 群通话配置
 *
 * @author gogym
 */
@Getter
@Setter
public class GroupCallConfig {

    /**
     * 模式选择策略：auto（按人数自动选择）/ mesh / sfu
     */
    private String mode = "auto";

    /**
     * 单个群通话房间同时在线人数上限（发起人 + 受邀成员），超过则不再邀请/拒绝加入
     */
    private int maxMembers = 9;

    /**
     * Mesh 模式人数上限（auto 模式下超过该人数自动切换 SFU）
     */
    private int meshMaxMembers = 8;

    /**
     * 邀请超时时间（秒）：RINGING 房间超过该时长无人加入则自动结束
     */
    private int inviteTimeoutSeconds = 60;

    /**
     * 空房间回收时间（秒）：全员离开后延迟销毁房间，避免闪断重连导致房间丢失
     */
    private int emptyRoomTtlSeconds = 30;
}
