package io.vertx.core.net.impl.tcp;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.http.impl.CleanableHttpServer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.net.NetServerInternal;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class CleanableNetServer extends NetServerImpl implements Closeable {

  private final VertxInternal vertx;
  private ContextInternal listenContext;

  public CleanableNetServer(VertxInternal vertx,
                            TcpServerConfig config,
                            ServerSSLOptions sslOptions,
                            boolean fileRegionEnabled,
                            boolean registerWriteHandler,
                            BiFunction<VertxMetrics, SocketAddress, TransportMetrics<?>> metricsProvider) {
    super(vertx, config, sslOptions, fileRegionEnabled, registerWriteHandler, metricsProvider);
    this.vertx = vertx;
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    ContextInternal context;
    synchronized (this) {
      if (listenContext == null) {
        return vertx.succeededFuture();
      }
      context = listenContext;
      listenContext = null;
    }
    context.removeCloseHook(this);
    return super.shutdown(timeout);
  }

  @Override
  public void close(Completable<Void> completion) {
    super.shutdown(0L, TimeUnit.SECONDS).onComplete(completion);
  }

  @Override
  public Future<NetServer> listen(ContextInternal context, SocketAddress localAddress) {
    synchronized (this) {
      if (listenContext != null) {
        return context.failedFuture(new IllegalStateException());
      }
      listenContext = context;
    }
    context.addCloseHook(this);
    return super
      .listen(context, localAddress)
      .andThen(ar -> {
        if (ar.failed()) {
          synchronized (CleanableNetServer.this) {
            if (listenContext == null) {
              return;
            }
            listenContext = null;
          }
          context.removeCloseHook(this);
        }
      });
  }
}
