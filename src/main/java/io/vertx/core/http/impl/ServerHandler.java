package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.impl.HandlerHolder;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServerHandler extends VertxHttpHandler<ServerConnection> {

  private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

  private final SSLHelper sslHelper;
  private final HttpServerOptions options;
  private final String serverOrigin;
  private final HttpServerMetrics metrics;
  private final HandlerHolder<HttpHandlers> holder;

  public ServerHandler(SSLHelper sslHelper, HttpServerOptions options, String serverOrigin, HandlerHolder<HttpHandlers> holder, HttpServerMetrics metrics) {
    this.holder = holder;
    this.metrics = metrics;
    this.sslHelper = sslHelper;
    this.options = options;
    this.serverOrigin = serverOrigin;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    super.handlerAdded(ctx);
    ServerConnection conn = new ServerConnection(holder.context.owner(),
      sslHelper,
      options,
      ctx,
      holder.context,
      serverOrigin,
      metrics);
    setConnection(conn);
    conn.requestHandler(holder.handler.requesthHandler);
    holder.context.executeFromIO(() -> {
      if (metrics != null) {
        conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
      }
      Handler<HttpConnection> connHandler = holder.handler.connectionHandler;
      if (connHandler != null) {
        connHandler.handle(conn);
      }
    });
  }

  @Override
  protected void handleMessage(ServerConnection conn, ContextImpl context, ChannelHandlerContext chctx, Object msg) throws Exception {
    conn.handleMessage(msg);
  }

  WebSocketServerHandshaker createHandshaker(ServerConnection conn, Channel ch, HttpRequest request) {
    // As a fun part, Firefox 6.0.2 supports Websockets protocol '7'. But,
    // it doesn't send a normal 'Connection: Upgrade' header. Instead it
    // sends: 'Connection: keep-alive, Upgrade'. Brilliant.
    String connectionHeader = request.headers().get(io.vertx.core.http.HttpHeaders.CONNECTION);
    if (connectionHeader == null || !connectionHeader.toLowerCase().contains("upgrade")) {
      HttpServerImpl.sendError("\"Connection\" must be \"Upgrade\".", BAD_REQUEST, ch);
      return null;
    }

    if (request.getMethod() != HttpMethod.GET) {
      HttpServerImpl.sendError(null, METHOD_NOT_ALLOWED, ch);
      return null;
    }

    try {

      WebSocketServerHandshakerFactory factory =
        new WebSocketServerHandshakerFactory(HttpServerImpl.getWebSocketLocation(ch.pipeline(), request), conn.options.getWebsocketSubProtocols(), false,
          conn.options.getMaxWebsocketFrameSize(), conn.options.isAcceptUnmaskedFrames());
      WebSocketServerHandshaker shake = factory.newHandshaker(request);

      if (shake == null) {
        log.error("Unrecognised websockets handshake");
        WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ch);
      }

      return shake;
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }
}
