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

  private Object metric;

  public WebSocketImpl(VertxInternal vertx,
                       ClientConnection conn, boolean supportsContinuation,
                       int maxWebSocketFrameSize) {
    super(vertx, conn, supportsContinuation, maxWebSocketFrameSize);
  }

  @Override
  public WebSocket handler(Handler<Buffer> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkClosed();
      }
      this.dataHandler = handler;
      return this;
    }
  }

  @Override
  public WebSocket endHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkClosed();
      }
      this.endHandler = handler;
      return this;
    }
  }

  @Override
  public WebSocket exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkClosed();
      }
      this.exceptionHandler = handler;
      return this;
    }
  }

  @Override
  public WebSocket writeFrame(WebSocketFrame frame) {
    writeFrameInternal(frame);
    return this;
  }

  @Override
  public WebSocket writeFinalTextFrame(String text) {
    return writeFrame(WebSocketFrame.textFrame(text, true));
  }

  @Override
  public WebSocket writeFinalBinaryFrame(Buffer data) {
    return writeFrame(WebSocketFrame.binaryFrame(data, true));
  }

  @Override
  public WebSocket writeBinaryMessage(Buffer data) {
    writeMessageInternal(data);
    return this;
  }

  @Override
  public WebSocket closeHandler(Handler<Void> handler) {
    synchronized (conn) {
      checkClosed();
      this.closeHandler = handler;
      return this;
    }
  }

  @Override
  public WebSocket frameHandler(Handler<WebSocketFrame> handler) {
    synchronized (conn) {
      checkClosed();
      this.frameHandler = handler;
      return this;
    }
  }

  @Override
  public WebSocket pause() {
    synchronized (conn) {
      checkClosed();
      conn.doPause();
      return this;
    }
  }

  @Override
  public WebSocket resume() {
    synchronized (conn) {
      checkClosed();
      conn.doResume();
      return this;
    }
  }

  @Override
  public WebSocket setWriteQueueMaxSize(int maxSize) {
    synchronized (conn) {
      checkClosed();
      conn.doSetWriteQueueMaxSize(maxSize);
      return this;
    }
  }

  @Override
  public WebSocket write(Buffer data) {
    writeFrame(WebSocketFrame.binaryFrame(data, true));
    return this;
  }

  @Override
  public WebSocket drainHandler(Handler<Void> handler) {
    synchronized (conn) {
      checkClosed();
      this.drainHandler = handler;
      return this;
    }
  }

  @Override
  void handleClosed() {
    synchronized (conn) {
      ((ClientConnection) conn).metrics().disconnected(metric);
      super.handleClosed();
    }
  }

  void setMetric(Object metric) {
    synchronized (conn) {
      this.metric = metric;
    }
  }
}
