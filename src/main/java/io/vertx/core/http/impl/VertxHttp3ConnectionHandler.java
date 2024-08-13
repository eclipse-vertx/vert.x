package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.function.Function;

public class VertxHttp3ConnectionHandler<C extends Http3ConnectionBase> extends Http3RequestStreamInboundHandler {
  private final Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory;
  private final HttpClientImpl client;
  private final ClientMetrics metrics;
  private final Object metric;
  private final Http3SettingsFrame http3InitialSettings;
  private C conn;
  private EventLoopContext context;
  private Promise<C> connectFuture;
  private final QuicStreamChannelInitializer quicStreamChannelInitializer;

  private ChannelHandlerContext chctx;
  private boolean read;
  private Handler<C> addHandler;
  private Handler<C> removeHandler;

  public static final AttributeKey<Http3ClientStream> HTTP3_MY_STREAM_KEY = AttributeKey.valueOf(Http3ClientStream.class
    , "HTTP3MyStream");

  public VertxHttp3ConnectionHandler(Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory,
                                     HttpClientImpl client, ClientMetrics metrics, Object metric,
                                     EventLoopContext context,
                                     QuicStreamChannelInitializer quicStreamChannelInitializer,
                                     Http3SettingsFrame http3InitialSettings) {
    this.client = client;
    this.metrics = metrics;
    this.metric = metric;
    this.connectionFactory = connectionFactory;
    this.http3InitialSettings = http3InitialSettings;
    this.quicStreamChannelInitializer = quicStreamChannelInitializer;
    connectFuture = new DefaultPromise<>(context.nettyEventLoop());

    quicStreamChannelInitializer.quicStreamChannelInitFuture().addListener((GenericFutureListener<Future<ChannelHandlerContext>>) future -> {
      ChannelHandlerContext ctx = future.get();
      this.chctx = ctx;
      this.conn = connectionFactory.apply(this);
      connectFuture.setSuccess(conn);
    });
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
    conn.onDataRead(ctx, controlStream(ctx).attr(HTTP3_MY_STREAM_KEY).get(), frame.content(), 0, true);
    checkFlush();
  }

  @Override
  protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
    read = true;
    conn.onHeadersRead(ctx, controlStream(ctx).attr(HTTP3_MY_STREAM_KEY).get(), frame.headers(), false);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    read = false;
    super.channelReadComplete(ctx);
  }

  public Future<C> connectFuture() {
    if (connectFuture == null) {
      throw new IllegalStateException();
    }
    return connectFuture;
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
//    conn.tryEvict();  //TODO: review
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

  public ChannelHandlerContext context() {
    return chctx;
  }

  /**
   * Set a handler to be called when the connection is unset from this handler.
   *
   * @param handler the handler to be notified
   * @return this
   */
  public VertxHttp3ConnectionHandler<C> removeHandler(Handler<C> handler) {
    removeHandler = handler;
    conn = null;
    return this;
  }

  /**
   * Set a handler to be called when the connection is set on this handler.
   *
   * @param handler the handler to be notified
   * @return this
   */
  public VertxHttp3ConnectionHandler<C> addHandler(Handler<C> handler) {
    this.addHandler = handler;
    return this;
  }

  public Http3ServerConnectionHandler createHttp3ServerConnectionHandler() {
    return new Http3ServerConnectionHandler(quicStreamChannelInitializer, null, null, http3InitialSettings, false);
  }

  public Http3ClientConnectionHandler createHttp3ClientConnectionHandler() {
    return new Http3ClientConnectionHandler(quicStreamChannelInitializer, null, null, http3InitialSettings, false);
  }
}
