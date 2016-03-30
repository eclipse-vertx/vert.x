package io.vertx.core.logging;

import io.vertx.core.spi.logging.LogDelegate;
import io.vertx.core.spi.logging.LogDelegateFactory;

public class Log4j2LogDelegateFactory implements LogDelegateFactory {
    @Override
    public LogDelegate createDelegate(final String name) {
        return new Log4j2LogDelegate(name);
    }
}