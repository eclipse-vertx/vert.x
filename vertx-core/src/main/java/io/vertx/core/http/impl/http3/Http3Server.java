package io.vertx.core.http.impl.http3;

import io.netty.handler.codec.http3.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpServerRequestImpl;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.net.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public class Http3Server implements HttpServer {

  private final VertxInternal vertx;
  private final Http3ServerOptions options;
  private volatile Handler<HttpServerRequest> requestHandler;
  private QuicServer quicServer;

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
  }

  @Override
  public HttpServer requestHandler(Handler<HttpServerRequest> handler) {
    this.requestHandler = handler;
    return this;
  }

  @Override
  public Handler<HttpServerRequest> requestHandler() {
    return requestHandler;
  }

  @Override
  public HttpServer invalidRequestHandler(Handler<HttpServerRequest> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServer connectionHandler(Handler<HttpConnection> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServer webSocketHandshakeHandler(Handler<ServerWebSocketHandshake> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServer exceptionHandler(Handler<Throwable> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServer webSocketHandler(Handler<ServerWebSocket> handler) {
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
    return listen(SocketAddress.inetSocketAddress(443, "0.0.0.0"));
  }

  private void handleConnection(QuicConnection connection) {
    String host = connection.localAddress().host();
    int port = connection.localAddress().port();
    String serverOrigin = "https://" + host + ":" + port;

    QuicConnectionInternal connectionInternal = (QuicConnectionInternal) connection;

    Http3ServerConnection http3Connection = new Http3ServerConnection(connectionInternal);

    http3Connection.init();

    http3Connection.streamHandler(stream -> {
      HttpServerRequestImpl request = new HttpServerRequestImpl(requestHandler, stream, stream.context(),
        options.isHandle100ContinueAutomatically(), HttpServerOptions.DEFAULT_MAX_FORM_ATTRIBUTE_SIZE,
        HttpServerOptions.DEFAULT_MAX_FORM_FIELDS, HttpServerOptions.DEFAULT_MAX_FORM_BUFFERED_SIZE, serverOrigin);
      request.init();
    });
  }

  @Override
  public Future<HttpServer> listen(SocketAddress address) {
    synchronized (this) {
      if (quicServer != null) {
        return vertx.getOrCreateContext().failedFuture("Already listening on port " + address.port());
      }
      quicServer = QuicServer.create(vertx, options);
    }
    quicServer.handler(this::handleConnection);
    return quicServer
      .bind(address)
      .map(this);
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
    throw new UnsupportedOperationException();
  }
}
