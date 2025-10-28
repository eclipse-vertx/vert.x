package io.vertx.core.http.impl.http3;

import io.netty.handler.codec.http3.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpServerRequestImpl;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.quic.QuicServerImpl;
import io.vertx.core.spi.metrics.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class Http3Server implements HttpServer, MetricsProvider {

  private final VertxInternal vertx;
  private final Http3ServerOptions options;
  private volatile Handler<HttpServerRequest> requestHandler;
  private Handler<HttpConnection> connectionHandler;
  private QuicServer quicServer;
  private volatile int actualPort;

  public Http3Server(VertxInternal vertx, Http3ServerOptions options) {

    options = new Http3ServerOptions(options);
    options.getSslOptions().setApplicationLayerProtocols(Arrays.asList(Http3.supportedApplicationProtocols()));
    options.getTransportOptions().setInitialMaxData(10000000L);
    options.getTransportOptions().setInitialMaxStreamDataBidirectionalLocal(1000000L);
    options.getTransportOptions().setInitialMaxStreamDataBidirectionalRemote(1000000L);
    options.getTransportOptions().setInitialMaxStreamDataUnidirectional(1000000L);
    options.getTransportOptions().setInitialMaxStreamsBidirectional(100L);
    options.getTransportOptions().setInitialMaxStreamsUnidirectional(100L);

    this.vertx = vertx;
    this.options = options;
    this.actualPort = 0;
  }

  @Override
  public HttpServer requestHandler(Handler<HttpServerRequest> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    this.requestHandler = handler;
    return this;
  }

  @Override
  public Handler<HttpServerRequest> requestHandler() {
    return requestHandler;
  }

  @Override
  public HttpServer invalidRequestHandler(Handler<HttpServerRequest> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServer connectionHandler(Handler<HttpConnection> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    connectionHandler = handler;
    return this;
  }

  @Override
  public HttpServer webSocketHandshakeHandler(Handler<ServerWebSocketHandshake> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServer exceptionHandler(Handler<Throwable> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServer webSocketHandler(Handler<ServerWebSocket> handler) {
    if (actualPort > 0) {
      throw new IllegalStateException("Server already bound");
    }
    throw new UnsupportedOperationException();
  }

  @Override
  public Handler<ServerWebSocket> webSocketHandler() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Boolean> updateSSLOptions(ServerSSLOptions options, boolean force) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Boolean> updateTrafficShapingOptions(TrafficShapingOptions options) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<HttpServer> listen() {
    return listen(SocketAddress.inetSocketAddress(options.getPort(), options.getHost()));
  }

  private static class ConnectionHandler implements Handler<QuicConnection> {

    private final QuicServer transport;
    private final Handler<HttpServerRequest> requestHandler;
    private final Handler<HttpConnection> connectionHandler;
    private final boolean handle100ContinueAutomatically;
    private final int maxFormAttributeSize;
    private final int maxFormFields;
    private final int maxFormBufferedSize;

    public ConnectionHandler(QuicServer transport,
                             Handler<HttpServerRequest> requestHandler,
                             Handler<HttpConnection> connectionHandler,
                             boolean handle100ContinueAutomatically,
                             int maxFormAttributeSize,
                             int maxFormFields,
                             int maxFormBufferedSize) {
      this.transport = transport;
      this.requestHandler = requestHandler;
      this.connectionHandler = connectionHandler;
      this.handle100ContinueAutomatically = handle100ContinueAutomatically;
      this.maxFormAttributeSize = maxFormAttributeSize;
      this.maxFormFields = maxFormFields;
      this.maxFormBufferedSize = maxFormBufferedSize;
    }

    @Override
    public void handle(QuicConnection connection) {
      String host = connection.localAddress().host();
      int port = connection.localAddress().port();
      String serverOrigin = "https://" + host + ":" + port;

      QuicConnectionInternal connectionInternal = (QuicConnectionInternal) connection;

      HttpServerMetrics<?, ?, ?> metrics = (HttpServerMetrics<?, ?, ?>)((MetricsProvider)transport).getMetrics();

      Http3ServerConnection http3Connection = new Http3ServerConnection(connectionInternal, metrics);

      http3Connection.init();

      http3Connection.streamHandler(stream -> {
        HttpServerRequestImpl request = new HttpServerRequestImpl(requestHandler, stream, stream.context(),
          handle100ContinueAutomatically, maxFormAttributeSize,
          maxFormFields, maxFormBufferedSize, serverOrigin);
        request.init();
      });

      Handler<HttpConnection> handler = connectionHandler;
      if (handler != null) {
        ContextInternal ctx = connectionInternal.context();
        ctx.dispatch(http3Connection, handler);
      }
    }
  }

  @Override
  public Future<HttpServer> listen(SocketAddress address) {

    Handler<HttpServerRequest> requestHandler;
    Handler<HttpConnection> connectionHandler;

    BiFunction<QuicEndpointOptions, SocketAddress, TransportMetrics<?>> metricsProvider;
    VertxMetrics metrics = vertx.metrics();
    if (metrics != null) {
      metricsProvider = (quicEndpointOptions, socketAddress) -> metrics
        .createHttpServerMetrics((Http3ServerOptions) quicEndpointOptions, socketAddress);
    } else {
      metricsProvider = null;
    }

    synchronized (this) {
      if (quicServer != null) {
        return vertx.getOrCreateContext().failedFuture(new IllegalStateException("Already listening on port " + address.port()));
      }
      requestHandler = this.requestHandler;
      connectionHandler = this.connectionHandler;
      quicServer = new QuicServerImpl(vertx, metricsProvider, options);
    }

    if (requestHandler == null) {
      return vertx.getOrCreateContext().failedFuture(new IllegalStateException("Set request handler first"));
    }

    quicServer.handler(new ConnectionHandler(quicServer, requestHandler, connectionHandler,
      options.isHandle100ContinueAutomatically(), options.getMaxFormAttributeSize(), options.getMaxFormFields(),
      options.getMaxFormBufferedBytes()));
    return quicServer
      .bind(address)
      .map(port -> {
        actualPort = port;
        return this;
      });
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    QuicServer s;
    synchronized (this) {
      s = quicServer;
      if (s == null) {
        return vertx.getOrCreateContext().succeededFuture();
      }
      quicServer = null;
    }
    return s.shutdown(Duration.ofMillis(unit.toMillis(timeout)));
  }

  @Override
  public int actualPort() {
    return actualPort;
  }

  @Override
  public Metrics getMetrics() {
    QuicServerImpl s;
    synchronized (this) {
      s = (QuicServerImpl) quicServer;
    }
    return s == null ? null : s.getMetrics();
  }
}
