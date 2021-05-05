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
package io.vertx.core.parsetools;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.streams.ReadStream;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class FakeStream implements ReadStream<Buffer> {

  private long demand = Long.MAX_VALUE;
  private Handler<Buffer> eventHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private volatile int pauseCount;
  private volatile int resumeCount;

  @Override
  public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public ReadStream<Buffer> handler(Handler<Buffer> handler) {
    eventHandler = handler;
    return this;
  }

  @Override
  public ReadStream<Buffer> fetch(long amount) {
    Arguments.require(amount > 0, "Fetch amount must be > 0L");
    demand += amount;
    if (demand < 0L) {
      demand = Long.MAX_VALUE;
    }
    return this;
  }

  @Override
  public ReadStream<Buffer> pause() {
    demand = 0L;
    pauseCount++;
    return this;
  }

  @Override
  public ReadStream<Buffer> resume() {
    resumeCount++;
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public ReadStream<Buffer> endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  boolean isPaused() {
    return demand == 0L;
  }

  void handle(String s) {
    handle(Buffer.buffer(s));
  }

  void handle(Buffer buff) {
    if (demand == 0L) {
      throw new IllegalStateException();
    }
    if (demand != Long.MAX_VALUE) {
      demand--;
    }
    eventHandler.handle(buff);
  }

  void fail(Throwable err) {
    exceptionHandler.handle(err);
  }

  void end() {
    endHandler.handle(null);
  }

  public int pauseCount() {
    return pauseCount;
  }

  public int resumeCount() {
    return resumeCount;
  }
}
