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

package io.vertx.test.http;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpTestBase extends AbstractHttpTest {

  @Override
  protected HttpClientAgent createHttpClient() {
    return vertx.createHttpClient(createBaseClientOptions());
  }

  protected HttpServer createHttpServer() {
    return vertx.createHttpServer(createBaseServerOptions());
  }

  protected HttpClientBuilder httpClientBuilder(Vertx vertx) {
    return vertx.httpClientBuilder().with(createBaseClientOptions());
  }

  protected HttpServerOptions createBaseServerOptions() {
    return new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST);
  }

  protected HttpClientOptions createBaseClientOptions() {
    return new HttpClientOptions();
  }

  public void setUp() throws Exception {
    super.setUp();
    HttpServerOptions baseServerOptions = createBaseServerOptions();
    testAddress = SocketAddress.inetSocketAddress(baseServerOptions.getPort(), baseServerOptions.getHost());
    requestOptions = new RequestOptions()
      .setHost(baseServerOptions.getHost())
      .setPort(baseServerOptions.getPort())
      .setURI(DEFAULT_TEST_URI);
  }

  @SuppressWarnings("unchecked")
  protected <E> Handler<E> noOpHandler() {
    return noOp;
  }

  private static final Handler noOp = e -> {
  };
}
