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

package org.vertx.java.tests.core.streams;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.WriteStream;

public class FakeWriteStream implements WriteStream<FakeWriteStream> {

  private int maxSize;
  private Buffer received = new Buffer();
  private Handler<Void> drainHandler;

  void clearReceived() {
    boolean callDrain = writeQueueFull();
    received = new Buffer();
    if (callDrain && drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  public FakeWriteStream setWriteQueueMaxSize(int maxSize) {
    this.maxSize = maxSize;
    return this;
  }

  public boolean writeQueueFull() {
    return received.length() >= maxSize;
  }

  public FakeWriteStream drainHandler(Handler<Void> handler) {
    this.drainHandler = handler;
    return this;
  }

  public FakeWriteStream write(Buffer data) {
    received.appendBuffer(data);
    return this;
  }

  public FakeWriteStream exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public void close() {
    // NOOP
  }

  int maxSize() {
    return maxSize;
  }

  Buffer received() {
    return received;
  }
}