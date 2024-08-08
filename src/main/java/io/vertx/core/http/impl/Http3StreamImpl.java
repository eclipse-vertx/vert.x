package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.VertxDefaultHttp3Headers;
import io.vertx.core.http.impl.headers.VertxDefaultHttpHeaders;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.tracing.TracingPolicy;

class Http3StreamImpl extends HttpStreamImpl<Http3ClientConnection, QuicStreamChannel, Http3Headers> {
  Http3StreamImpl(Http3ClientConnection conn, ContextInternal context, boolean push,
                  VertxHttpConnectionDelegate<QuicStreamChannel, Http3Headers> connectionDelegate,
                  ClientMetrics metrics) {
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
//    return this.conn.handler.encoder().connection().local().lastStreamCreated(); //V2
    return this.conn.quicStreamChannel != null ? (int) this.conn.quicStreamChannel.streamId() : 0;  //TODO: review this!
  }

  @Override
  protected void createStream2(int id, boolean b, Handler<AsyncResult<QuicStreamChannel>> onComplete) throws HttpException {
    Http3.newRequestStream(conn.quicChannel, new Http3RequestStreamInboundHandler() {
        private boolean read;

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
          System.err.print(frame.content().toString(CharsetUtil.US_ASCII));
          conn.onDataRead(ctx, connectionDelegate.getStreamId(), frame.content(), 0, true);
          read = true;
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
          conn.onHeadersRead(ctx, id, frame.headers(), true);
        }

        @Override
        protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
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
    //TODO: review this!
    throw new RuntimeException("Method not implemented");
//    return conn.client.options().getTracingPolicy();
  }

  @Override
  protected boolean isTryUseCompression() {
    //TODO: review this!
//    throw new RuntimeException("Method not implemented");
//    return this.conn.client.options().isTryUseCompression();
    return true;
  }

  @Override
  VertxDefaultHttpHeaders<Http3Headers> createHttpHeadersWrapper() {
    return new VertxDefaultHttp3Headers();
  }










  //TODO: implement the following methods correctly!

  @Override
  public Future<Void> write(Buffer data) {
    return super.write(data);
  }

  @Override
  public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
    super.write(data, handler);
  }

  @Override
  public Future<Void> end(Buffer data) {
    return super.end(data);
  }

  @Override
  public void end(Buffer data, Handler<AsyncResult<Void>> handler) {
    super.end(data, handler);
  }

  @Override
  public Future<Void> end() {
    return super.end();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    super.end(handler);
  }

}
