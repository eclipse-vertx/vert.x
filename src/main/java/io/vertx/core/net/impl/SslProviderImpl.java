package io.vertx.core.net.impl;

import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.spi.tls.SslContextFactory;
import io.vertx.core.spi.tls.SslProvider;

import java.util.List;

public class SslProviderImpl implements SslProvider {

  @Override
  public SslContextFactory contextFactory(TCPSSLOptions options, KeyCertOptions keyCertOptions, TrustOptions trustOptions, List<String> applicationProtocols) {
    return new SslContextFactoryImpl(options, keyCertOptions, trustOptions, applicationProtocols);
  }
}
