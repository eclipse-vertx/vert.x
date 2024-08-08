package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.VertxDefaultHttp3Headers;
import io.vertx.core.http.impl.headers.VertxDefaultHttpHeaders;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.tracing.TracingPolicy;

class Http3StreamImpl extends HttpStreamImpl<Http3ClientConnection, QuicStreamChannel, Http3Headers> {
  Http3StreamImpl(Http3ClientConnection conn, ContextInternal context, boolean push,
                  VertxHttpConnectionDelegate<QuicStreamChannel, Http3Headers> connectionDelegate,
                  ClientMetrics<?, ?, ?, ?> metrics) {
    super(conn, context, push, connectionDelegate, metrics);
  }

  @Override
  public HttpClientConnection connection() {
    return conn;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_3;
  }

  @Override
  void metricsEnd(HttpStream<?, ?, ?> stream) {
    conn.metricsEnd(stream);
  }

  @Override
  void recycle() {
    conn.recycle();
  }

  @Override
  int lastStreamCreated() {
    return this.conn.quicStreamChannel != null ? (int) this.conn.quicStreamChannel.streamId() : 0;
  }

  @Override
  protected void createStream2(int id, boolean b, Handler<AsyncResult<QuicStreamChannel>> onComplete) {
    Http3.newRequestStream(conn.quicChannel, new Http3RequestStreamInboundHandler() {
        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
          System.err.print(frame.content().toString(CharsetUtil.US_ASCII));
          Attribute<Object> stream = controlStream(ctx).attr(AttributeKey.valueOf("MY_STREAM"));
          conn.onDataRead(ctx, (Http3StreamImpl) stream.get(), frame.content(), 0, true);
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
          Attribute<Object> stream = controlStream(ctx).attr(AttributeKey.valueOf("MY_STREAM"));
          conn.onHeadersRead(ctx, (Http3StreamImpl) stream.get(), frame.headers(), true);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
          super.channelActive(ctx);
          Attribute<Object> streamAttr = controlStream(ctx).attr(AttributeKey.newInstance("MY_STREAM"));
          streamAttr.set(Http3StreamImpl.this);
        }

        @Override
        protected void channelInputClosed(ChannelHandlerContext ctx) {
          ctx.close();
        }
      })
      .addListener((GenericFutureListener<io.netty.util.concurrent.Future<QuicStreamChannel>>) quicStreamChannelFuture -> {
        QuicStreamChannel quicStreamChannel = quicStreamChannelFuture.get();
        onComplete.handle(Future.succeededFuture(quicStreamChannel));
      });
  }

  @Override
  protected TracingPolicy getTracingPolicy() {
    return conn.client.options().getTracingPolicy();
  }

  @Override
  protected boolean isTryUseCompression() {
    return this.conn.client.options().isTryUseCompression();
  }

  @Override
  VertxDefaultHttpHeaders<Http3Headers> createHttpHeadersWrapper() {
    return new VertxDefaultHttp3Headers();
  }

}
