package io.getbit.gim.core.connection.server;

import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.protocol.codec.*;
import io.getbit.gim.core.connection.channel.ConnectionInfo;
import io.getbit.gim.core.connection.auth.ConnectionAuthHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChatServerHandler.java
 *
 * 聊天服务处理器
 *
 * 核心逻辑：
 * 1. channelActive: 连接建立，仅添加到全局通道组
 * 2. channelRead0: 按 cmd 分发
 *    - 未认证时：只接受 BIND_REQ，否则拒绝
 *    - 已认证后：按 cmd 路由到对应业务处理
 * 3. userEventTriggered: 空闲超时断开
 * 4. channelInactive: 解绑通道
 *
 * @author gogym
 */
public class ChatServerHandler extends SimpleChannelInboundHandler<ImProto.Packet> {

    private static final Logger logger = LoggerFactory.getLogger(ChatServerHandler.class);

    private final IMServerFacade facade;
    private final ConnectionAuthHandler authHandler;

    public ChatServerHandler(IMServerFacade facade, ConnectionAuthHandler authHandler) {
        this.facade = facade;
        this.authHandler = authHandler;
    }

    /**
     * 通道激活 - 客户端连接成功
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        logger.info("[{}] 新连接建立, 等待认证 ({}s 超时)",
                channel.id().asShortText(), ConnectionAuthHandler.AUTH_TIMEOUT);
    }

    /**
     * 读取消息 - 按 cmd 分发
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ImProto.Packet packet) throws Exception {
        Channel channel = ctx.channel();
        int cmd = packet.getCmd();

        // 未认证状态：只接受 BIND_REQ
        if (!authHandler.isAuthenticated(channel)) {
            if (cmd == Cmd.BIND_REQ) {
                boolean success = authHandler.handleBind(packet, channel);
                if (!success) {
                    channel.close();
                } else {
                    // 绑定成功，触发上线事件（身份信息已由 handleBind 登记到 ConnectionInfo）
                    ConnectionInfo connInfo = facade.getChannelManager().getConnectionInfo(channel.id().asLongText());
                    if (connInfo != null) {
                        facade.fireUserOnline(connInfo.userId(), connInfo.device());
                    }
                }
            } else {
                logger.warn("[{}] 未认证连接发送了 cmd={}, 拒绝", channel.id().asShortText(), cmd);
                channel.close();
            }
            return;
        }

        // 已认证状态：按 cmd 路由
        ConnectionInfo connInfo = facade.getChannelManager().getConnectionInfo(channel.id().asLongText());
        // 连接档案不存在说明已被互踢替换或解绑，不再处理任何消息
        if (connInfo == null) {
            return;
        }
        String userId = connInfo.userId();

        // 已认证连接再次收到 BIND_REQ：客户端重连/网络切换导致的重复绑定
        // 回复 BIND_RESP 确认连接仍有效，避免客户端因等待响应超时而新建连接触发互踢
        if (cmd == Cmd.BIND_REQ) {
            authHandler.handleRebind(packet, channel);
            return;
        }

        facade.getMessageDispatcher().dispatch(packet, channel, userId);
    }

    /**
     * 空闲超时事件
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleEvent) {
            if (idleEvent.state() == IdleState.READER_IDLE) {
                ConnectionInfo connInfo = facade.getChannelManager().getConnectionInfo(ctx.channel().id().asLongText());
                logger.info("[{}] 读超时, userId={}, 断开连接",
                        ctx.channel().id().asShortText(), connInfo != null ? connInfo.userId() : null);
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 通道失活 - 解绑用户通道
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();

        ConnectionInfo connInfo = facade.getChannelManager().unbindByChannelId(channel.id().asLongText());

        if (connInfo != null) {
            String userId = connInfo.userId();
            DeviceType device = connInfo.device();

            logger.info("[{}] 用户断开, userId={}, device={}",
                    channel.id().asShortText(), userId, device);

            // 检查用户是否还有其他在线设备
            var remainingChannels = facade.getChannelManager().getChannels(userId);
            if (remainingChannels.isEmpty()) {
                facade.getUserRouteService().unregister(userId);
                facade.fireUserOffline(userId);
            }
            return;
        }

        // 连接未登记或已提前解绑（被新连接替换/服务端踢人），无需重复清理，仅记录日志
        if (Boolean.TRUE.equals(channel.attr(ConnectionAuthHandler.KICKED_BY_NEW_KEY).get())) {
            logger.info("[{}] 被新连接替换下线（userId/device 详见互踢日志）", channel.id().asShortText());
        } else {
            logger.info("[{}] 连接已解绑或未认证即断开", channel.id().asShortText());
        }
    }

    /**
     * 异常捕获
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String channelId = ctx.channel().id().asShortText();
        ConnectionInfo connInfo = facade.getChannelManager().getConnectionInfo(ctx.channel().id().asLongText());
        String userId = connInfo != null ? connInfo.userId() : null;

        if (cause instanceof DecoderException) {
            logger.warn("[{}] 协议解码失败, userId={}, 断开连接. 原因: {}",
                    channelId, userId, cause.getMessage());
        } else {
            logger.error("[{}] 通道异常, userId={}", channelId, userId, cause);
        }
        ctx.close();
    }
}
