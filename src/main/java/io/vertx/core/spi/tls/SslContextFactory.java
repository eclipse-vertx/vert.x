package io.vertx.core.spi.tls;

import io.netty.handler.ssl.SslContext;
import io.vertx.core.impl.VertxInternal;

public interface SslContextFactory {

  SslContext getContext(VertxInternal vertx,
                        String serverName,
                        boolean useAlpn,
                        boolean client,
                        boolean trustAll);

}
