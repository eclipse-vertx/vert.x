/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.core.net.impl;

import io.netty.handler.ssl.SslHandler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Set;

/**
 *
 * This is a pretty sucky class - could do with a refactoring
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SSLHelper {

  private static final Logger log = LoggerFactory.getLogger(SSLHelper.class);

  private boolean ssl;
  private KeyStoreHelper keyStoreHelper;
  private KeyStoreHelper trustStoreHelper;
  private boolean trustAll;
  private ClientAuth clientAuth = ClientAuth.NONE;
  private Set<String> enabledCipherSuites;

  private SSLContext sslContext;

  public SSLHelper(NetClientOptions options, KeyStoreHelper keyStoreHelper, KeyStoreHelper trustStoreHelper) {
    this.ssl = options.isSsl();
    this.keyStoreHelper = keyStoreHelper;
    this.trustStoreHelper = trustStoreHelper;
    this.trustAll = options.isTrustAll();
    this.enabledCipherSuites = options.getEnabledCipherSuites();
  }

  public SSLHelper(HttpClientOptions options, KeyStoreHelper keyStoreHelper, KeyStoreHelper trustStoreHelper) {
    this.ssl = options.isSsl();
    this.keyStoreHelper = keyStoreHelper;
    this.trustStoreHelper = trustStoreHelper;
    this.trustAll = options.isTrustAll();
    this.enabledCipherSuites = options.getEnabledCipherSuites();
  }

  public SSLHelper(NetServerOptions options, KeyStoreHelper keyStoreHelper, KeyStoreHelper trustStoreHelper) {
    this.ssl = options.isSsl();
    this.keyStoreHelper = keyStoreHelper;
    this.trustStoreHelper = trustStoreHelper;
    this.clientAuth = options.isClientAuthRequired() ? ClientAuth.REQUIRED : ClientAuth.NONE;
    this.enabledCipherSuites = options.getEnabledCipherSuites();
  }

  public synchronized void checkSSL(VertxInternal vertx) {
    if (ssl && sslContext == null) {
      sslContext = createContext(vertx, keyStoreHelper, trustStoreHelper, trustAll);
    }
  }

  public enum ClientAuth {
    NONE, REQUEST, REQUIRED
  }

  public boolean isSSL() {
    return ssl;
  }

  public ClientAuth getClientAuth() {
    return clientAuth;
  }

  /*
  If you don't specify a trust store, and you haven't set system properties, the system will try to use either a file
  called jsssecacerts or cacerts in the JDK/JRE security directory.
  You can override this by specifying the javax.echo.ssl.trustStore system property

  If you don't specify a key store, and don't specify a system property no key store will be used
  You can override this by specifying the javax.echo.ssl.keyStore system property
   */
  public static SSLContext createContext(VertxInternal vertx, KeyStoreHelper ksHelper,
                                         KeyStoreHelper tsHelper, boolean trustAll) {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      KeyManager[] keyMgrs = ksHelper == null ? null : ksHelper.getKeyMgrs(vertx);
      TrustManager[] trustMgrs;
      if (trustAll) {
        trustMgrs = new TrustManager[]{createTrustAllTrustManager()};
      } else {
        trustMgrs = tsHelper == null ? null : tsHelper.getTrustMgrs(vertx);
      }
      context.init(keyMgrs, trustMgrs, new SecureRandom());
      return context;
    } catch (Exception e) {
      //TODO better logging
      log.error("Failed to create context", e);
      throw new RuntimeException(e.getMessage());
    }
  }

  // Create a TrustManager which trusts everything
  private static TrustManager createTrustAllTrustManager() {
    return new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    };
  }

  public SslHandler createSslHandler(VertxInternal vertx, boolean client) {
    if (sslContext == null) {
      sslContext = createContext(vertx, keyStoreHelper, trustStoreHelper, trustAll);
    }
    SSLEngine engine = sslContext.createSSLEngine();
    String[] current = engine.getSupportedCipherSuites();
//    System.out.println("Enabled cipher suites:");
//    for (String str: current) {
//      System.out.println("\"" + str+ "\",");
//    }

    if (enabledCipherSuites != null && !enabledCipherSuites.isEmpty()) {
      String[] toUse = enabledCipherSuites.toArray(new String[enabledCipherSuites.size()]);
      engine.setEnabledCipherSuites(toUse);
    }

    engine.setUseClientMode(client);

    if (!client) {
      switch (getClientAuth()) {
        case REQUEST: {
          engine.setWantClientAuth(true);
          break;
        }
        case REQUIRED: {
          engine.setNeedClientAuth(true);
          break;
        }
        case NONE: {
          engine.setNeedClientAuth(false);
          break;
        }
      }
    }
    return new SslHandler(engine);
  }

  public SSLContext getSslContext() {
    return sslContext;
  }
}
