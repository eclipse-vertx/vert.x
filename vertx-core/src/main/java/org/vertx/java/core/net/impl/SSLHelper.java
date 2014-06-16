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

package org.vertx.java.core.net.impl;

import io.netty.handler.ssl.SslHandler;
import org.vertx.java.core.file.impl.PathAdjuster;
import org.vertx.java.core.http.HttpClientOptions;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetClientOptions;
import org.vertx.java.core.net.NetServerOptions;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SSLHelper {

  private static final Logger log = LoggerFactory.getLogger(SSLHelper.class);

  private boolean ssl;
  private String keyStorePath;
  private String keyStorePassword;
  private String trustStorePath;
  private String trustStorePassword;
  private boolean trustAll;
  private ClientAuth clientAuth = ClientAuth.NONE;

  private SSLContext sslContext;

  public SSLHelper(NetClientOptions options) {
    this.ssl = options.isSsl();
    this.keyStorePath = options.getKeyStorePath();
    this.keyStorePassword = options.getKeyStorePassword();
    this.trustStorePath = options.getTrustStorePath();
    this.trustStorePassword = options.getTrustStorePassword();
    this.trustAll = options.isTrustAll();
  }

  public SSLHelper(HttpClientOptions options) {
    this.ssl = options.isSsl();
    this.keyStorePath = options.getKeyStorePath();
    this.keyStorePassword = options.getKeyStorePassword();
    this.trustStorePath = options.getTrustStorePath();
    this.trustStorePassword = options.getTrustStorePassword();
    this.trustAll = options.isTrustAll();
  }

  public SSLHelper(NetServerOptions options) {
    this.ssl = options.isSsl();
    this.keyStorePath = options.getKeyStorePath();
    this.keyStorePassword = options.getKeyStorePassword();
    this.trustStorePath = options.getTrustStorePath();
    this.trustStorePassword = options.getTrustStorePassword();
    this.clientAuth = options.isClientAuthRequired() ? ClientAuth.REQUIRED : ClientAuth.NONE;
  }

  public SSLHelper() {
  }

  public void checkSSL(VertxInternal vertx) {
    if (ssl) {
      sslContext = createContext(vertx, keyStorePath, keyStorePassword, trustStorePath, trustStorePassword, trustAll);
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
  public static SSLContext createContext(VertxInternal vertx, String ksPath,
                                         String ksPassword,
                                         String tsPath,
                                         String tsPassword,
                                         boolean trustAll) {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      KeyManager[] keyMgrs = ksPath == null ? null : getKeyMgrs(vertx, ksPath, ksPassword);
      TrustManager[] trustMgrs;
      if (trustAll) {
        trustMgrs = new TrustManager[]{createTrustAllTrustManager()};
      } else {
        trustMgrs = tsPath == null ? null : getTrustMgrs(vertx, tsPath, tsPassword);
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

  private static TrustManager[] getTrustMgrs(VertxInternal vertx, String tsPath,
                                             String tsPassword) throws Exception {
    TrustManagerFactory fact = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    KeyStore ts = loadStore(vertx, tsPath, tsPassword);
    fact.init(ts);
    return fact.getTrustManagers();
  }

  private static KeyManager[] getKeyMgrs(VertxInternal vertx, String ksPath, String ksPassword) throws Exception {
    KeyManagerFactory fact = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    KeyStore ks = loadStore(vertx, ksPath, ksPassword);
    fact.init(ks, ksPassword != null ? ksPassword.toCharArray(): null);
    return fact.getKeyManagers();
  }

  private static KeyStore loadStore(VertxInternal vertx, String path, String ksPassword) throws Exception {
    String ksPath = PathAdjuster.adjust(vertx, path);
    KeyStore ks = KeyStore.getInstance("JKS");
    InputStream in = null;
    try {
      in = new FileInputStream(new File(ksPath));
      ks.load(in, ksPassword != null ? ksPassword.toCharArray(): null);
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException ignore) {
        }
      }
    }
    return ks;
  }

  public SslHandler createSslHandler(VertxInternal vertx, boolean client) {
    if (sslContext == null) {
      sslContext = createContext(vertx, keyStorePath, keyStorePassword, trustStorePath, trustStorePassword, trustAll);
    }
    SSLEngine engine = sslContext.createSSLEngine();
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
