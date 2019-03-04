/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import java.util.function.Function;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import static io.vertx.core.http.impl.HttpUtils.SC_SWITCHING_PROTOCOLS;
import static io.vertx.core.http.impl.HttpUtils.SC_BAD_GATEWAY;

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
  private final Function<ServerWebSocketImpl, String> handshaker;
  private final MultiMap headers;
  private Integer status;
  private Future<Integer> handshakeFuture;

  public ServerWebSocketImpl(VertxInternal vertx, ContextInternal context, String uri, String path, String query, MultiMap headers,
                             Http1xConnectionBase conn, boolean supportsContinuation, Function<ServerWebSocketImpl, String> handshaker,
                             int maxWebSocketFrameSize, int maxWebSocketMessageSize) {
    super(vertx, context, conn, supportsContinuation, maxWebSocketFrameSize, maxWebSocketMessageSize);
    this.uri = uri;
    this.path = path;
    this.query = query;
    this.headers = headers;
    this.handshaker = handshaker;
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
  public void accept() {
    if (tryHandshake(SC_SWITCHING_PROTOCOLS) != Boolean.TRUE) {
      throw new IllegalStateException("WebSocket already rejected");
    }
  }

  @Override
  public void reject() {
    reject(SC_BAD_GATEWAY);
  }

  @Override
  public void reject(int sc) {
    if (sc == SC_SWITCHING_PROTOCOLS) {
      throw new IllegalArgumentException("Invalid WebSocket rejection status code: 101");
    }
    if (tryHandshake(sc) != Boolean.TRUE) {
      throw new IllegalStateException("Cannot reject WebSocket, it has already been written to");
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
      if (status == null) {
        if (handshakeFuture == null) {
          tryHandshake(101);
        } else {
          handshakeFuture.tryComplete(101);
        }
      }
    }
    super.close();
  }

  @Override
  public ServerWebSocket writeFrame(WebSocketFrame frame) {
    synchronized (conn) {
      Boolean check = checkAccept();
      if (check == null) {
        throw new IllegalStateException("Cannot write to WebSocket, it is pending accept or reject");
      }
      if (!check) {
        throw new IllegalStateException("Cannot write to WebSocket, it has been rejected");
      }
      return super.writeFrame(frame);
    }
  }

  private Boolean checkAccept() {
    return tryHandshake(SC_SWITCHING_PROTOCOLS);
  }

  private void handleHandshake(int sc) {
    synchronized (conn) {
      if (status == null) {
        if (sc == SC_SWITCHING_PROTOCOLS) {
          String res;
          try {
            res = handshaker.apply(this);
            status = sc;
          } catch (Exception e) {
            status = BAD_REQUEST.code();
            HttpUtils.sendError(conn.channel(), BAD_REQUEST, "\"Connection\" header must be \"Upgrade\".");
            throw e;
          }
          subProtocol(res);
        } else {
          status = sc;
          HttpUtils.sendError(conn.channel(), HttpResponseStatus.valueOf(sc));
        }
      }
    }
  }

  Boolean tryHandshake(int sc) {
    synchronized (conn) {
      if (status == null && handshakeFuture == null) {
        setHandshake(Future.succeededFuture(sc));
      }
      return status == null ? null : status == sc;
    }
  }

  @Override
  public void setHandshake(Future<Integer> future) {
    if (future == null) {
      throw new NullPointerException();
    }
    synchronized (conn) {
      if (handshakeFuture != null) {
        throw new IllegalStateException();
      }
      handshakeFuture = future;
    }
    future.setHandler(ar -> {
      if (ar.succeeded()) {
        handleHandshake(ar.result());
      } else {
        handleHandshake(500);
      }
    });
  }
}
