/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 */

package io.vertx.core.http.impl;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ConnectionBase;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_GATEWAY;

/**
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public class ServerWebSocketImpl extends WebSocketImplBase<ServerWebSocket> implements ServerWebSocket {

  private final String uri;
  private final String path;
  private final String query;
  private final Runnable connectRunnable;
  private final MultiMap headers;

  private boolean connected;
  private boolean rejected;
  private HttpResponseStatus rejectedStatus;

  public ServerWebSocketImpl(VertxInternal vertx, String uri, String path, String query, MultiMap headers,
                             ConnectionBase conn, boolean supportsContinuation, Runnable connectRunnable,
                             int maxWebSocketFrameSize, int maxWebSocketMessageSize) {
    super(vertx, conn, supportsContinuation, maxWebSocketFrameSize, maxWebSocketMessageSize);
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
    reject(BAD_GATEWAY);
  }

  @Override
  public void reject(int status) {
    reject(HttpResponseStatus.valueOf(status));
  }

  private void reject(HttpResponseStatus status) {
    synchronized (conn) {
      checkClosed();
      if (connectRunnable == null) {
        throw new IllegalStateException("Cannot reject websocket on the client side");
      }
      if (connected) {
        throw new IllegalStateException("Cannot reject websocket, it has already been written to");
      }
      rejected = true;
      rejectedStatus = status;
    }
  }

  @Override
  public SSLSession sslSession() {
    return conn.sslSession();
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.peerCertificateChain();
  }

  @Override
  public void close() {
    synchronized (conn) {
      checkClosed();
      if (connectRunnable != null) {
        // Server side
        if (isRejected()) {
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
  public ServerWebSocket writeFrame(WebSocketFrame frame) {
    synchronized (conn) {
      if (connectRunnable != null) {
        if (isRejected()) {
          throw new IllegalStateException("Cannot write to websocket, it has been rejected");
        }
        if (!connected && !closed) {
          connect();
        }
      }
      return super.writeFrame(frame);
    }
  }

  private void connect() {
    connectRunnable.run();
    connected = true;
  }

  // Connect if not already connected
  void connectNow() {
    synchronized (conn) {
      if (!connected && !isRejected()) {
        connect();
      }
    }
  }

  boolean isRejected() {
    synchronized (conn) {
      return rejected;
    }
  }

  HttpResponseStatus getRejectedStatus() {
    synchronized (conn) {
      return rejectedStatus;
    }
  }

}
