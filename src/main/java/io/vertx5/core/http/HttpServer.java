package io.vertx5.core.http;

import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.http.HttpObject;
import io.netty5.handler.codec.http.HttpServerCodec;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx5.core.net.NetServer;
import io.vertx5.core.net.SocketAddress;

public class HttpServer {

  private final ContextInternal context;
  private final NetServer tcpServer;
  private Handler<HttpServerStream> streamHandler;

  public HttpServer(ContextInternal context) {

    NetServer server = new NetServer(context);
    server.connectHandler(so -> {
      ChannelHandlerContext chctx = so.channelHandlerContext();
      ChannelPipeline pipeline = chctx.pipeline();
      pipeline.addBefore("vertx", "http", new HttpServerCodec());
      Handler<HttpServerStream> handler = streamHandler;
      if (handler == null) {
        handler = req -> {
          req.status(404).end();
        };
      }
      HttpServerConnection connection = new HttpServerConnection(so, handler);
      so.messageHandler(msg -> {
        if (msg instanceof HttpObject) {
          connection.handleObject((HttpObject) msg);
        }
      });
    });

    this.context = context;
    this.tcpServer = server;
  }

  public HttpServer streamHandler(Handler<HttpServerStream> handler) {
    streamHandler = handler;
    return this;
  }

  public Future<SocketAddress> listen(int port, String host) {
    return tcpServer.listen(port, host);
  }
}
