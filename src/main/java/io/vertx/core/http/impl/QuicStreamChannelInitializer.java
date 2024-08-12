package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.spi.metrics.ClientMetrics;

public class QuicStreamChannelInitializer extends ChannelInitializer<QuicStreamChannel> {

  private final HttpClientImpl client;
  private final ClientMetrics metrics;
  private final EventLoopContext context;
  private final Object metric;
  private final PromiseInternal<HttpClientConnection> promise;

  private ChannelHandlerContext ctx;

  public QuicStreamChannelInitializer(HttpClientImpl client, ClientMetrics metrics, EventLoopContext context,
                                      Object metric, PromiseInternal<HttpClientConnection> promise) {
    this.client = client;
    this.metrics = metrics;
    this.context = context;
    this.metric = metric;
    this.promise = promise;
  }

  @Override
  protected void initChannel(QuicStreamChannel ch) {
    Http3ClientConnection conn = new Http3ClientConnection(ch.parent(), ch, ctx, promise, client, metrics, context,
      metric);
    promise.tryComplete(conn);
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    this.ctx = ctx;
    super.handlerAdded(ctx);
  }
}
