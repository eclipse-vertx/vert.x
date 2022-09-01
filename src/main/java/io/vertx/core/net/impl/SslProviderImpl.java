package io.vertx.core.net.impl;

import io.vertx.core.spi.tls.SslContextFactory;
import io.vertx.core.spi.tls.SslProvider;

import java.util.List;
import java.util.Set;

public class SslProviderImpl implements SslProvider {

  private final io.netty.handler.ssl.SslProvider provider;
  private final boolean sslSessionCacheEnabled;

  public SslProviderImpl(io.netty.handler.ssl.SslProvider provider, boolean sslSessionCacheEnabled) {
    this.provider = provider;
    this.sslSessionCacheEnabled = sslSessionCacheEnabled;
  }

  @Override
  public SslContextFactory contextFactory(Set<String> enabledCipherSuites, List<String> applicationProtocols) {
    return new SslContextFactoryImpl(provider, sslSessionCacheEnabled, enabledCipherSuites, applicationProtocols);
  }
}
