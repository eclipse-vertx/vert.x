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

package io.vertx.test.core;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
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

  // requestAbs test

  @Test
  // Client trusts all server certs
  public void testClearClientRequestAbsSetSSL() throws Exception {
    String absoluteURI = "https://" + DEFAULT_HTTP_HOST + ":4043/" + DEFAULT_TEST_URI;
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(false).requestProvider(c -> c.requestAbs(HttpMethod.POST, absoluteURI)).pass();
  }

  @Test
  // Client trusts all server certs
  public void testSSLClientRequestAbsSetSSL() throws Exception {
    String absoluteURI = "https://" + DEFAULT_HTTP_HOST + ":4043/" + DEFAULT_TEST_URI;
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(true).requestProvider(c -> c.requestAbs(HttpMethod.POST, absoluteURI)).pass();
  }

  @Test
  // Client trusts all server certs
  public void testClearClientRequestAbsSetClear() throws Exception {
    String absoluteURI = "http://" + DEFAULT_HTTP_HOST + ":4043/" + DEFAULT_TEST_URI;
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(false).serverSSL(false).requestProvider(c -> c.requestAbs(HttpMethod.POST, absoluteURI)).pass();
  }

  @Test
  // Client trusts all server certs
  public void testSSLClientRequestAbsSetClear() throws Exception {
    String absoluteURI = "http://" + DEFAULT_HTTP_HOST + ":4043/" + DEFAULT_TEST_URI;
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(true).serverSSL(false).requestProvider(c -> c.requestAbs(HttpMethod.POST, absoluteURI)).pass();
  }

  // Redirect tests

  @Test
  public void testRedirectToSSL() throws Exception {
    HttpServer redirectServer = vertx.createHttpServer(new HttpServerOptions()
        .setHost(DEFAULT_HTTP_HOST)
        .setPort(DEFAULT_HTTP_PORT)
    ).requestHandler(req -> {
      req.response().setStatusCode(303).putHeader("location", "https://" + DEFAULT_HTTP_HOST + ":4043/" + DEFAULT_TEST_URI).end();
    });
    startServer(redirectServer);
    RequestOptions options = new RequestOptions().setHost(DEFAULT_HTTP_HOST).setURI(DEFAULT_TEST_URI).setPort(DEFAULT_HTTP_PORT);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE)
        .clientSSL(false)
        .serverSSL(true)
        .requestOptions(options)
        .followRedirects(true)
        .pass();
  }

  @Test
  public void testRedirectFromSSL() throws Exception {
    HttpServer redirectServer = vertx.createHttpServer(new HttpServerOptions()
        .setSsl(true)
        .setKeyStoreOptions(Cert.SERVER_JKS.get())
        .setHost(DEFAULT_HTTP_HOST)
        .setPort(DEFAULT_HTTP_PORT)
    ).requestHandler(req -> {
      req.response().setStatusCode(303).putHeader("location", "http://" + DEFAULT_HTTP_HOST + ":4043/" + DEFAULT_TEST_URI).end();
    });
    startServer(redirectServer);
    RequestOptions options = new RequestOptions().setHost(DEFAULT_HTTP_HOST).setURI(DEFAULT_TEST_URI).setPort(4043);
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.NONE, Trust.NONE)
        .clientSSL(true)
        .serverSSL(false)
        .requestOptions(options)
        .followRedirects(true)
        .pass();
  }
}
