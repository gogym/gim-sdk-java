package io.getbit.gim.core.spi;

/**
 * ConnectionCloseListener.java
 *
 * 连接关闭监听器 SPI（SDK 内部扩展点）
 * 用户全部设备离线（连接关闭链路）时触发，用于模块级资源清理。
 * 典型用途：WebRTC 群通话掉线清理（GroupCallSessionManager.onDisconnect）。
 *
 * 触发时机与 ImEventListener.onUserOffline 一致：用户最后一个在线设备断开时。
 *
 * @author gogym
 */
public interface ConnectionCloseListener {

    /**
     * 用户全部设备已离线时回调
     *
     * @param userId 离线用户ID
     */
    void onConnectionClosed(String userId);
}
