package io.getbit.gim.core.connection.server;

import io.getbit.gim.core.bootstrap.GimLifecycle;
import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.config.properties.GimProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NettyServer.java
 * <p>
 * Netty服务器启动器
 * 实现 GimLifecycle，支持手动启停
 *
 * @author gogym
 */
@Slf4j
public class NettyServer implements GimLifecycle {

    private final GimProperties config;
    private final IMServerFacade facade;

    private volatile boolean running = false;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyServer(GimProperties config, IMServerFacade facade) {
        this.config = config;
        this.facade = facade;
    }

    /**
     * 启动 Netty 服务器（非阻塞，在独立线程中运行）
     */
    @Override
    public void start() {
        int port = config.getNetty().getPort();
        int bossThreads = config.getNetty().getBossThreads();
        int workerThreads = config.getNetty().getWorkerThreads();
        int backlog = config.getNetty().getBacklog();

        // 计算worker线程数：0表示自动检测CPU核心数
        int actualWorkerThreads = workerThreads <= 0
                ? Runtime.getRuntime().availableProcessors() * 2
                : workerThreads;

        log.info("Starting Netty Server - Port: {}, Boss: {}, Worker: {}",
                port, bossThreads, actualWorkerThreads);

        bossGroup = new NioEventLoopGroup(bossThreads,
                new ThreadFactory() {
                    private final AtomicInteger index = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "netty-boss-" + index.incrementAndGet());
                    }
                });

        workerGroup = new NioEventLoopGroup(actualWorkerThreads,
                new ThreadFactory() {
                    private final AtomicInteger index = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "netty-worker-" + index.incrementAndGet());
                    }
                });

        Thread serverThread = new Thread(() -> {
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new GimServerInitializer(facade, facade.getAuthHandler()))
                        .option(ChannelOption.SO_BACKLOG, backlog)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                                new WriteBufferWaterMark(32 * 1024, 256 * 1024))
                        .childOption(ChannelOption.SO_SNDBUF, 64 * 1024)
                        .childOption(ChannelOption.SO_RCVBUF, 64 * 1024);

                ChannelFuture f = b.bind(port).sync();
                serverChannel = f.channel();
                log.info("Netty server started, listening on port: {}", port);
                running = true;
                serverChannel.closeFuture().sync();

            } catch (Exception e) {
                log.error("Netty server startup failed", e);
                throw new RuntimeException(e);
            } finally {
                shutdownGroups();
            }
        }, "netty-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /**
     * 停止 Netty 服务器（优雅关闭）
     */
    @Override
    public void stop() {
        log.info("Stopping Netty Server...");
        running = false;
        if (serverChannel != null && serverChannel.isActive()) {
            try {
                serverChannel.close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("关闭 serverChannel 被中断", e);
            }
        }
        shutdownGroups();
        log.info("Netty Server stopped");
    }

    private void shutdownGroups() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
