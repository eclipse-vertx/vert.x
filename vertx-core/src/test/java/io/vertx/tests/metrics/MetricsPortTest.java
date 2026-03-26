package io.vertx.tests.metrics;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.test.core.TestUtils;
import io.vertx.test.fakemetrics.FakeHttpServerMetrics;
import io.vertx.test.fakemetrics.FakeMetricsBase;
import io.vertx.test.fakemetrics.FakeMetricsFactory;
import io.vertx.test.http.HttpTestBase;
import org.junit.Test;

public class MetricsPortTest extends HttpTestBase {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return new HttpServerOptions().setPort(0).setHost(DEFAULT_HTTP_HOST);
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.setMetricsOptions(new MetricsOptions().setEnabled(true));
    return options;
  }

  @Override
  protected Vertx createVertx(VertxOptions options) {
    return Vertx.builder().with(options)
      .withMetrics(new FakeMetricsFactory())
      .build();
  }

  @Test
  public void actualPortInMetricsWhenDynamicPortIsUsed() throws Exception {
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);

      assertEquals(server.actualPort(), metrics.socketAddress().port());

      req.response().end();
      testComplete();
    });

    startServer();

    var requestOptions = new RequestOptions()
      .setPort(server.actualPort());

    client.request(new RequestOptions(requestOptions).setURI(TestUtils.randomAlphaString(16))).onComplete(onSuccess(HttpClientRequest::send));
    await();
  }

}
