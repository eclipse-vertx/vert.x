package io.vertx.core.net.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.TCPSSLOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.spi.tls.SslContextFactory;
import io.vertx.core.spi.tls.SslProvider;

import java.util.List;
import java.util.Set;

public class SslProviderImpl implements SslProvider {

  @Override
  public SslContextFactory contextFactory(SSLEngineOptions options, KeyCertOptions keyCertOptions, TrustOptions trustOptions, List<String> crltPaths, List<Buffer> crlValues, Set<String> enabledCipherSuites, List<String> applicationProtocols) {
    return new SslContextFactoryImpl(options, keyCertOptions, trustOptions, crltPaths, crlValues, enabledCipherSuites, applicationProtocols);
  }
}
