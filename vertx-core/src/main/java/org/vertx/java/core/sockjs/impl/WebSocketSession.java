package org.vertx.java.core.sockjs.impl;

import java.net.InetSocketAddress;
import java.util.Map;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;

import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.sockjs.SockJSSocket;

class WebSocketSession extends Session {

  private final ServerWebSocket ws;

  WebSocketSession(VertxInternal vertx, Map<String, Session> sessions, long heartbeatPeriod, 
      Handler<SockJSSocket> sockHandler, ServerWebSocket ws) {
    super(vertx, sessions, heartbeatPeriod, sockHandler);
    this.ws = ws;
  }

  @Override
  public InetSocketAddress remoteAddress() {
    return ws.remoteAddress();
  }

  @Override
  public InetSocketAddress localAddress() {
    return ws.localAddress();
  }

  @Override
  public MultiMap headers() {
    return ws.headers();
  }

  @Override
  public String uri() {
    return ws.uri();
  }

  @Override
  public X509Certificate[] peerCertificateChain()
      throws SSLPeerUnverifiedException {
    return ws.peerCertificateChain();
  }

}
