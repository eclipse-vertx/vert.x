package io.vertx.core.net.impl;

import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.spi.tls.SslContextFactory;
import io.vertx.core.spi.tls.SslProvider;

import java.util.List;
import java.util.Set;

public class SslProviderImpl implements SslProvider {

  @Override
  public SslContextFactory contextFactory(SSLEngineOptions options, Set<String> enabledCipherSuites, List<String> applicationProtocols) {
    return new SslContextFactoryImpl(options, enabledCipherSuites, applicationProtocols);
  }
}
