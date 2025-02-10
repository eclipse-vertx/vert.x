package io.vertx.tests.net;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.tests.http.HttpOptionsFactory;

import static io.vertx.test.http.HttpTestBase.*;

public class Http2NetTest extends NetTest {

  protected NetServerOptions createNetServerOptions() {
    return new NetServerOptions();
  }

  protected NetClientOptions createNetClientOptions() {
    return new NetClientOptions();
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createHttp2ClientOptions();
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory.createHttp2ServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }
}
