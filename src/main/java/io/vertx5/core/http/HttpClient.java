package io.vertx5.core.http;

import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.http.HttpClientCodec;
import io.netty5.handler.codec.http.HttpObject;
import io.vertx.core.Future;
import io.vertx5.core.Vertx;
import io.vertx5.core.net.NetClient;

public class HttpClient {

  private Vertx vertx;
  private final NetClient client;

  public HttpClient(Vertx vertx) {
    this.vertx = vertx;
    this.client = vertx.createNetClient();
  }

  public Future<HttpClientConnection> connect(int port, String host) {
    return client.connect(port, host)
      .map(so -> {
      ChannelHandlerContext chctx = so.channelHandlerContext();
      ChannelPipeline pipeline = chctx.pipeline();
      pipeline.addBefore("vertx", "http", new HttpClientCodec());
      HttpClientConnection connection = new HttpClientConnection(so);
      so.messageHandler(msg -> {
        if (msg instanceof HttpObject) {
          connection.handleObject((HttpObject) msg);
        }
      });
      return connection;
    });
  }

}
