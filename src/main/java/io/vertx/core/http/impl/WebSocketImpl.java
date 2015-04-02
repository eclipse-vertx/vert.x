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

package io.vertx.core.http.impl;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public class WebSocketImpl extends WebSocketImplBase implements WebSocket {

  final Object metric;

  public WebSocketImpl(VertxInternal vertx,
                       ClientConnection conn, boolean supportsContinuation,
                       int maxWebSocketFrameSize) {
    super(vertx, conn, supportsContinuation, maxWebSocketFrameSize);
    metric = conn.metrics().connected(conn.metric(), this);
  }

  @Override
  public synchronized WebSocket handler(Handler<Buffer> handler) {
    if (handler != null) {
      checkClosed();
    }
    this.dataHandler = handler;
    return this;
  }

  @Override
  public synchronized WebSocket endHandler(Handler<Void> handler) {
    if (handler != null) {
      checkClosed();
    }
    this.endHandler = handler;
    return this;
  }

  @Override
  public synchronized WebSocket exceptionHandler(Handler<Throwable> handler) {
    if (handler != null) {
      checkClosed();
    }
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public WebSocket writeFrame(WebSocketFrame frame) {
    writeFrameInternal(frame);
    return this;
  }

  @Override
  public WebSocket writeMessage(Buffer data) {
    writeMessageInternal(data);
    return this;
  }

  @Override
  public synchronized WebSocket closeHandler(Handler<Void> handler) {
    checkClosed();
    this.closeHandler = handler;
    return this;
  }

  @Override
  public synchronized WebSocket frameHandler(Handler<WebSocketFrame> handler) {
    checkClosed();
    this.frameHandler = handler;
    return this;
  }

  @Override
  public synchronized WebSocket pause() {
    checkClosed();
    conn.doPause();
    return this;
  }

  @Override
  public synchronized WebSocket resume() {
    checkClosed();
    conn.doResume();
    return this;
  }

  @Override
  public synchronized WebSocket setWriteQueueMaxSize(int maxSize) {
    checkClosed();
    conn.doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public WebSocket write(Buffer data) {
    writeFrame(WebSocketFrame.binaryFrame(data, true));
    return this;
  }

  @Override
  public synchronized WebSocket drainHandler(Handler<Void> handler) {
    checkClosed();
    this.drainHandler = handler;
    return this;
  }

  @Override
  synchronized void handleClosed() {
    ((ClientConnection) conn).metrics().disconnected(metric);
    super.handleClosed();
  }
}
