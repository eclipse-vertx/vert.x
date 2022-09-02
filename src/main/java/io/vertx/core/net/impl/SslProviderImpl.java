package io.vertx.core.net.impl;

import io.vertx.core.spi.tls.SslContextFactory;
import io.vertx.core.spi.tls.SslProvider;

public class SslProviderImpl implements SslProvider {

  private final io.netty.handler.ssl.SslProvider provider;
  private final boolean sslSessionCacheEnabled;

  public SslProviderImpl(io.netty.handler.ssl.SslProvider provider, boolean sslSessionCacheEnabled) {
    this.provider = provider;
    this.sslSessionCacheEnabled = sslSessionCacheEnabled;
  }

  @Override
  public SslContextFactory contextFactory() {
    return new SslContextFactoryImpl(provider, sslSessionCacheEnabled);
  }
}
