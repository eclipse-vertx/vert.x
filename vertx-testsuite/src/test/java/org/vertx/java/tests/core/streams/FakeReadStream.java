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
import org.vertx.java.core.streams.ReadStream;

public class FakeReadStream implements ReadStream<FakeReadStream> {

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;

  private boolean paused;
  private int pauseCount;
  private int resumeCount;

  void addData(Buffer data) {
    if (dataHandler != null) {
      dataHandler.handle(data);
    }
  }

  boolean isPaused() {
    return paused;
  }

  int pauseCount() {
    return pauseCount;
  }

  int resumeCount() {
    return resumeCount;
  }

  public FakeReadStream dataHandler(Handler<Buffer> handler) {
    this.dataHandler = handler;
    return this;
  }

  public FakeReadStream pause() {
    paused = true;
    pauseCount++;
    return this;
  }

  public FakeReadStream resume() {
    paused = false;
    resumeCount++;
    return this;
  }

  public FakeReadStream exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  public FakeReadStream endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  public void end() {
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }
}