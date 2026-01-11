package io.vertx.core.http.impl.http3;

import io.netty.channel.*;
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http3.*;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.vertx.core.Handler;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpServerStream;
import io.vertx.core.http.impl.observability.ServerStreamObserver;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.function.Supplier;

public class Http3ServerConnection extends Http3Connection implements HttpServerConnection {

  private final Supplier<ContextInternal> streamContextProvider;
  private final HttpServerMetrics<?, ?, ?> metrics;
  private Handler<HttpServerStream> streamHandler;
  private QuicStreamChannel outboundControlStream;

  public Http3ServerConnection(QuicConnectionInternal connection, HttpServerMetrics<?, ?, ?> metrics) {
    super(connection);

    this.streamContextProvider = connection.context()::duplicate;
    this.metrics = metrics;
  }

  void handleStream(QuicStreamInternal quicStream) {
    ContextInternal streamContext = streamContextProvider.get();
    VertxTracer<?, ?> tracer = context.owner().tracer();
    ServerStreamObserver observer;
    if (metrics != null || tracer != null) {
      observer = new ServerStreamObserver(streamContext, metrics, tracer, connection.metric(), TracingPolicy.PROPAGATE, connection.remoteAddress());
    } else {
      observer = null;
    }
    Http3ServerStream httpStream = new Http3ServerStream(this, quicStream, streamContext, observer);
    httpStream.init();
    registerStream(httpStream);
    Handler<HttpServerStream> handler = streamHandler;
    streamContext.emit(httpStream, handler);
  }

  public void init() {

    super.init();

    Http3ServerConnectionHandler http3Handler = new Http3ServerConnectionHandler(
      new ChannelInitializer<QuicStreamChannel>() {
        @Override
        protected void initChannel(QuicStreamChannel ch) {
          // Nothing to do
        }
      },
      new ChannelInitializer<QuicStreamChannel>() {
        @Override
        protected void initChannel(QuicStreamChannel ch) {
          outboundControlStream = ch;
        }
      },
      null,
      null,
      true
    );

    ChannelPipeline pipeline = connection.channelHandlerContext().pipeline();
    pipeline.addBefore("handler", "http3", http3Handler);
  }

  @Override
  public HttpServerConnection streamHandler(Handler<HttpServerStream> handler) {
    streamHandler = handler;
    return this;
  }

  @Override
  public Headers<CharSequence, CharSequence, ?> newHeaders() {
    return new DefaultHttp3Headers();
  }

  @Override
  public boolean supportsSendFile() {
    return false;
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return connection.channelHandlerContext();
  }

  @Override
  public String indicatedServerName() {
    return connection.indicatedServerName();
  }
}
