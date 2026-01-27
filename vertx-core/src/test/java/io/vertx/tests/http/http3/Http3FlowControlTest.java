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
package io.vertx.tests.http.http3;

import io.vertx.core.Completable;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpClientConfig;
import io.vertx.core.streams.WriteStream;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RunWith(LinuxOrOsx.class)
public class Http3FlowControlTest extends VertxTestBase {

  private HttpServer server;
  private HttpClientConfig clientConfig;
  private HttpClientAgent client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    QuicHttpServerConfig serverConfig = new QuicHttpServerConfig();
    serverConfig.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
    clientConfig = new HttpClientConfig();
    clientConfig.setSupportedVersions(List.of(HttpVersion.HTTP_3));
    clientConfig.getSslOptions().setTrustOptions(Trust.SERVER_JKS.get());
    server = vertx.createHttpServer(serverConfig);
    client = vertx.createHttpClient(clientConfig);
  }

  @Override
  protected void tearDown() throws Exception {
    server.close().await();
    client.close().await();
    super.tearDown();
  }

  private void pump(int times, Buffer chunk, WriteStream<Buffer> writeStream, Completable<Integer> cont) {
    if (writeStream.writeQueueFull()) {
      cont.succeed(times);
    } else {
      writeStream.write(chunk);
      vertx.runOnContext(v -> pump(times + 1, chunk, writeStream, cont));
    }
  }

  @Test
  public void testHttpServerResponseFlowControl() {

    Buffer chunk = Buffer.buffer(TestUtils.randomAlphaString(128));
    CompletableFuture<Integer> latch = new CompletableFuture<>();

    server.requestHandler(req -> {
      pump(0, chunk, req.response(), onSuccess2(times -> {
        req.response().end();
        latch.complete(times);
      }));
    });
    server.listen(8443, "localhost").await();

    client.request(HttpMethod.GET, 8443, "localhost", "/")
      .compose(request -> request
        .send()
        .expecting(HttpResponseExpectation.SC_OK))
      .onComplete(onSuccess2(resp -> {
        resp.pause();
        Buffer expected = Buffer.buffer();
        latch.whenComplete((times, err) -> {
          for (int i = 0; i < times; i++) {
            expected.appendBuffer(chunk);
          }
          resp.resume();
        });
        Buffer cumulation = Buffer.buffer();
        resp.handler(cumulation::appendBuffer);
        resp.endHandler(v -> {
          assertEquals(expected, cumulation);
          testComplete();
        });
      }));

    await();
  }
}
