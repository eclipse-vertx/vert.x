package io.vertx.core.net.impl.tcp;

import io.vertx.core.*;
import io.vertx.core.internal.ServiceResource;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.*;

import java.time.Duration;

public class CleanableNetServer extends NetServerImpl {

  private final VertxInternal vertx;
  private final ServiceResource<SocketAddress, NetServer> serviceResource;

  public CleanableNetServer(VertxInternal vertx,
                            TcpServerConfig config,
                            String protocol,
                            ServerSSLOptions sslOptions,
                            SSLEngineOptions sslEngineOptions,
                            boolean fileRegionEnabled,
                            boolean registerWriteHandler) {
    super(vertx, config, protocol, sslOptions, sslEngineOptions, fileRegionEnabled, registerWriteHandler);
    this.vertx = vertx;
    this.serviceResource = new ServiceResource<>() {
      @Override
      protected Future<NetServer> startImpl(ContextInternal context, SocketAddress localAddress) {
        return CleanableNetServer.super.listen(context, localAddress).map(CleanableNetServer.this);
      }
      @Override
      protected Future<Void> stopImpl(ContextInternal context, SocketAddress args, Duration timeout) {
        return CleanableNetServer.super.shutdown(timeout);
      }
    };
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    return serviceResource.stop(vertx.getOrCreateContext(), timeout);
  }

  @Override
  public Future<NetServer> listen(ContextInternal context, SocketAddress localAddress) {
    return serviceResource.start(context, localAddress);
  }
}
