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

package io.vertx.core.http;

import io.vertx.core.http.HttpServer;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.*;

/**
 * Make sure that the Netty pipeline has a handler catching the {@link java.io.IOException} if the connection is reset
 * before any data has been sent.
 *
 * @author Thomas Segismont
 */
public class HttpConnectionEarlyResetTest extends VertxTestBase {

  private HttpServer httpServer;
  private AtomicReference<Throwable> caught = new AtomicReference<>();
  private CountDownLatch resetLatch = new CountDownLatch(1);

  @Override
  public void setUp() throws Exception {
    super.setUp();
    CountDownLatch listenLatch = new CountDownLatch(1);
    httpServer = vertx.createHttpServer()
      .requestHandler(request -> {})
      .exceptionHandler(t -> {
        caught.set(t);
        resetLatch.countDown();
      })
      .listen(8080, onSuccess(server -> listenLatch.countDown()));
    awaitLatch(listenLatch);
  }

  @Test
  public void testExceptionCaught() throws Exception {
    vertx.createNetClient(new NetClientOptions().setSoLinger(0)).connect(8080, "localhost", onSuccess(socket -> {
      vertx.setTimer(2000, id -> {
        socket.close();
      });
    }));
    awaitLatch(resetLatch);
    assertThat(caught.get(), instanceOf(IOException.class));
  }

  @Override
  public void tearDown() throws Exception {
    if (httpServer != null) {
      CountDownLatch closeLatch = new CountDownLatch(1);
      httpServer.close(event -> closeLatch.countDown());
      awaitLatch(closeLatch);
    }
    super.tearDown();
  }
}
