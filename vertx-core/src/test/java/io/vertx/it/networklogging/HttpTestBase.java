/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.it.networklogging;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.http.HttpServer;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.netty.TestLoggerFactory;
import org.junit.Test;

import java.util.function.Predicate;

public abstract class HttpTestBase extends VertxTestBase {

  private final HttpConfig config;
  private HttpServer server;
  private HttpClient client;

  public HttpTestBase(HttpConfig config) {
    this.config = config;
  }

  protected abstract boolean check(TestLoggerFactory factory);

  @Test
  public void testNoLogging() throws Exception {
    testLogging(false, false, ((Predicate<TestLoggerFactory>) this::check).negate());
  }

  @Test
  public void testServerLogging() throws Exception {
    testLogging(true, false, this::check);
  }

  @Test
  public void testClientLogging() throws Exception {
    testLogging(false, true, this::check);
  }

  private void testLogging(boolean logServerActivity, boolean logClientActivity, Predicate<TestLoggerFactory> checker) throws Exception {
    TestUtils.testLogging(factory -> {
      server = config.forServer().setLogActivity(logServerActivity).create(vertx);
      client = config.forClient().setLogActivity(logClientActivity).create(vertx);
      try {
        server.requestHandler(req -> {
          req.response().end();
        });
        server.listen().await();
        client
          .request(HttpMethod.GET, server.actualPort(), "localhost", "/")
          .compose(request -> {
            Future<Buffer> compose = request
              .send()
              .expecting(HttpResponseExpectation.SC_OK)
              .compose(HttpClientResponse::body);
            return compose;
          })
          .await();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      assertTrue(checker.test(factory));
    });
  }
}
