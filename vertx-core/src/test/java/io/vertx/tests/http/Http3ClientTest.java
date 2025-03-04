package io.vertx.tests.http;

import io.netty.channel.EventLoopGroup;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;

import java.util.concurrent.TimeUnit;


public class Http3ClientTest extends HttpClientTest {
  @Override
  public void setUp() throws Exception {
    eventLoopGroups.clear();
    serverOptions = HttpOptionsFactory.createH3HttpServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
    clientOptions = HttpOptionsFactory.createH3HttpClientOptions();
    super.setUp();
  }

  @Override
  protected void configureDomainSockets() throws Exception {
    // Nope
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    for (EventLoopGroup eventLoopGroup : eventLoopGroups) {
      eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
    }
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return serverOptions;
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return clientOptions;
  }

}
