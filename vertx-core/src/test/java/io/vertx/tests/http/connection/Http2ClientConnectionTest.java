/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.connection;

import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpClientConnection;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpConnectOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.Origin;
import io.vertx.test.core.TestUtils;
import io.vertx.tests.http.Http2TestBase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class Http2ClientConnectionTest extends HttpClientConnectionTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return Http2TestBase.createHttp2ServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST)
      .setInitialSettings(new Http2Settings().setMaxConcurrentStreams(10));
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return Http2TestBase.createHttp2ClientOptions();
  }

  @Test
  public void testAlternateServiceHandlerConnectionStream() throws Exception {
    testAlternateServiceHandler(new Origin("https", "example.com", 334));
  }

  @Test
  public void testGoAwayFailsQueuedRequests() throws Exception {
    server.requestHandler(req -> req.connection().shutdown(1, TimeUnit.HOURS));
    startServer(testAddress);
    HttpConnectOptions connect = new HttpConnectOptions().setServer(testAddress).setHost(requestOptions.getHost()).setPort(requestOptions.getPort());
    HttpClientConnection conn = client.connect(connect).await();
    Promise<Void> goAway = Promise.promise();
    conn.goAwayHandler(v -> goAway.tryComplete());
    conn.request().compose(req -> req.send());
    goAway.future().await();
    int num = (int) conn.maxActiveStreams();
    waitFor(num);
    for (int i = 0;i < num;i++) {
      conn.request()
        .compose(req -> req.send(Buffer.buffer("body")))
        .onComplete(TestUtils.onFailure(err -> complete()));
    }
    await(10, TimeUnit.SECONDS);
  }
}
