package io.vertx.core.http.impl;

import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http3HeadersAdaptor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.ArrayDeque;
import java.util.Deque;


public class Http3ClientConnection extends Http3ConnectionBase implements HttpClientConnection {

  private Handler<Void> evictionHandler = DEFAULT_EVICTION_HANDLER;

  public ClientMetrics metrics;
  private HttpVersion version;
  private boolean isConnect;
  public HttpClientImpl client;
  private HttpClientOptions options;
  private Deque<Http3ClientStream> requests = new ArrayDeque<>();
  private Deque<Http3ClientStream> responses = new ArrayDeque<>();
  private boolean closed;
  private long expirationTimestamp;
  private boolean evicted;

  private long writeWindow;
  private boolean writeOverflow;

  public QuicStreamChannel quicStreamChannel;
  public QuicChannel quicChannel;

  public Http3ClientConnection(HttpClientImpl client, EventLoopContext context, VertxHttp3ConnectionHandler<? extends Http3ConnectionBase> connHandler, ClientMetrics metrics) {
    super(context, connHandler);
    this.client = client;
    this.metrics = metrics;
    this.quicStreamChannel = (QuicStreamChannel) connHandler.context().channel();
    this.quicChannel = (QuicChannel) connHandler.context().channel().parent();
  }

  @Override
  public HttpClientConnection evictionHandler(Handler<Void> handler) {
    return null;
  }

  @Override
  public HttpClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    return null;
  }

  @Override
  public long concurrency() {
    return 5;
  }

  @Override
  public HttpClientMetrics metrics() {
    return client.metrics();
  }

  @Override
  public void createStream(ContextInternal context, Handler<AsyncResult<HttpClientStream>> handler) {
    Future<HttpClientStream> fut;
    synchronized (this) {
      try {
        Http3ClientStream stream = createStream(context);
        fut = Future.succeededFuture(stream);
      } catch (Exception e) {
        fut = Future.failedFuture(e);
      }
    }
    context.emit(fut, handler);
  }

  private Http3ClientStream createStream(ContextInternal context) {
    return new Http3ClientStream(this, context, false, metrics);
  }

  @Override
  public boolean isValid() {
    return expirationTimestamp == 0 || System.currentTimeMillis() <= expirationTimestamp;
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return 0;
  }

  public void recycle() {
    int timeout = client.options().getHttp2KeepAliveTimeout();
    expirationTimestamp = timeout > 0 ? System.currentTimeMillis() + timeout * 1000L : 0L;
  }

  public void metricsEnd(HttpStream<?, ?, ?> stream) {
    if (metrics != null) {
      metrics.responseEnd(stream.metric, stream.bytesRead());
    }
  }

  @Override
  protected synchronized void onHeadersRead(
    VertxHttpStreamBase<?, ?, Http3Headers> stream, Http3Headers headers, StreamPriority streamPriority,
    boolean endOfStream) {
    if (!stream.isTrailersReceived()) {
      stream.onHeaders(headers, streamPriority);
      if (endOfStream) {
        stream.onEnd();
      }
    } else {
      stream.onEnd(new Http3HeadersAdaptor(headers));
    }
  }

  public void tryEvict() {
    if (!evicted) {
      evicted = true;
      evictionHandler.handle(null);
    }
  }

  public static VertxHttp3ConnectionHandler<Http3ClientConnection> createVertxHttp3ConnectionHandler(
    HttpClientImpl client,
    ClientMetrics metrics,
    EventLoopContext context,
    Object socketMetric) {
    HttpClientOptions options = client.options();
    HttpClientMetrics met = client.metrics();
    VertxHttp3ConnectionHandler<Http3ClientConnection> handler =
      new VertxHttp3ConnectionHandlerBuilder<Http3ClientConnection>()
        .http3InitialSettings(options.getHttp3InitialSettings())
        .channelInitializer(new QuicStreamChannelInitializer(context))
        .connectionFactory(connHandler -> {
          Http3ClientConnection conn = new Http3ClientConnection(client, context, connHandler, metrics);
          if (metrics != null) {
            Object m = socketMetric;
            conn.metric(m);
          }
          return conn;
        })
        .build(client, metrics, context, socketMetric);
    return handler;
  }
}
