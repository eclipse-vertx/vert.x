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
import io.vertx.core.http.RequestOptions;
import io.vertx.test.core.tls.Cert;
import io.vertx.test.core.tls.Trust;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http1xTLSTest extends HttpTLSTest {

  @Override
  HttpServer createHttpServer(HttpServerOptions options) {
    return vertx.createHttpServer(options);
  }

  @Override
  HttpClient createHttpClient(HttpClientOptions options) {
    return vertx.createHttpClient(options);
  }


  // ALPN tests

  @Test
  // Client and server uses ALPN
  public void testAlpn() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).serverUsesAlpn().clientUsesAlpn().pass();
  }

  // RequestOptions tests

  @Test
  // Client trusts all server certs
  public void testClearClientRequestOptionsSetSSL() throws Exception {
    RequestOptions options = new RequestOptions().setHost(DEFAULT_HTTP_HOST).setPort(4043).setURI(DEFAULT_TEST_URI).setSsl(true);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(false).requestOptions(options).pass();
  }

  @Test
  // Client trusts all server certs
  public void testSSLClientRequestOptionsSetSSL() throws Exception {
    RequestOptions options = new RequestOptions().setHost(DEFAULT_HTTP_HOST).setPort(4043).setURI(DEFAULT_TEST_URI).setSsl(true);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(true).requestOptions(options).pass();
  }

  @Test
  // Client trusts all server certs
  public void testClearClientRequestOptionsSetClear() throws Exception {
    RequestOptions options = new RequestOptions().setHost(DEFAULT_HTTP_HOST).setURI(DEFAULT_TEST_URI).setPort(4043).setSsl(false);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(false).serverSSL(false).requestOptions(options).pass();
  }

  @Test
  // Client trusts all server certs
  public void testSSLClientRequestOptionsSetClear() throws Exception {
    RequestOptions options = new RequestOptions().setHost(DEFAULT_HTTP_HOST).setURI(DEFAULT_TEST_URI).setPort(4043).setSsl(false);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(true).serverSSL(false).requestOptions(options).pass();
  }
}
