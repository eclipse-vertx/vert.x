package io.vertx.core.net.impl.tcp;

import io.vertx.core.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.*;

import java.time.Duration;

public class CleanableTcpServer extends TcpServerImpl implements Closeable {

  private final VertxInternal vertx;
  private ContextInternal listenContext;

  public CleanableTcpServer(VertxInternal vertx,
                            TcpServerConfig config,
                            String protocol,
                            ServerSSLOptions sslOptions,
                            boolean fileRegionEnabled,
                            boolean registerWriteHandler) {
    super(vertx, config, protocol, sslOptions, fileRegionEnabled, registerWriteHandler);
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
    super.close().onComplete(completion);
  }

  @Override
  public Future<SocketAddress> listen(ContextInternal context, SocketAddress localAddress) {
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
          synchronized (CleanableTcpServer.this) {
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
