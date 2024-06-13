/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.impl.core.spi;

import io.vertx.impl.core.VertxBootstrapImpl;
import io.vertx.impl.core.VertxThread;
import io.vertx.core.spi.VertxBootstrap;
import io.vertx.core.spi.VertxServiceProvider;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface VertxThreadFactory extends VertxServiceProvider {

  VertxThreadFactory INSTANCE = new VertxThreadFactory() {
  };

  @Override
  default void init(VertxBootstrap builder) {
    VertxBootstrapImpl bootstrap = (VertxBootstrapImpl) builder;
    if (bootstrap.threadFactory() == null) {
      bootstrap.threadFactory(this);
    }
  }

  default VertxThread newVertxThread(Runnable target, String name, boolean worker, long maxExecTime, TimeUnit maxExecTimeUnit) {
    return new VertxThread(target, name, worker, maxExecTime, maxExecTimeUnit);
  }
}
