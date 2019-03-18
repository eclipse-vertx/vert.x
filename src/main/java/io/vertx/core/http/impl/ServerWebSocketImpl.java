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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.impl.ContextInternal;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import static io.netty.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;
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
public class ServerWebSocketImpl extends WebSocketImplBase<ServerWebSocketImpl> implements ServerWebSocket {

  private final Http1xServerConnection conn;
  private final String uri;
  private final String path;
  private final String query;
  private final WebSocketServerHandshaker handshaker;
  private final MultiMap headers;
  private HttpServerRequestImpl request;
  private Integer status;
  private Promise<Integer> handshakePromise;

  ServerWebSocketImpl(ContextInternal context,
                      Http1xServerConnection conn,
                      boolean supportsContinuation,
                      HttpServerRequestImpl request,
                      WebSocketServerHandshaker handshaker,
                      int maxWebSocketFrameSize,
                      int maxWebSocketMessageSize) {
    super(context, conn, supportsContinuation, maxWebSocketFrameSize, maxWebSocketMessageSize);
    this.conn = conn;
    this.uri = request.uri();
    this.path = request.path();
    this.query = request.query();
    this.headers = request.headers();
    this.request = request;
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
  public Future<Void> close() {
    synchronized (conn) {
      checkClosed();
      if (status == null) {
        if (handshakePromise == null) {
          tryHandshake(101);
        } else {
          handshakePromise.tryComplete(101);
        }
      }
    }
    return super.close();
  }

  @Override
  public ServerWebSocketImpl writeFrame(WebSocketFrame frame, Handler<AsyncResult<Void>> handler) {
    synchronized (conn) {
      Boolean check = checkAccept();
      if (check == null) {
        throw new IllegalStateException("Cannot write to WebSocket, it is pending accept or reject");
      }
      if (!check) {
        throw new IllegalStateException("Cannot write to WebSocket, it has been rejected");
      }
      return super.writeFrame(frame, handler);
    }
  }

  private Boolean checkAccept() {
    return tryHandshake(SC_SWITCHING_PROTOCOLS);
  }

  private void handleHandshake(int sc) {
    synchronized (conn) {
      if (status == null) {
        if (sc == SC_SWITCHING_PROTOCOLS) {
          doHandshake();
        } else {
          status = sc;
          HttpUtils.sendError(conn.channel(), HttpResponseStatus.valueOf(sc));
        }
      }
    }
  }

  private void doHandshake() {
    Channel channel = conn.channel();
    try {
      handshaker.handshake(channel, request.nettyRequest());
    } catch (Exception e) {
      request.response().setStatusCode(BAD_REQUEST.code()).end();
      throw e;
    } finally {
      request = null;
    }
    conn.responseComplete();
    status = SWITCHING_PROTOCOLS.code();
    subProtocol(handshaker.selectedSubprotocol());
    // remove compressor as its not needed anymore once connection was upgraded to websockets
    ChannelPipeline pipeline = channel.pipeline();
    ChannelHandler handler = pipeline.get(HttpChunkContentCompressor.class);
    if (handler != null) {
      pipeline.remove(handler);
    }
    registerHandler(conn.getContext().owner().eventBus());
  }

  Boolean tryHandshake(int sc) {
    synchronized (conn) {
      if (status == null && handshakePromise == null) {
        setHandshake(Promise.succeededPromise(sc));
      }
      return status == null ? null : status == sc;
    }
  }

  @Override
  public void setHandshake(Future<Integer> future) {
    setHandshake((Promise<Integer>) future);
  }

  @Override
  public void setHandshake(Promise<Integer> promise) {
    if (promise == null) {
      throw new NullPointerException();
    }
    synchronized (conn) {
      if (handshakePromise != null) {
        throw new IllegalStateException();
      }
      handshakePromise = promise;
    }
    promise.future().setHandler(ar -> {
      if (ar.succeeded()) {
        handleHandshake(ar.result());
      } else {
        handleHandshake(500);
      }
    });
  }
}
