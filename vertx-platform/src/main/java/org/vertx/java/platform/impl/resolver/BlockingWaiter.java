/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BlockingWaiter<Result> {
  private static final Logger log = LoggerFactory.getLogger(BlockingWaiter.class);

  private CountDownLatch latch;
  private Result result;


  public BlockingWaiter() {
    latch = new CountDownLatch(1);
  }

  public Result await(long timeout, TimeUnit timeUnit) throws TimeoutException, InterruptedException {
    if (!latch.await(timeout, timeUnit)) {
      throw new TimeoutException("Timed out waiting for result after " + timeout + timeUnit);
    }
    return result;
  }

  public Result await(long timeout, TimeUnit timeUnit, Result defaultValue) {
    try {
      if (!latch.await(timeout, timeUnit)) {
        throw new TimeoutException("Timed out waiting for result after " + timeout + timeUnit);
      }
    } catch (InterruptedException | TimeoutException e) {
      log.warn("Assuming default value for await result", e);
      result = defaultValue;
    }
    return result;
  }

  public void end(Result res) {
    result = res;
    latch.countDown();
  }
}
