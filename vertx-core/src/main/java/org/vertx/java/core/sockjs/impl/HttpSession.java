package org.vertx.java.core.sockjs.impl;

import java.net.InetSocketAddress;
import java.util.Map;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;

import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.sockjs.SockJSSocket;

class HttpSession extends Session {

  private final HttpServerRequest req;

  HttpSession(VertxInternal vertx, Map<String, Session> sessions, String id, long timeout, long heartbeatPeriod,
      Handler<SockJSSocket> sockHandler, HttpServerRequest req) {
    super(vertx, sessions, id, timeout, heartbeatPeriod, sockHandler);
    this.req = req;
  }

  @Override
  public InetSocketAddress remoteAddress() {
    return req.remoteAddress();
  }

  @Override
  public InetSocketAddress localAddress() {
    return req.localAddress();
  }

  @Override
  public MultiMap headers() {
    return req.headers();
  }

  @Override
  public String uri() {
    return req.uri();
  }

  @Override
  public X509Certificate[] peerCertificateChain()
      throws SSLPeerUnverifiedException {
    return req.peerCertificateChain();
  }

}
