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
    testAddress = SocketAddress.inetSocketAddress(config.port(), config.host());
    requestOptions = new RequestOptions()
      .setHost(config.host())
      .setPort(config.port())
      .setURI(DEFAULT_TEST_URI);
  }

  @Override
  protected HttpServer createHttpServer() {
    return config.forServer().create(vertx);
  }

  @Override
  protected HttpClientAgent createHttpClient() {
    return config.forClient().create(vertx);
  }

  @Override
  protected HttpClientBuilder httpClientBuilder(Vertx vertx) {
    return config.forClient().builder(vertx);
  }
}
