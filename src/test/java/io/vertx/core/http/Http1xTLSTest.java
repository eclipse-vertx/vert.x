/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(false).requestProvider(c -> c.request(new RequestOptions().setMethod(HttpMethod.POST).setAbsoluteURI(absoluteURI))).pass();
  }

  @Test
  // Client trusts all server certs
  public void testSSLClientRequestAbsSetSSL() throws Exception {
    String absoluteURI = "https://" + DEFAULT_HTTP_HOST + ":4043/" + DEFAULT_TEST_URI;
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(true).requestProvider(c -> c.request(new RequestOptions().setMethod(HttpMethod.POST).setAbsoluteURI(absoluteURI))).pass();
  }

  @Test
  // Client trusts all server certs
  public void testClearClientRequestAbsSetClear() throws Exception {
    String absoluteURI = "http://" + DEFAULT_HTTP_HOST + ":4043/" + DEFAULT_TEST_URI;
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(false).serverSSL(false).requestProvider(c -> c.request(new RequestOptions().setMethod(HttpMethod.POST).setAbsoluteURI(absoluteURI))).pass();
  }

  @Test
  // Client trusts all server certs
  public void testSSLClientRequestAbsSetClear() throws Exception {
    String absoluteURI = "http://" + DEFAULT_HTTP_HOST + ":4043/" + DEFAULT_TEST_URI;
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE).clientSSL(true).serverSSL(false).requestProvider(c -> c.request(new RequestOptions().setMethod(HttpMethod.POST).setAbsoluteURI(absoluteURI))).pass();
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
    try {
      RequestOptions options = new RequestOptions().setHost(DEFAULT_HTTP_HOST).setURI(DEFAULT_TEST_URI).setPort(DEFAULT_HTTP_PORT);
      testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE)
          .clientSSL(false)
          .serverSSL(true)
          .requestOptions(options)
          .followRedirects(true)
          .pass();
    } finally {
      redirectServer.close();
    }
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
    try {
      RequestOptions options = new RequestOptions().setHost(DEFAULT_HTTP_HOST).setURI(DEFAULT_TEST_URI).setPort(4043).setSsl(false);
      testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.NONE, Trust.NONE)
          .clientSSL(true)
          .serverSSL(false)
          .requestOptions(options)
          .followRedirects(true)
          .pass();
    } finally {
      redirectServer.close();
    }
  }

  @Test
  public void testAppendToHttpChunks() throws Exception {
    List<String> expected = Arrays.asList("chunk-1", "chunk-2", "chunk-3");
    server = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setKeyStoreOptions(Cert.SERVER_JKS.get())
      .setHost(DEFAULT_HTTPS_HOST)
      .setPort(DEFAULT_HTTPS_PORT)
    ).requestHandler(req -> {
      HttpServerResponse resp = req.response().setChunked(true);
      expected.forEach(resp::write);
      resp.end();
    });
    startServer(server);
    client = vertx.createHttpClient(new HttpClientOptions().setSsl(true).setTrustAll(true));
    client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, DEFAULT_TEST_URI, onSuccess(req -> {
      req.send(onSuccess(resp -> {
        List<String> chunks = new ArrayList<>();
        resp.handler(chunk -> {
          chunk.appendString("-suffix");
          chunks.add(chunk.toString());
        });
        resp.endHandler(v -> {
          assertEquals(expected.stream().map(s -> s + "-suffix").collect(Collectors.toList()), chunks);
          testComplete();
        });
      }));
    }));
    await();
  }
}
