package io.getbit.gim.webrtc.enums;

/**
 * GroupCallMode.java
 *
 * 群通话媒体架构模式
 *
 * @author gogym
 */
public enum GroupCallMode {

    /**
     * Mesh 模式：成员间 P2P 直连，服务端只做信令协调（适合小群，默认上限 meshMaxMembers）
     */
    MESH,

    /**
     * SFU 模式：媒体流由外部 SFU（如 LiveKit）承载，SDK 负责房间协调与 token 签发（适合大群，20+ 人）
     */
    SFU
}
