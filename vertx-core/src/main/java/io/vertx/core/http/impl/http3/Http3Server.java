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
    return listen(SocketAddress.inetSocketAddress(443, "0.0.0.0"));
  }

  private static class ConnectionHandler implements Handler<QuicConnection> {

    private final Handler<HttpServerRequest> requestHandler;
    private final Handler<HttpConnection> connectionHandler;
    private final boolean handle100ContinueAutomatically;

    public ConnectionHandler(Handler<HttpServerRequest> requestHandler,
                             Handler<HttpConnection> connectionHandler,
                             boolean handle100ContinueAutomatically) {
      this.requestHandler = requestHandler;
      this.connectionHandler = connectionHandler;
      this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    }

    @Override
    public void handle(QuicConnection connection) {
      String host = connection.localAddress().host();
      int port = connection.localAddress().port();
      String serverOrigin = "https://" + host + ":" + port;

      QuicConnectionInternal connectionInternal = (QuicConnectionInternal) connection;

      Http3ServerConnection http3Connection = new Http3ServerConnection(connectionInternal);

      http3Connection.init();

      http3Connection.streamHandler(stream -> {
        HttpServerRequestImpl request = new HttpServerRequestImpl(requestHandler, stream, stream.context(),
          handle100ContinueAutomatically, HttpServerOptions.DEFAULT_MAX_FORM_ATTRIBUTE_SIZE,
          HttpServerOptions.DEFAULT_MAX_FORM_FIELDS, HttpServerOptions.DEFAULT_MAX_FORM_BUFFERED_SIZE, serverOrigin);
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

    synchronized (this) {
      if (quicServer != null) {
        return vertx.getOrCreateContext().failedFuture(new IllegalStateException("Already listening on port " + address.port()));
      }
      requestHandler = this.requestHandler;
      connectionHandler = this.connectionHandler;
      quicServer = QuicServer.create(vertx, options);
    }

    if (requestHandler == null) {
      return vertx.getOrCreateContext().failedFuture(new IllegalStateException("Set request handler first"));
    }

    quicServer.handler(new ConnectionHandler(requestHandler, connectionHandler, options.isHandle100ContinueAutomatically()));
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
}
