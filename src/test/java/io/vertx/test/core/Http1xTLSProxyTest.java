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

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;

import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * test for a https client request via HTTP CONNECT proxy
 * 
 */
public class Http1xTLSProxyTest extends HttpTLSTest {

  private ConnectHttpProxy proxy;

  @Override
  HttpServer createHttpServer(HttpServerOptions options) {
    return vertx.createHttpServer(options);
  }

  @Override
  HttpClient createHttpClient(HttpClientOptions options) {
    return vertx.createHttpClient(options);
  }

  @Before
  public void startProxy() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    proxy = new ConnectHttpProxy();
    proxy.start(vertx, v -> latch.countDown());
    awaitLatch(latch);
  }

  @After
  public void stopProxy() {
    proxy.stop();
  }

  @Test
  // Access https server via connect proxy
  public void testHttpsProxy() throws Exception {
    proxy.setUsername(null);
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.JKS, TLSCert.NONE).useProxy().pass();
    // check that the connection did in fact go through the proxy
    assertNotNull(proxy.getLastUri());
  }

  @Test
  // Check that proxy auth fails if it is missing
  public void testHttpsProxyAuthFail() throws Exception {
    proxy.setUsername("username");
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.JKS, TLSCert.NONE).useProxy().useProxyAuth().fail();
  }

  @Test
  // Access https server via connect proxy with proxy auth required
  public void testHttpsProxyAuth() throws Exception {
    proxy.setUsername("username");
    testTLS(TLSCert.NONE, TLSCert.JKS, TLSCert.JKS, TLSCert.NONE).useProxy().useProxyAuth().pass();
    // check that the connection did in fact go through the proxy
    assertNotNull(proxy.getLastUri());
  }
}
