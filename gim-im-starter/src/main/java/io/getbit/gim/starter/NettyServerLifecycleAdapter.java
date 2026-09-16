package io.getbit.gim.starter;

import io.getbit.gim.core.bootstrap.StartContext;
import org.springframework.context.SmartLifecycle;

/**
 * NettyServerLifecycleAdapter.java
 * <p>
 * 将 core 的 StartContext 适配为 Spring SmartLifecycle
 * 使 IM 服务器（Netty + 集群路由）随 Spring 容器自动启停
 *
 * @author gogym
 */
public class NettyServerLifecycleAdapter implements SmartLifecycle {

    private final StartContext startContext;
    private volatile boolean running = false;

    public NettyServerLifecycleAdapter(StartContext startContext) {
        this.startContext = startContext;
    }

    @Override
    public void start() {
        startContext.start();
        running = true;
    }

    @Override
    public void stop() {
        startContext.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 较高 phase，确保在其他 Bean 之后启动、之前停止
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
