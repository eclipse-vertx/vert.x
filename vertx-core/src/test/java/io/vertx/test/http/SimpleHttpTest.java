package io.vertx.test.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;

public class SimpleHttpTest extends AbstractHttpTest {

  protected final HttpConfig config;

  public SimpleHttpTest(HttpConfig config) {
    this.config = config;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    HttpServerOptions baseServerOptions = config.createBaseServerOptions();
    testAddress = SocketAddress.inetSocketAddress(baseServerOptions.getPort(), baseServerOptions.getHost());
    requestOptions = new RequestOptions()
      .setHost(baseServerOptions.getHost())
      .setPort(baseServerOptions.getPort())
      .setURI(DEFAULT_TEST_URI);
  }

  @Override
  protected HttpServer createHttpServer() {
    return createHttpServer(config.createBaseServerOptions());
  }

  @Override
  protected HttpClientAgent createHttpClient() {
    return createHttpClient(config.createBaseClientOptions());
  }

  @Override
  protected HttpClientBuilder httpClientBuilder(Vertx vertx) {
    return vertx.httpClientBuilder().with(config.createBaseClientOptions());
  }
}
