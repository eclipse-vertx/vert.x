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

package io.vertx.ext.sockjs;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.RouteMatcher;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.impl.WebSocketMatcher;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class RawWebSocketTransport {

  private static final Logger log = LoggerFactory.getLogger(WebSocketTransport.class);

  private static class RawWSSockJSSocket extends SockJSSocketBase {

    private ServerWebSocket ws;
    private MultiMap headers;

    RawWSSockJSSocket(Vertx vertx, ServerWebSocket ws) {
      super(vertx);
      this.ws = ws;
      ws.closeHandler(v -> RawWSSockJSSocket.super.close());
    }

    public SockJSSocket dataHandler(Handler<Buffer> handler) {
      ws.dataHandler(handler);
      return this;
    }

    public SockJSSocket pause() {
      ws.pause();
      return this;
    }

    public SockJSSocket resume() {
      ws.resume();
      return this;
    }

    public SockJSSocket write(Buffer data) {
      ws.write(data);
      return this;
    }

    public SockJSSocket setWriteQueueMaxSize(int maxQueueSize) {
      ws.setWriteQueueMaxSize(maxQueueSize);
      return this;
    }

    public boolean writeQueueFull() {
      return ws.writeQueueFull();
    }

    public SockJSSocket drainHandler(Handler<Void> handler) {
      ws.drainHandler(handler);
      return this;
    }

    public SockJSSocket exceptionHandler(Handler<Throwable> handler) {
      ws.exceptionHandler(handler);
      return this;
    }

    public SockJSSocket endHandler(Handler<Void> endHandler) {
      ws.endHandler(endHandler);
      return this;
    }

    public void close() {
      super.close();
      ws.close();
    }

    @Override
    public SocketAddress remoteAddress() {
      return ws.remoteAddress();
    }

    @Override
    public SocketAddress localAddress() {
      return ws.localAddress();
    }

    @Override
    public MultiMap headers() {
      if (headers == null) {
        headers = BaseTransport.removeCookieHeaders(ws.headers());
      }
      return headers;
    }

    @Override
    public String uri() {
      return ws.uri();
    }

    @Override
    public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
      return ws.peerCertificationChain();
    }
  }

  RawWebSocketTransport(final Vertx vertx, WebSocketMatcher wsMatcher, RouteMatcher rm, String basePath,
                        final Handler<SockJSSocket> sockHandler) {

    String wsRE = basePath + "/websocket";

    wsMatcher.addRegEx(wsRE, match -> {
        SockJSSocket sock = new RawWSSockJSSocket(vertx, match.ws);
        sockHandler.handle(sock);
    });

    rm.getWithRegEx(wsRE, request -> {
      request.response().setStatusCode(400);
      request.response().end("Can \"Upgrade\" only to \"WebSocket\".");
    });

    rm.allWithRegEx(wsRE, request -> {
      request.response().headers().set("Allow", "GET");
      request.response().setStatusCode(405);
      request.response().end();
    });
  }

}
