/*
 * Copyright (c) 2011-2014 The original author or authors
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
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.util.Map;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpSession extends Session {
  private final HttpServerRequest request;

  public HttpSession(Vertx vertx, Map<String, Session> sessions, String id, long timeout, long heartbeatPeriod, Handler<SockJSSocket> sockHandler, HttpServerRequest request) {
    super(vertx, sessions, id, timeout, heartbeatPeriod, sockHandler, request.headers());
    this.request = request;
  }

  @Override
  public SocketAddress remoteAddress() {
    return request.remoteAddress();
  }

  @Override
  public SocketAddress localAddress() {
    return request.localAddress();
  }

  @Override
  public String uri() {
    return request.uri();
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return request.peerCertificateChain();
  }
}
