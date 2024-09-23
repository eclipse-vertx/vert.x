/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import io.netty.channel.EventLoop;
import io.vertx.core.internal.EventExecutor;

/**
 * Execute events on an event-loop.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class EventLoopExecutor implements EventExecutor {

  final EventLoop eventLoop;

  public EventLoopExecutor(EventLoop eventLoop) {
    this.eventLoop = eventLoop;
  }

  public EventLoop eventLoop() {
    return eventLoop;
  }

  @Override
  public boolean inThread() {
    return eventLoop.inEventLoop();
  }

  @Override
  public void execute(Runnable command) {
    eventLoop.execute(command);
  }
}
