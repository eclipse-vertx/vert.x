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
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ConnectionBase;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;

/**
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public class ServerWebSocketImpl extends WebSocketImplBase implements ServerWebSocket {

  private final String uri;
  private final String path;
  private final String query;
  private final Runnable connectRunnable;
  private final MultiMap headers;
  private Object metric;

  private boolean connected;
  private boolean rejected;

  public ServerWebSocketImpl(VertxInternal vertx, String uri, String path, String query, MultiMap headers,
                             ConnectionBase conn, boolean supportsContinuation, Runnable connectRunnable,
                             int maxWebSocketFrameSize) {
    super(vertx, conn, supportsContinuation, maxWebSocketFrameSize);
    this.uri = uri;
    this.path = path;
    this.query = query;
    this.headers = headers;
    this.connectRunnable = connectRunnable;
  }

  @Override
  public String uri() {
    return uri;
  }

  @Override
  public String path() {
    return path;
  }

  @Override
  public String query() {
    return query;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public void reject() {
    synchronized (conn) {
      checkClosed();
      if (connectRunnable == null) {
        throw new IllegalStateException("Cannot reject websocket on the client side");
      }
      if (connected) {
        throw new IllegalStateException("Cannot reject websocket, it has already been written to");
      }
      rejected = true;
    }
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.getPeerCertificateChain();
  }

  @Override
  public void close() {
    synchronized (conn) {
      checkClosed();
      if (connectRunnable != null) {
        // Server side
        if (rejected) {
          throw new IllegalStateException("Cannot close websocket, it has been rejected");
        }
        if (!connected && !closed) {
          connect();
        }
      }
      super.close();
    }
  }

  @Override
  public ServerWebSocket handler(Handler<Buffer> handler) {
    synchronized (conn) {
      checkClosed();
      this.dataHandler = handler;
      return this;
    }
  }

  @Override
  public ServerWebSocket endHandler(Handler<Void> handler) {
    synchronized (conn) {
      checkClosed();
      this.endHandler = handler;
      return this;
    }
  }

  @Override
  public ServerWebSocket exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      checkClosed();
      this.exceptionHandler = handler;
      return this;
    }
  }

  @Override
  public ServerWebSocket closeHandler(Handler<Void> handler) {
    synchronized (conn) {
      checkClosed();
      this.closeHandler = handler;
      return this;
    }
  }

  @Override
  public ServerWebSocket frameHandler(Handler<WebSocketFrame> handler) {
    synchronized (conn) {
      checkClosed();
      this.frameHandler = handler;
      return this;
    }
  }

  @Override
  public ServerWebSocket pause() {
    synchronized (conn) {
      checkClosed();
      conn.doPause();
      return this;
    }
  }

  @Override
  public ServerWebSocket resume() {
    synchronized (conn) {
      checkClosed();
      conn.doResume();
      return this;
    }
  }

  @Override
  public ServerWebSocket setWriteQueueMaxSize(int maxSize) {
    synchronized (conn) {
      checkClosed();
      conn.doSetWriteQueueMaxSize(maxSize);
      return this;
    }
  }

  @Override
  public boolean writeQueueFull() {
    synchronized (conn) {
      checkClosed();
      return conn.isNotWritable();
    }
  }

  @Override
  public ServerWebSocket write(Buffer data) {
    synchronized (conn) {
      checkClosed();
      writeFrame(WebSocketFrame.binaryFrame(data, true));
      return this;
    }
  }

  @Override
  public ServerWebSocket drainHandler(Handler<Void> handler) {
    synchronized (conn) {
      checkClosed();
      this.drainHandler = handler;
      return this;
    }
  }

  @Override
  public ServerWebSocket writeFrame(WebSocketFrame frame) {
    synchronized (conn) {
      if (connectRunnable != null) {
        if (rejected) {
          throw new IllegalStateException("Cannot write to websocket, it has been rejected");
        }
        if (!connected && !closed) {
          connect();
        }
      }
      super.writeFrameInternal(frame);
      return this;
    }
  }

  @Override
  public ServerWebSocket writeFinalTextFrame(String text) {
    return writeFrame(WebSocketFrame.textFrame(text, true));
  }

  @Override
  public ServerWebSocket writeFinalBinaryFrame(Buffer data) {
    return writeFrame(WebSocketFrame.binaryFrame(data, true));
  }

  @Override
  public ServerWebSocket writeBinaryMessage(Buffer data) {
    synchronized (conn) {
      checkClosed();
      writeMessageInternal(data);
      return this;
    }
  }

  private void connect() {
    connectRunnable.run();
    connected = true;
  }

  // Connect if not already connected
  void connectNow() {
    synchronized (conn) {
      if (!connected && !rejected) {
        connect();
      }
    }
  }

  boolean isRejected() {
    synchronized (conn) {
      return rejected;
    }
  }

  void setMetric(Object metric) {
    this.metric = metric;
  }

  Object getMetric() {
    return metric;
  }
}
