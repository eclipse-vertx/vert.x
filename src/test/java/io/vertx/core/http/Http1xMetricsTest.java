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

import io.vertx.core.ThreadingModel;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

public class Http1xMetricsTest extends HttpMetricsTestBase {

  public Http1xMetricsTest() {
    this(ThreadingModel.EVENT_LOOP);
  }

  protected Http1xMetricsTest(ThreadingModel threadingModel) {
    super(HttpVersion.HTTP_1_1, threadingModel);
  }

  @Test
  public void testAllocatedStreamResetShouldNotCallMetricsLifecycle() throws Exception {
    server.requestHandler(req -> {
      fail();
    });
    startServer(testAddress);
    CountDownLatch latch = new CountDownLatch(1);
    client = vertx.createHttpClient(createBaseClientOptions().setIdleTimeout(2));
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.exceptionHandler(err -> {
        latch.countDown();
      });
      req.connection().close();
    }));
    awaitLatch(latch);
  }
}
