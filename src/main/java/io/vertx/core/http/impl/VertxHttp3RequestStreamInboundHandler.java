package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.spi.metrics.ClientMetrics;

public class VertxHttp3RequestStreamInboundHandler extends Http3RequestStreamInboundHandler {
  private final EventLoopContext context;
  private final PromiseInternal<HttpClientConnection> promise;
  private final HttpClientImpl client;
  private final ClientMetrics metrics;
  private final boolean upgrade;
  private final Object metric;

  public VertxHttp3RequestStreamInboundHandler(
    PromiseInternal<HttpClientConnection> promise, HttpClientImpl client, ClientMetrics metrics,
    EventLoopContext context, boolean upgrade, Object metric) {
    this.promise = promise;
    this.context = context;
    this.client = client;
    this.metrics = metrics;
    this.upgrade = upgrade;
    this.metric = metric;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    super.handlerAdded(ctx);
    promise.complete(new Http3ClientConnection((QuicChannel) ctx.channel().parent(), (QuicStreamChannel) ctx.channel(),
      ctx, promise, client, metrics, context, upgrade, metric));
  }

  @Override
  protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
    ReferenceCountUtil.release(frame);
  }

  @Override
  protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
    System.err.print(frame.content().toString(CharsetUtil.US_ASCII));
    ReferenceCountUtil.release(frame);
  }

  @Override
  protected void channelInputClosed(ChannelHandlerContext ctx) {
    ctx.close();
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof SslHandshakeCompletionEvent) {
      SslHandshakeCompletionEvent completion = (SslHandshakeCompletionEvent) evt;
      if (completion.isSuccess()) {
        ctx.pipeline().remove(this);
        promise.tryComplete(null);
      } else {
        promise.tryFail(completion.cause());
      }
    } else {
      ctx.fireUserEventTriggered(evt);
    }
  }
}
