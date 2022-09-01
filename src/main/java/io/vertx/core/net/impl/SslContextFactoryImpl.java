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

package io.vertx.core.net.impl;

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslServerContext;
import io.netty.handler.ssl.OpenSslServerSessionContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.vertx.core.VertxException;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.spi.tls.SslContextFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The default implementation of {@link SslContextFactory} that creates and configures a Netty {@link SslContext} with
 * the provided options.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SslContextFactoryImpl implements SslContextFactory {

  private final Set<String> enabledCipherSuites;
  private final boolean openSsl;
  private final List<String> applicationProtocols;
  private final boolean openSslSessionCacheEnabled;

  public SslContextFactoryImpl(SSLEngineOptions sslEngineOptions,
                               Set<String> enabledCipherSuites,
                               List<String> applicationProtocols) {
    this.enabledCipherSuites = new HashSet<>(enabledCipherSuites);
    this.openSsl = sslEngineOptions instanceof OpenSSLEngineOptions;
    this.openSslSessionCacheEnabled = (sslEngineOptions instanceof OpenSSLEngineOptions) && ((OpenSSLEngineOptions) sslEngineOptions).isSessionCacheEnabled();
    this.applicationProtocols = applicationProtocols;
  }

  private boolean useAlpn;
  private boolean forClient;
  private KeyManagerFactory kmf;
  private TrustManagerFactory tmf;

  @Override
  public SslContextFactory useAlpn(boolean useAlpn) {
    this.useAlpn = useAlpn;
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
  public SslContext create() {
    return createContext(useAlpn, forClient, kmf, tmf);
  }

  /*
      If you don't specify a trust store, and you haven't set system properties, the system will try to use either a file
      called jsssecacerts or cacerts in the JDK/JRE security directory.
      You can override this by specifying the javax.echo.ssl.trustStore system property

      If you don't specify a key store, and don't specify a system property no key store will be used
      You can override this by specifying the javax.echo.ssl.keyStore system property
       */
  private SslContext createContext(boolean useAlpn, boolean client, KeyManagerFactory kmf, TrustManagerFactory tmf) {
    try {
      SslContextBuilder builder;
      if (client) {
        builder = SslContextBuilder.forClient();
        if (kmf != null) {
          builder.keyManager(kmf);
        }
      } else {
        builder = SslContextBuilder.forServer(kmf);
      }
      Collection<String> cipherSuites = enabledCipherSuites;
      if (openSsl) {
        builder.sslProvider(SslProvider.OPENSSL);
        if (cipherSuites == null || cipherSuites.isEmpty()) {
          cipherSuites = OpenSsl.availableOpenSslCipherSuites();
        }
      } else {
        builder.sslProvider(SslProvider.JDK);
        if (cipherSuites == null || cipherSuites.isEmpty()) {
          cipherSuites = DefaultJDKCipherSuite.get();
        }
      }
      if (tmf != null) {
        builder.trustManager(tmf);
      }
      if (cipherSuites != null && cipherSuites.size() > 0) {
        builder.ciphers(cipherSuites);
      }
      if (useAlpn && applicationProtocols != null && applicationProtocols.size() > 0) {
        builder.applicationProtocolConfig(new ApplicationProtocolConfig(
            ApplicationProtocolConfig.Protocol.ALPN,
            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
            applicationProtocols
        ));
      }
      SslContext ctx = builder.build();
      if (ctx instanceof OpenSslServerContext){
        SSLSessionContext sslSessionContext = ctx.sessionContext();
        if (sslSessionContext instanceof OpenSslServerSessionContext){
          ((OpenSslServerSessionContext)sslSessionContext).setSessionCacheEnabled(openSslSessionCacheEnabled);
        }
      }
      return ctx;
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }
}
