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

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.proxy.SocksProxy;
import io.vertx.test.proxy.TestProxyBase;
import io.vertx.test.core.VertxTestBase;

import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpTestBase extends VertxTestBase {

  public static final String DEFAULT_HTTP_HOST = "localhost";
  public static final int DEFAULT_HTTP_PORT = 8080;
  public static final String DEFAULT_HTTPS_HOST = "localhost";
  public static final int DEFAULT_HTTPS_PORT = 4043;
  public static final String DEFAULT_HTTPS_HOST_AND_PORT = "localhost:4043";
  public static final String DEFAULT_TEST_URI = "some-uri";

  protected HttpServer server;
  protected HttpClient client;
  protected TestProxyBase proxy;
  protected SocketAddress testAddress;

  protected HttpServerOptions createBaseServerOptions() {
    return new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST);
  }

  protected HttpClientOptions createBaseClientOptions() {
    return new HttpClientOptions();
  }

  public void setUp() throws Exception {
    super.setUp();
    server = vertx.createHttpServer(createBaseServerOptions());
    client = vertx.createHttpClient(createBaseClientOptions());
    testAddress = SocketAddress.inetSocketAddress(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }

  protected void tearDown() throws Exception {
    if (client != null) {
      try {
        client.close();
      } catch (IllegalStateException ignore) {
        // Client was already closed by the test
      }
    }
    if (server != null) {
      CountDownLatch latch = new CountDownLatch(1);
      server.close((asyncResult) -> {
        assertTrue(asyncResult.succeeded());
        latch.countDown();
      });
      awaitLatch(latch);
    }
    if (proxy != null) {
      proxy.stop();
    }
    super.tearDown();
  }

  @SuppressWarnings("unchecked")
  protected <E> Handler<E> noOpHandler() {
    return noOp;
  }

  private static final Handler noOp = e -> {
  };

  protected void startServer() throws Exception {
    startServer(vertx.getOrCreateContext());
  }

  protected void startServer(SocketAddress bindAddress) throws Exception {
    startServer(bindAddress, vertx.getOrCreateContext());
  }

  protected void startServer(HttpServer server) throws Exception {
    startServer(vertx.getOrCreateContext(), server);
  }

  protected void startServer(SocketAddress bindAddress, HttpServer server) throws Exception {
    startServer(bindAddress, vertx.getOrCreateContext(), server);
  }

  protected void startServer(Context context) throws Exception {
    startServer(context, server);
  }

  protected void startServer(SocketAddress bindAddress, Context context) throws Exception {
    startServer(bindAddress, context, server);
  }

  protected void startServer(Context context, HttpServer server) throws Exception {
    startServer(null, context, server);
  }

  protected void startServer(SocketAddress bindAddress, Context context, HttpServer server) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    context.runOnContext(v -> {
      Handler<AsyncResult<HttpServer>> onListen = onSuccess(s -> latch.countDown());
      if (bindAddress != null) {
        server.listen(bindAddress, onListen);
      } else {
        server.listen(onListen);
      }
    });
    awaitLatch(latch);
  }

  protected void startProxy(String username, ProxyType proxyType) throws Exception {
    if (proxyType == ProxyType.HTTP) {
      proxy = new HttpProxy(username);
    } else {
      proxy = new SocksProxy(username);
    }
    proxy.start(vertx);
  }
}
