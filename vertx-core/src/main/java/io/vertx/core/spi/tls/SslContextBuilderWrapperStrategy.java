package io.vertx.core.spi.tls;

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.codec.quic.QuicSslContextBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.util.Collection;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
interface SslContextBuilderWrapperStrategy {
  void keyManager(KeyManagerFactory kmf);

  void sslProvider(SslProvider sslProvider);

  void trustManager(TrustManagerFactory tmf);

  void ciphers(Collection<String> cipherSuites);

  void applicationProtocolConfig(ApplicationProtocolConfig applicationProtocolConfig);

  void clientAuth(ClientAuth clientAuth);

  SslContext build() throws SSLException;

  void supportedApplicationProtocols(String[] supportedApplicationProtocols);
}

class SslContextBuilderWrapper implements SslContextBuilderWrapperStrategy {
  private final SslContextBuilder sslContextBuilder;

  public SslContextBuilderWrapper(SslContextBuilder sslContextBuilder) {
    this.sslContextBuilder = sslContextBuilder;
  }

  public void keyManager(KeyManagerFactory kmf) {
    this.sslContextBuilder.keyManager(kmf);
  }

  public void sslProvider(SslProvider sslProvider) {
    this.sslContextBuilder.sslProvider(sslProvider);
  }

  public void trustManager(TrustManagerFactory tmf) {
    this.sslContextBuilder.trustManager(tmf);
  }

  public void ciphers(Collection<String> cipherSuites) {
    this.sslContextBuilder.ciphers(cipherSuites);
  }

  public void applicationProtocolConfig(ApplicationProtocolConfig applicationProtocolConfig) {
    this.sslContextBuilder.applicationProtocolConfig(applicationProtocolConfig);
  }

  @Override
  public void supportedApplicationProtocols(String[] supportedApplicationProtocols) {
  }

  public void clientAuth(ClientAuth clientAuth) {
    this.sslContextBuilder.clientAuth(clientAuth);
  }

  public SslContext build() throws SSLException {
    return this.sslContextBuilder.build();
  }
}

class QuicSslContextBuilderWrapper implements SslContextBuilderWrapperStrategy {
  private final QuicSslContextBuilder quicSslContextBuilder;

  public QuicSslContextBuilderWrapper(QuicSslContextBuilder quicSslContextBuilder) {
    this.quicSslContextBuilder = quicSslContextBuilder;
  }

  public void keyManager(KeyManagerFactory kmf) {
    this.quicSslContextBuilder.keyManager(kmf, null);
  }

  public void sslProvider(SslProvider sslProvider) {
  }

  public void trustManager(TrustManagerFactory tmf) {
    this.quicSslContextBuilder.trustManager(tmf);
  }

  public void ciphers(Collection<String> cipherSuites) {
    /*
     *     Cipher suites cannot be modified in QUIC.
     *     In the `QuicheQuicSslContext.java` file, the following method demonstrates that cipher suites are fixed:
     *
     *     public List<String> cipherSuites() {
     *         return Arrays.asList("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384");
     *     }
     *
     *     This method returns a predefined list of cipher suites for TLS 1.3, and no mechanism is provided to modify
     *     them.
     */
  }

  public void applicationProtocolConfig(ApplicationProtocolConfig applicationProtocolConfig) {
    this.quicSslContextBuilder.applicationProtocols(applicationProtocolConfig.supportedProtocols().toArray(new String[0]));
  }

  @Override
  public void supportedApplicationProtocols(String[] supportedApplicationProtocols) {
    this.quicSslContextBuilder.applicationProtocols(supportedApplicationProtocols);
  }

  public void clientAuth(ClientAuth clientAuth) {
    this.quicSslContextBuilder.clientAuth(clientAuth);
  }

  public SslContext build() throws SSLException {
    return this.quicSslContextBuilder.build();
  }
}
