package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

public class VertxHttp3StreamHandler extends Http3RequestStreamInboundHandler {
  private final HttpClientImpl client;
  private final ClientMetrics metrics;
  private final Object metric;
  private final Http3ClientConnection conn;
  private final Http3StreamImpl http3Stream;
  private ChannelHandlerContext chctx;

  private boolean read;

  private static final AttributeKey<Http3StreamImpl> HTTP3_MY_STREAM_KEY = AttributeKey.valueOf(Http3StreamImpl.class
    , "HTTP3MyStream");

  public VertxHttp3StreamHandler(
    HttpClientImpl client, ClientMetrics metrics,
    Object metric,
    Http3StreamImpl http3Stream) {
    this.client = client;
    this.metrics = metrics;
    this.metric = metric;

    this.http3Stream = http3Stream;
    this.conn = http3Stream.conn;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    this.chctx = ctx;
    super.handlerAdded(ctx);

    HttpClientOptions options = client.options();
    HttpClientMetrics met = client.metrics();
    boolean upgrade = false;

    if (metrics != null) {
      conn.metric(metric);
    }
    if (options.getHttp2ConnectionWindowSize() > 0) {
      conn.setWindowSize(options.getHttp2ConnectionWindowSize());
    }
    if (metrics != null) {
      if (!upgrade) {
        met.endpointConnected(metrics);
      }
    }
  }

  @Override
  protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
    read = true;
    System.err.print(frame.content().toString(CharsetUtil.US_ASCII));
    conn.onDataRead(ctx, controlStream(ctx).attr(HTTP3_MY_STREAM_KEY).get(), frame.content(), 0, true);
    checkFlush();
  }

  @Override
  protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
    read = true;
    conn.onHeadersRead(ctx, controlStream(ctx).attr(HTTP3_MY_STREAM_KEY).get(), frame.headers(), true);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    super.channelActive(ctx);
    controlStream(ctx).attr(HTTP3_MY_STREAM_KEY).set(http3Stream);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    read = false;
    super.channelReadComplete(ctx);
  }

  @Override
  protected void channelInputClosed(ChannelHandlerContext ctx) {
    ctx.close();
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    super.handlerRemoved(ctx);
    HttpClientMetrics met = client.metrics();
    if (metrics != null) {
      met.endpointDisconnected(metrics);
    }
    conn.tryEvict();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    super.exceptionCaught(ctx, cause);
  }

  private void checkFlush() {
    if (!read) {
      chctx.channel().flush();
    }
  }
}
