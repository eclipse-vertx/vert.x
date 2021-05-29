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
package io.vertx.core.net.impl.pool;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SemaphoreExecutor<S> implements Executor<S> {

  private final Lock lock = new ReentrantLock();
  private final S state;

  public SemaphoreExecutor(S state) {
    this.state = state;
  }

  @Override
  public void submit(Action<S> action) {
    lock.lock();
    Runnable post = null;
    try {
      post = action.execute(state);
    } finally {
      lock.unlock();
      if (post != null) {
        post.run();
      }
    }
  }
}
