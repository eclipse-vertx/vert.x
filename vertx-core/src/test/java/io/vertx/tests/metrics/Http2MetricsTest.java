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
package io.vertx.tests.metrics;

import io.vertx.core.ThreadingModel;
import io.vertx.core.http.*;
import io.vertx.test.core.TestUtils;
import io.vertx.test.fakemetrics.*;
import io.vertx.test.http.HttpTestBase;
import io.vertx.tests.http.HttpOptionsFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(Parameterized.class)
public class Http2MetricsTest extends HttpMetricsTestBase {

  @Parameterized.Parameters
  public static Collection<Object[]> params() {
    ArrayList<Object[]> params = new ArrayList<>();
    // h2
    params.add(new Object[] { HttpOptionsFactory.createHttp2ClientOptions(), HttpOptionsFactory.createHttp2ServerOptions(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST), ThreadingModel.EVENT_LOOP });
    // h2 + worker
    params.add(new Object[] { HttpOptionsFactory.createHttp2ClientOptions(), HttpOptionsFactory.createHttp2ServerOptions(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST), ThreadingModel.WORKER });
    // h2c with upgrade
    params.add(new Object[] { new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2).setHttp2ClearTextUpgrade(true), new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST), ThreadingModel.EVENT_LOOP  });
    // h2c direct
    params.add(new Object[] { new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2).setHttp2ClearTextUpgrade(false), new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST), ThreadingModel.EVENT_LOOP  });
    return params;
  }

  private HttpClientOptions clientOptions;
  private HttpServerOptions serverOptions;

  public Http2MetricsTest(HttpClientOptions clientOptions, HttpServerOptions serverOptions, ThreadingModel threadingModel) {
    super(HttpVersion.HTTP_2, threadingModel);

    this.clientOptions = clientOptions;
    this.serverOptions = serverOptions.setHandle100ContinueAutomatically(true);
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return serverOptions;
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return clientOptions;
  }

  @Test
  public void testPushPromise() throws Exception {
    waitFor(2);
    int numBuffers = 10;
    int contentLength = numBuffers * 1000;
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble").onComplete(ar -> {
        HttpServerResponse pushedResp = ar.result();
        FakeHttpServerMetrics serverMetrics = FakeMetricsBase.getMetrics(server);
        HttpServerMetric serverMetric = serverMetrics.getResponseMetric("/wibble");
        assertNotNull(serverMetric);
        pushedResp.putHeader("content-length", "" + contentLength);
        AtomicInteger numBuffer = new AtomicInteger(numBuffers);
        vertx.setPeriodic(1, timerID -> {
          if (numBuffer.getAndDecrement() == 0) {
            pushedResp.end().onComplete(onSuccess(v -> {
              assertNull(serverMetrics.getResponseMetric("/wibble"));
              complete();
            }));
            vertx.cancelTimer(timerID);
          } else {
            pushedResp.write(TestUtils.randomBuffer(1000));
          }
        });
      });
    });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions());
    FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(client);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.pushHandler(pushedReq -> {
        HttpClientMetric metric = metrics.getMetric(pushedReq);
        assertNotNull(metric);
        pushedReq.response().onComplete(onSuccess(resp -> {
          resp.endHandler(v -> {
            assertNull(metrics.getMetric(pushedReq));
            complete();
          });
        }));
      })
        .end();
    }));
    await();
  }
}
