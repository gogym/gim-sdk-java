package io.getbit.gim.core.message.handler;

import io.getbit.gim.protocol.codec.ImProto;
import io.netty.channel.Channel;

import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DefaultMessageDispatcher.java
 *
 * 消息分发器 — 基于 Handler 自注册的 cmd 路由
 *
 * 设计：
 * - 所有 Handler 继承 BaseHandler，通过 cmd() 声明处理的指令类型
 * - 本类在构造期自动收集所有 Handler Bean，建立 cmd → Handler 映射
 * - 收到 Packet 时按 cmd 查找 Handler 并调用 handle()
 *
 * @author gogym
 */
@Slf4j
public class DefaultMessageDispatcher implements MessageDispatcher {


    /** cmd → Handler 映射（线程安全，支持动态注册） */
    private final Map<Integer, BaseHandler> cmdHandlerMap = new ConcurrentHashMap<>();

    public DefaultMessageDispatcher(List<BaseHandler> handlers) {
        for (BaseHandler handler : handlers) {
            registerHandler(handler);
        }
    }

    @Override
    public void registerHandler(BaseHandler handler) {
        cmdHandlerMap.put(handler.cmd(), handler);
        log.info("注册消息处理器: cmd={}, handler={}", handler.cmd(), handler.getClass().getSimpleName());
    }

    @Override
    public void dispatch(ImProto.Packet packet, Channel channel, String userId) {
        BaseHandler handler = cmdHandlerMap.get(packet.getCmd());
        if (handler != null) {
            handler.handle(packet, channel, userId);
        } else {
            log.warn("未找到处理器: cmd={}, userId={}", packet.getCmd(), userId);
        }
    }
}
