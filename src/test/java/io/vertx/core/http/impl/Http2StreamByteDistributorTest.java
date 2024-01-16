/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Http2TestBase;
import io.vertx.core.http.HttpClientResponse;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2StreamByteDistributorTest extends Http2TestBase {

  @Test
  public void smokeTest() throws Exception {
    System.setProperty(HttpUtils.H2_STREAM_BYTE_DISTRIBUTOR, HttpUtils.UNIFORM_DISTRIBUTOR);
    try {
      server.requestHandler(req -> {
        req.response().end("Hello World");
      });
      startServer(testAddress);
      Future<Buffer> response = client
        .request(requestOptions)
        .compose(req -> req
          .send()
          .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
          .compose(HttpClientResponse::body));
      response.onComplete(onSuccess(body -> {
        assertEquals("Hello World", body.toString());
        testComplete();
      }));
      await();
    } finally {
      System.clearProperty(HttpUtils.H2_STREAM_BYTE_DISTRIBUTOR);
    }
  }
}
