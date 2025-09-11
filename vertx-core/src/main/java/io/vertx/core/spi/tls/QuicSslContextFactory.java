/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.tls;

import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslServerContext;
import io.netty.handler.ssl.OpenSslServerSessionContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link SslContextFactory} that creates and configures a Netty {@link io.netty.handler.codec.quic.QuicSslContext} using a
 * {@link QuicSslContextBuilder}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicSslContextFactory implements SslContextFactory {

  public QuicSslContextFactory() {
  }

  private boolean forServer;
  private boolean forClient;
  private Set<String> enabledProtocols;
  private Set<String> enabledCipherSuites;
  private List<String> applicationProtocols;
  private String endpointIdentificationAlgorithm;
  private String serverName;
  private boolean useAlpn;
  private ClientAuth clientAuth;
  private KeyManagerFactory kmf;
  private TrustManagerFactory tmf;

  @Override
  public SslContextFactory useAlpn(boolean useAlpn) {
    this.useAlpn = useAlpn;
    return this;
  }

  @Override
  public SslContextFactory forServer(ClientAuth clientAuth) {
    this.forServer = true;
    this.clientAuth = clientAuth;
    return this;
  }

  @Override
  public SslContextFactory forClient(String serverName, String endpointIdentificationAlgorithm) {
    this.forClient = true;
    this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
    this.serverName = serverName;
    return this;
  }

  @Override
  public SslContextFactory enabledProtocols(Set<String> enabledProtocols) {
    this.enabledProtocols = enabledProtocols;
    return this;
  }

  @Override
  public SslContextFactory keyMananagerFactory(KeyManagerFactory kmf) {
    this.kmf = kmf;
    return this;
  }

  @Override
  public SslContextFactory trustManagerFactory(TrustManagerFactory tmf) {
    this.tmf = tmf;
    return this;
  }

  @Override
  public SslContext create() throws SSLException {
    return createContext(forClient, kmf, tmf);
  }

  @Override
  public SslContextFactory enabledCipherSuites(Set<String> enabledCipherSuites) {
    this.enabledCipherSuites = enabledCipherSuites;
    return this;
  }

  @Override
  public SslContextFactory applicationProtocols(List<String> applicationProtocols) {
    this.applicationProtocols = applicationProtocols;
    return this;
  }

  private SslContext createContext(boolean client, KeyManagerFactory kmf, TrustManagerFactory tmf) throws SSLException {
    QuicSslContextBuilder builder;
    if (client) {
      builder = QuicSslContextBuilder.forClient();
      if (kmf != null) {
        builder.keyManager(kmf, null);
      }
    } else {
      builder = QuicSslContextBuilder.forServer(kmf, null);
      if (clientAuth != null) {
        builder.clientAuth(clientAuth);
      }
    }
/*
    Collection<String> cipherSuites = enabledCipherSuites;
    switch (sslProvider) {
      case OPENSSL:
        builder.sslProvider(SslProvider.OPENSSL);
        if (cipherSuites == null || cipherSuites.isEmpty()) {
          cipherSuites = OpenSsl.availableOpenSslCipherSuites();
        }
        break;
      case JDK:
        builder.sslProvider(SslProvider.JDK);
        if (cipherSuites == null || cipherSuites.isEmpty()) {
          cipherSuites = DefaultJDKCipherSuite.get();
        }
        break;
      default:
        throw new UnsupportedOperationException();
    }
*/
    if (tmf != null) {
      builder.trustManager(tmf);
    }
/*
    if (cipherSuites != null && cipherSuites.size() > 0) {
      builder.ciphers(cipherSuites);
    }
*/
    builder.applicationProtocols(applicationProtocols.toArray(new String[0]));
    return builder.build();
  }
}
