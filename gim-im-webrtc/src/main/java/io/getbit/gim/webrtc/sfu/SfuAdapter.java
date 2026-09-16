package io.getbit.gim.webrtc.sfu;

/**
 * SfuAdapter.java
 *
 * SFU 适配器 SPI（服务端媒体服务器扩展点）
 * 群通话 SFU 模式下，SDK 通过此接口与外部 SFU（如 LiveKit / Jitsi / mediasoup）交互：
 * 创建/销毁媒体房间、为成员签发接入凭证。
 * SDK 内置 LiveKitSfuAdapter 参考实现，使用方可实现此接口对接任意 SFU。
 *
 * @author gogym
 */
public interface SfuAdapter {

    /**
     * 创建 SFU 媒体房间（群通话创建时调用）
     * 实现方应保证幂等：房间已存在时不抛异常
     *
     * @param roomId 房间ID（与 GroupCallRoom.roomId 一致）
     */
    void createRoom(String roomId);

    /**
     * 销毁 SFU 媒体房间（群通话结束时调用）
     * 实现方应保证幂等：房间不存在时不抛异常
     *
     * @param roomId 房间ID
     */
    void destroyRoom(String roomId);

    /**
     * 为成员签发 SFU 接入凭证（成员加入群通话时调用）
     *
     * @param roomId 房间ID
     * @param userId 成员 userId
     * @return 接入凭证（token + 连接地址），客户端用它连接 SFU 收发媒体流
     */
    SfuToken issueToken(String roomId, String userId);
}
