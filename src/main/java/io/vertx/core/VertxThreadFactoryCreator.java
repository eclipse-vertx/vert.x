package io.vertx.core;

import java.util.concurrent.TimeUnit;

import io.vertx.core.impl.BlockedThreadChecker;
import io.vertx.core.impl.VertxThreadFactory;

/**
 * Extension point to allow creating custom {@code VertxThreadFactory} instances.
 */
public interface VertxThreadFactoryCreator {

    /**
     * Creates a new {@code VertxThreadFactory} with the following parameters.
     * @param prefix
     * @param checker
     * @param worker
     * @param maxExecTime
     * @param maxExecTimeUnit
     * @return
     */
    VertxThreadFactory createVertxThreadFactory(String prefix, BlockedThreadChecker checker, boolean worker, long maxExecTime,
                                                TimeUnit maxExecTimeUnit);
    
}
