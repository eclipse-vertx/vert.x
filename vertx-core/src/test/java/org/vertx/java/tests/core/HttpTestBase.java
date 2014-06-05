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
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpTestBase extends VertxTestBase {
  private static final int DEFAULT_HTTP_PORT = Integer.getInteger("vertx.http.port", 8080);

  protected HttpServer server;
  protected HttpClient client;
  protected int port = DEFAULT_HTTP_PORT;

  @Before
  public void beforeHttpTestBase() throws Exception {
    server = vertx.createHttpServer();
    client = vertx.createHttpClient().setPort(port);
  }

  @After
  public void afterHttpTestBase() throws Exception {
    client.close();
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
