/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JksOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2TestBase extends HttpTestBase {

  static HttpServerOptions createHttp2ServerOptions(int port, String host) {
    return new HttpServerOptions()
        .setPort(port)
        .setHost(host)
        .setUseAlpn(true)
        .setSsl(true)
        .addEnabledCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA") // Non Diffie-helman -> debuggable in wireshark
        .setKeyStoreOptions((JksOptions) TLSCert.JKS.getServerKeyCertOptions());
  };

  static HttpClientOptions createHttp2ClientOptions() {
    return new HttpClientOptions().
        setUseAlpn(true).
        setTrustStoreOptions((JksOptions) TLSCert.JKS.getClientTrustOptions()).
        setProtocolVersion(HttpVersion.HTTP_2);
  }

  protected HttpServerOptions serverOptions;
  protected HttpClientOptions clientOptions;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    serverOptions =  createHttp2ServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
    clientOptions = createHttp2ClientOptions();
    server = vertx.createHttpServer(serverOptions);
  }

  protected void assertOnIOContext(Context context) {
    Context current = Vertx.currentContext();
    assertNotNull(current);
    assertEquals(context, current);
    for (StackTraceElement elt : Thread.currentThread().getStackTrace()) {
      if (elt.getMethodName().equals("executeFromIO")) {
        return;
      }
    }
    fail("Not from IO");
  }
}
