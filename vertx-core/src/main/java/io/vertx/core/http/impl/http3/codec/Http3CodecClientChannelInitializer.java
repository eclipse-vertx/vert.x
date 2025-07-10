package io.vertx.core.http.impl.http3.codec;

import io.netty.channel.Channel;
import io.vertx.core.Promise;
import io.vertx.core.http.impl.HttpClientBase;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.http3.Http3ClientChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.ClientMetrics;

public class Http3CodecClientChannelInitializer implements Http3ClientChannelInitializer {

  private HttpClientBase client;
  private ClientMetrics metrics;
  private boolean pooled;
  private long maxLifetime;
  private HostAndPort authority;

  public Http3CodecClientChannelInitializer(HttpClientBase client, ClientMetrics metrics, boolean pooled, long maxLifetime, HostAndPort authority) {
    this.client = client;
    this.metrics = metrics;
    this.pooled = pooled;
    this.maxLifetime = maxLifetime;
    this.authority = authority;
  }

  @Override
  public void http3Connected(ContextInternal context,
                              Object metric,
                              Channel ch,
                              PromiseInternal<HttpClientConnection> promise) {
    VertxHttp3ConnectionHandler<Http3ClientConnectionImpl> clientHandler;
    try {
      clientHandler = Http3ClientConnectionImpl.createHttp3ConnectionHandler(client, metrics, context, false, metric, authority, pooled, maxLifetime);
      ch.pipeline().addLast("handler", clientHandler.getHttp3ConnectionHandler());
//      ch.pipeline().addLast(clientHandler.getUserEventHandler());
      ch.pipeline().addLast(clientHandler);
      ch.flush();
    } catch (Exception e) {
      connectFailed(ch, e, promise);
      return;
    }
    clientHandler.connectFuture().addListener(promise);
  }

  private void connectFailed(Channel ch, Throwable t, Promise<HttpClientConnection> future) {
    if (ch != null) {
      try {
        ch.close();
      } catch (Exception ignore) {
      }
    }
    future.tryFail(t);
  }
}
