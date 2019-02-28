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
import io.vertx.core.MultiMap;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.impl.VertxInternal;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import java.util.function.Function;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_GATEWAY;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;

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
  private HttpResponseStatus status;

  public ServerWebSocketImpl(VertxInternal vertx, String uri, String path, String query, MultiMap headers,
                             Http1xConnectionBase conn, boolean supportsContinuation, Function<ServerWebSocketImpl, String> handshaker,
                             int maxWebSocketFrameSize, int maxWebSocketMessageSize) {
    super(vertx, conn, supportsContinuation, maxWebSocketFrameSize, maxWebSocketMessageSize);
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
    synchronized (conn) {
      if (tryHandshake(SWITCHING_PROTOCOLS) != null) {
        throw new IllegalStateException("WebSocket already rejected");
      }
    }
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
    if (status.code() == SWITCHING_PROTOCOLS.code()) {
      throw new IllegalArgumentException("Invalid WebSocket rejection status code: 101");
    }
    if (tryHandshake(status) != null) {
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
      if (checkAccept()) {
        throw new IllegalStateException("Cannot close WebSocket, it has been rejected");
      }
      super.close();
    }
  }

  @Override
  public ServerWebSocket writeFrame(WebSocketFrame frame) {
    synchronized (conn) {
      if (checkAccept()) {
        throw new IllegalStateException("Cannot write to WebSocket, it has been rejected");
      }
      return super.writeFrame(frame);
    }
  }

  private boolean checkAccept() {
    HttpResponseStatus ret = tryHandshake(SWITCHING_PROTOCOLS);
    return ret != null && ret != SWITCHING_PROTOCOLS;
  }

  /**
   * Attempt to handshake to the specified {@code handshakeStatus} or report the status that was already sent.
   * 
   * @param handshakeStatus the desired status of the handshake
   * @return {@code null} when the handshake has been done or the status that was already sent
   */
  HttpResponseStatus tryHandshake(HttpResponseStatus handshakeStatus) {
    synchronized (conn) {
      if (status == null) {
        status = handshakeStatus;
        if (handshakeStatus == SWITCHING_PROTOCOLS) {
          String res;
          try {
            res = handshaker.apply(this);
          } catch (Exception e) {
            HttpUtils.sendError(conn.channel(), BAD_REQUEST, "\"Connection\" header must be \"Upgrade\".");
            throw e;
          }
          subProtocol(res);
        } else {
          HttpUtils.sendError(conn.channel(), handshakeStatus);
        }
        return null;
      } else {
        return status;
      }
    }
  }
}
