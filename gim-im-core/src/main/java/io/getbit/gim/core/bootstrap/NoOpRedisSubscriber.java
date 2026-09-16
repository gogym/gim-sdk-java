package io.getbit.gim.core.bootstrap;

import io.getbit.gim.core.spi.ImRedisSubscriber;

/**
 * NoOpRedisSubscriber.java
 *
 * ImRedisSubscriber 空实现（单机模式默认占位，包级私有）
 *
 * @author gogym
 */
class NoOpRedisSubscriber implements ImRedisSubscriber {

    @Override
    public void subscribe(String channel, java.util.function.Consumer<String> callback) {
    }

    @Override
    public void unsubscribe() {
    }

    @Override
    public boolean isSubscribed() {
        return false;
    }
}
