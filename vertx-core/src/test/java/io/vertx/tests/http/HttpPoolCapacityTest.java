package io.vertx.tests.http;

import io.vertx.core.Future;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.PoolOptions;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.http.HttpConfig;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class HttpPoolCapacityTest extends VertxTestBase {

  @Test
  public void testCapacity1() throws Exception {
    List<HttpVersion> versions = testSome(5, 1, 4, 100, 5);
    assertEquals(List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2), versions);
  }

  @Test
  public void testCapacity2() throws Exception {
    List<HttpVersion> versions = testSome(5, 1, 1, 100, 5);
    assertEquals(List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2, HttpVersion.HTTP_2, HttpVersion.HTTP_2, HttpVersion.HTTP_2), versions);
  }

  @Test
  public void testCapacity3() throws Exception {
    List<HttpVersion> versions = testSome(5, 1, 2, 3, 5);
    assertEquals(List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1, HttpVersion.HTTP_2, HttpVersion.HTTP_2, HttpVersion.HTTP_2), versions);
  }

  public List<HttpVersion> testSome(int http1xPoolSize, int http2PoolSize, int http1Concurrency, int http2Concurrency, int numberOfRequests) throws Exception {
    HttpServerOptions http2Options = HttpConfig.H2.CODEC.createBaseServerOptions();
    HttpServerOptions http1Options = new HttpServerOptions(http2Options).setUseAlpn(false);
    http2Options.setInitialSettings(new Http2Settings().setMaxConcurrentStreams(http2Concurrency));
    HttpServer http1Server = vertx.createHttpServer(http1Options);
    HttpServer http2Server = vertx.createHttpServer(http2Options);
    http1Server.requestHandler(request -> {
    });
    http2Server.requestHandler(request -> {
    });
    http1Server.listen().await();
    http2Server.listen().await();

    HttpClientOptions clientOptions = HttpConfig.H2.CODEC.createBaseClientOptions();
    clientOptions.setPipelining(true);
    clientOptions.setPipeliningLimit(http1Concurrency);
    HttpClientAgent client = vertx.createHttpClient(clientOptions, new PoolOptions()
      .setHttp1MaxSize(http1xPoolSize)
      .setHttp2MaxSize(http2PoolSize));

    List<HttpVersion> versions = new ArrayList<>();
    for (int i = 0;i < numberOfRequests;i++) {
      HttpVersion version = client
        .request(HttpMethod.GET, http1Server.actualPort(), "localhost", "/")
        .compose(request -> request
          .end()
          .map(request.version()))
        .await();
      versions.add(version);
    }
    http1Server.close().await();
    http2Server.close().await();
    return versions;
  }
}
