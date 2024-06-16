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

package io.vertx.benchmarks;

import io.vertx.core.spi.VertxThreadFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxExecutorService extends ThreadPoolExecutor {

  public VertxExecutorService(int maxThreads, String prefix) {
    super(maxThreads, maxThreads,
      0L, TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue<>(),
      r -> VertxThreadFactory.INSTANCE.newVertxThread(r, prefix, false, 10000, TimeUnit.NANOSECONDS));
  }
}
