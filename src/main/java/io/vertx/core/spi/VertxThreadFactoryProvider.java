/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi;

import java.util.concurrent.TimeUnit;

import io.vertx.core.impl.BlockedThreadChecker;
import io.vertx.core.impl.VertxThreadFactory;

/**
 * Extension point to allow creating custom {@code VertxThreadFactory} instances.
 */
public interface VertxThreadFactoryProvider {

    /**
     * Creates a new {@code VertxThreadFactory} with the following parameters.
     * @param prefix The prefix to be used for the thread name
     * @param checker The implementation which checks about blocked threads
     * @param worker Whether or not the threads created by the returned factory is a worker thread
     * @param maxExecTime The max execute time of the created threads
     * @param maxExecTimeUnit The time unit of the value of the max execute time of the created threads
     * @return the newly constructed VertxThreadFactory
     */
    VertxThreadFactory createVertxThreadFactory(String prefix, BlockedThreadChecker checker, boolean worker, long maxExecTime,
                                                TimeUnit maxExecTimeUnit);
    
}
