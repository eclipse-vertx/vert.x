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

package org.vertx.java.tests.core;

import org.junit.After;
import org.junit.Before;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;

import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpTestBase extends VertxTestBase {

  public static final String DEFAULT_HTTP_HOST = "localhost";
  public static final int DEFAULT_HTTP_PORT = 8080;
  public static final String DEFAULT_TEST_URI = "some-uri";

  protected HttpServer server;
  protected HttpClient client;

  @Before
  public void beforeHttpTestBase() throws Exception {
    server = vertx.createHttpServer();
  }

  @After
  public void afterHttpTestBase() throws Exception {
    if (client != null) {
      client.close();
    }
    CountDownLatch latch = new CountDownLatch(1);
    server.close((asyncResult) -> {
      assertTrue(asyncResult.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
  }

  @SuppressWarnings("unchecked")
  protected <E> Handler<E> noOpHandler() {
    return noOp;
  }

  private static final Handler noOp = e -> {
  };
}
