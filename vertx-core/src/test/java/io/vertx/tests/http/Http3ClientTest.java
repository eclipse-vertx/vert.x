package io.vertx.tests.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.StreamPriorityBase;

import java.util.List;
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

  @Override
  protected ServerBootstrap createServerForGet() {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForClientResetServerStream(boolean endServer) {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForStreamError() {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForConnectionDecodeError() {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForInvalidServerResponse() {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForClearText(List<String> requests, boolean withUpgrade) {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForConnectionWindowSize() {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForUpdateConnectionWindowSize() {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForStreamPriority(StreamPriorityBase requestStreamPriority, StreamPriorityBase responseStreamPriority) {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForStreamPriorityChange(StreamPriorityBase requestStreamPriority, StreamPriorityBase responseStreamPriority, StreamPriorityBase requestStreamPriority2, StreamPriorityBase responseStreamPriority2) {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForClientStreamPriorityNoChange(StreamPriorityBase streamPriority, Promise<Void> latch) {
    return null;
  }

  @Override
  protected ServerBootstrap createServerForServerStreamPriorityNoChange(StreamPriorityBase streamPriority) {
    return null;
  }
}
