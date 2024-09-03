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

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslServerContext;
import io.netty.handler.ssl.OpenSslServerSessionContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The default implementation of {@link SslContextFactory} that creates and configures a Netty {@link SslContext} using a
 * {@link SslContextBuilder}.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DefaultSslContextFactory implements SslContextFactory {

  private final SslProvider sslProvider;
  private final boolean sslSessionCacheEnabled;

  public DefaultSslContextFactory(SslProvider sslProvider,
                                  boolean sslSessionCacheEnabled) {
    this.sslProvider = sslProvider;
    this.sslSessionCacheEnabled = sslSessionCacheEnabled;
  }

  private Set<String> enabledCipherSuites;
  private List<String> applicationProtocols;
  private boolean useAlpn;
  private boolean http3;
  private ClientAuth clientAuth;
  private boolean forClient;
  private KeyManagerFactory kmf;
  private TrustManagerFactory tmf;

  @Override
  public SslContextFactory useAlpn(boolean useAlpn) {
    this.useAlpn = useAlpn;
    return this;
  }

  @Override
  public SslContextFactory http3(boolean http3) {
    this.http3 = http3;
    return this;
  }

  @Override
  public SslContextFactory clientAuth(ClientAuth clientAuth) {
    this.clientAuth = clientAuth;
    return this;
  }

  @Override
  public SslContextFactory forClient(boolean forClient) {
    this.forClient = forClient;
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
    return createContext(useAlpn, forClient, kmf, tmf);
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

  /*
        If you don't specify a trust store, and you haven't set system properties, the system will try to use either a file
        called jsssecacerts or cacerts in the JDK/JRE security directory.
        You can override this by specifying the javax.echo.ssl.trustStore system property

        If you don't specify a key store, and don't specify a system property no key store will be used
        You can override this by specifying the javax.echo.ssl.keyStore system property
         */
  private SslContext createContext(boolean useAlpn, boolean client, KeyManagerFactory kmf, TrustManagerFactory tmf) throws SSLException {
    SslContextBuilderWrapperStrategy builder;
    if (client) {
      builder = http3 ? new QuicSslContextBuilderWrapper(QuicSslContextBuilder.forClient()) : new SslContextBuilderWrapper(SslContextBuilder.forClient());
      if (kmf != null) {
        builder.keyManager(kmf);
      }
    } else {
      builder = http3 ? new QuicSslContextBuilderWrapper(QuicSslContextBuilder.forServer(kmf, null)) : new SslContextBuilderWrapper(SslContextBuilder.forServer(kmf));
    }
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
    if (tmf != null) {
      builder.trustManager(tmf);
    }
    if (cipherSuites != null && cipherSuites.size() > 0) {
      builder.ciphers(cipherSuites);
    }

    if (useAlpn && applicationProtocols != null && applicationProtocols.size() > 0) {
      if(http3) {
        builder.supportedApplicationProtocols(Http3.supportedApplicationProtocols());
      } else {
        ApplicationProtocolConfig.SelectorFailureBehavior sfb;
        ApplicationProtocolConfig.SelectedListenerFailureBehavior slfb;
        if (sslProvider == SslProvider.JDK) {
          sfb = ApplicationProtocolConfig.SelectorFailureBehavior.FATAL_ALERT;
          slfb = ApplicationProtocolConfig.SelectedListenerFailureBehavior.FATAL_ALERT;
        } else {
          // Fatal alert not supportd by OpenSSL
          sfb = ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE;
          slfb = ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT;
        }
        builder.applicationProtocolConfig(new ApplicationProtocolConfig(
          ApplicationProtocolConfig.Protocol.ALPN,
          sfb,
          slfb,
          applicationProtocols
        ));
      }
    }
    if (clientAuth != null) {
      builder.clientAuth(clientAuth);
    }
    SslContext ctx = builder.build();
    if (ctx instanceof OpenSslServerContext){
      SSLSessionContext sslSessionContext = ctx.sessionContext();
      if (sslSessionContext instanceof OpenSslServerSessionContext){
        ((OpenSslServerSessionContext)sslSessionContext).setSessionCacheEnabled(sslSessionCacheEnabled);
      }
    }
    return ctx;
  }
}
