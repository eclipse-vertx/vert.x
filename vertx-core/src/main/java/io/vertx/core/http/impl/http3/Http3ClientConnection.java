package io.vertx.core.http.impl.http3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3RequestStreamInitializer;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.observability.ClientStreamObserver;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.function.Consumer;
import java.util.function.Function;

public class Http3ClientConnection extends Http3Connection implements HttpClientConnection {

  private final HostAndPort authority;
  private final ClientMetrics<Object, HttpRequest, HttpResponse> metrics;
  private Handler<Void> evictionHandler;

  public Http3ClientConnection(QuicConnectionInternal connection, HostAndPort authority, ClientMetrics<Object, HttpRequest, HttpResponse> metrics) {
    super(connection);

    this.authority = authority;
    this.metrics = metrics;
  }

  public void init() {

    super.init();

    Http3ClientConnectionHandler http3Handler = new Http3ClientConnectionHandler();

    ChannelPipeline pipeline = connection.channelHandlerContext().pipeline();

    pipeline.addBefore("handler", "http3", http3Handler);



  }

  @Override
  public MultiMap newHttpRequestHeaders() {
    return new HttpRequestHeaders(new DefaultHttp3Headers());
  }

  @Override
  public long activeStreams() {
    return 0;
  }

  @Override
  public long concurrency() {
    // For now hardcode
    return 10;
  }

  @Override
  protected void handleClosed() {
    Handler<Void> handler = evictionHandler;
    if (handler != null) {
      handler.handle(null);
    }
    super.handleClosed();
  }

  @Override
  public HostAndPort authority() {
    return authority;
  }

  @Override
  public HttpClientConnection evictionHandler(Handler<Void> handler) {
    evictionHandler = handler;
    return this;
  }

  @Override
  public HttpClientConnection invalidMessageHandler(Handler<Object> handler) {
    return null;
  }

  @Override
  public HttpClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    return null;
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return null;
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    return connection.openStream(context, true, new Function<Consumer<QuicStreamChannel>, ChannelInitializer<QuicStreamChannel>>() {
      @Override
      public ChannelInitializer<QuicStreamChannel> apply(Consumer<QuicStreamChannel> quicStreamChannelConsumer) {
        return new Http3RequestStreamInitializer() {
          @Override
          protected void initRequestStream(QuicStreamChannel ch) {
            quicStreamChannelConsumer.accept(ch);
          }
        };
      }
    }).map(stream -> {
      QuicStreamInternal streamInternal = (QuicStreamInternal) stream;
      VertxTracer<?, ?> tracer = context.owner().tracer();
      ClientStreamObserver observer;
      if (metrics != null || tracer != null) {
        Object metric;
        if (metrics != null) {
          metric = metrics.init();
        } else {
          metric = null;
        }
        observer = new ClientStreamObserver(context, TracingPolicy.PROPAGATE, metrics, metric, connection.metrics(),
          connection.metric(), tracer, connection.remoteAddress());
      } else {
        observer = null;
      }
      Http3ClientStream http3Stream = new Http3ClientStream(this, streamInternal, context, observer);
      http3Stream.init();
      registerStream(http3Stream);
      return http3Stream;
    });
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public boolean isValid() {
    // For now, no keep alive timeout
    return true;
  }

  @Override
  public Object metric() {
    return null;
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return 0;
  }

  @Override
  public String indicatedServerName() {
    return "";
  }

}
