package io.vertx.core.http.impl.http2.h3;

import io.netty.channel.Channel;
import io.vertx.core.Promise;
import io.vertx.core.http.impl.Http1xClientConnection;
import io.vertx.core.http.impl.Http2UpgradeClientConnection;
import io.vertx.core.http.impl.HttpClientBase;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.http2.Http2ClientChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.ClientMetrics;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3CodecClientChannelInitializer implements Http2ClientChannelInitializer {

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
  public Http2UpgradeClientConnection.Http2ChannelUpgrade channelUpgrade(Http1xClientConnection conn) {
    throw new RuntimeException("Quic does not support channel upgrades");
  }

  @Override
  public void http2Connected(ContextInternal context, Object metric, Channel ch, PromiseInternal<HttpClientConnection> promise) {
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
