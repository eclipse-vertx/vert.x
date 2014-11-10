/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.handler.ssl.SslHandler;
import org.vertx.java.core.file.impl.PathAdjuster;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Helper class for TCP and SSL attributes
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TCPSSLHelper {

  private static final Logger log = LoggerFactory.getLogger(TCPSSLHelper.class);

  private boolean ssl;
  private boolean verifyHost = false;
  private String keyStorePath;
  private String keyStorePassword;
  private String trustStorePath;
  private String trustStorePassword;
  private boolean trustAll;
  private ClientAuth clientAuth = ClientAuth.NONE;

  private static SocketDefaults defaults = SocketDefaults.instance;

  private boolean tcpNoDelay = true;
  private int tcpSendBufferSize = -1;
  private int tcpReceiveBufferSize = -1;
  private boolean tcpKeepAlive = defaults.isTcpKeepAlive();
  private boolean reuseAddress = defaults.isReuseAddress();
  private int soLinger = defaults.getSoLinger();
  private int trafficClass = -1;
  private int acceptBackLog = 1024;
  private int connectTimeout = 60000;
  private boolean usePooledBuffers;

  private SSLContext sslContext;

  private SSLContext externalSSLContext = null;

  public TCPSSLHelper() {
  }

  public void checkSSL(VertxInternal vertx) {
    if (ssl) {
      sslContext = createContext(vertx, keyStorePath, keyStorePassword, trustStorePath, trustStorePassword, trustAll);
    }
  }

  public enum ClientAuth {
    NONE, REQUEST, REQUIRED
  }

  public void applyConnectionOptions(ServerBootstrap bootstrap) {
    bootstrap.childOption(ChannelOption.TCP_NODELAY, tcpNoDelay);
    if (tcpSendBufferSize != -1) {
      bootstrap.childOption(ChannelOption.SO_SNDBUF, tcpSendBufferSize);
    }
    if (tcpReceiveBufferSize != -1) {
      bootstrap.childOption(ChannelOption.SO_RCVBUF, tcpReceiveBufferSize);
      bootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(tcpReceiveBufferSize));
    }

    bootstrap.option(ChannelOption.SO_LINGER, soLinger);
    if (trafficClass != -1) {
      bootstrap.childOption(ChannelOption.IP_TOS, trafficClass);
    }
    bootstrap.childOption(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);

    bootstrap.childOption(ChannelOption.SO_KEEPALIVE, tcpKeepAlive);
    bootstrap.option(ChannelOption.SO_REUSEADDR, reuseAddress);
    bootstrap.option(ChannelOption.SO_BACKLOG, acceptBackLog);
  }

  public void applyConnectionOptions(Bootstrap bootstrap) {
    bootstrap.option(ChannelOption.TCP_NODELAY, tcpNoDelay);
    if (tcpSendBufferSize != -1) {
      bootstrap.option(ChannelOption.SO_SNDBUF, tcpSendBufferSize);
    }
    if (tcpReceiveBufferSize != -1) {
      bootstrap.option(ChannelOption.SO_RCVBUF, tcpReceiveBufferSize);
      bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(tcpReceiveBufferSize));
    }
    bootstrap.option(ChannelOption.SO_LINGER, soLinger);
    if (trafficClass != -1) {
      bootstrap.option(ChannelOption.IP_TOS, trafficClass);
    }
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
    bootstrap.option(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);
    bootstrap.option(ChannelOption.SO_KEEPALIVE, tcpKeepAlive);
  }

  public boolean isTCPNoDelay() {
    return tcpNoDelay;
  }

  public int getSendBufferSize() {
    return tcpSendBufferSize;
  }

  public int getReceiveBufferSize() {
    return tcpReceiveBufferSize;
  }

  public boolean isTCPKeepAlive() {
    return tcpKeepAlive;
  }

  public boolean isReuseAddress() {
    return reuseAddress;
  }

  public int getSoLinger() {
    return soLinger;
  }

  public int getTrafficClass() {
    return trafficClass;
  }

  public void setTCPNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
  }

  public void setSendBufferSize(int size) {
    if (size < 1) {
      throw new IllegalArgumentException("TCP send buffer size must be >= 1");
    }
    this.tcpSendBufferSize = size;
  }

  public void setReceiveBufferSize(int size) {
    if (size < 1) {
      throw new IllegalArgumentException("TCP receive buffer size must be >= 1");
    }
    this.tcpReceiveBufferSize = size;
  }

  public void setTCPKeepAlive(boolean keepAlive) {
    this.tcpKeepAlive = keepAlive;
  }

  public void setReuseAddress(boolean reuse) {
    this.reuseAddress = reuse;
  }

  public void setSoLinger(int linger) {
    this.soLinger = linger;
  }

  public void setTrafficClass(int trafficClass) {
    this.trafficClass = trafficClass;
  }

  public boolean isSSL() {
    return ssl;
  }

  public boolean isVerifyHost() {
    return verifyHost;
  }

  public String getKeyStorePath() {
    return keyStorePath;
  }

  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  public String getTrustStorePath() {
    return trustStorePath;
  }

  public String getTrustStorePassword() {
    return trustStorePassword;
  }

  public ClientAuth getClientAuth() {
    return clientAuth;
  }

  public boolean isTrustAll() {
    return trustAll;
  }

  public void setSSL(boolean ssl) {
    this.ssl = ssl;
  }

  public void setExternalSSLContext(SSLContext externalSSLContext) {
    this.externalSSLContext = externalSSLContext;
  }

  public void setVerifyHost(boolean verifyHost) {
    this.verifyHost = verifyHost;
  }

  public void setKeyStorePath(String path) {
    this.keyStorePath = path;
  }

  public void setKeyStorePassword(String pwd) {
    this.keyStorePassword = pwd;
  }

  public void setTrustStorePath(String path) {
    this.trustStorePath = path;
  }

  public void setTrustStorePassword(String pwd) {
    this.trustStorePassword = pwd;
  }

  public void setClientAuthRequired(boolean required) {
    clientAuth = required ? ClientAuth.REQUIRED : ClientAuth.NONE;
  }

  public void setTrustAll(boolean trustAll) {
    this.trustAll = trustAll;
  }

  public int getAcceptBacklog() {
    return acceptBackLog;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(int connectTimeout) {
    if (connectTimeout < 0) {
      throw new IllegalArgumentException("connectTimeout must be >= 0");
    }
    this.connectTimeout = connectTimeout;
  }

  public void setAcceptBacklog(int acceptBackLog) {
    if (acceptBackLog < 0) {
      throw new IllegalArgumentException("acceptBackLog must be >= 0");
    }
    this.acceptBackLog = acceptBackLog;
  }

  public void setUsePooledBuffers(boolean usePooledBuffers) {
    this.usePooledBuffers = usePooledBuffers;
  }

  public boolean isUsePooledBuffers() {
    return usePooledBuffers;
  }

  /*
  If you don't specify a trust store, and you haven't set system properties, the system will try to use either a file
  called jsssecacerts or cacerts in the JDK/JRE security directory.
  You can override this by specifying the javax.echo.ssl.trustStore system property

  If you don't specify a key store, and don't specify a system property no key store will be used
  You can override this by specifying the javax.echo.ssl.keyStore system property
   */
  public SSLContext createContext(VertxInternal vertx, final String ksPath,
                                   final String ksPassword,
                                   final String tsPath,
                                   final String tsPassword,
                                   final boolean trustAll) {
    if ( externalSSLContext != null ) {
      return externalSSLContext;
    }
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

  private static TrustManager[] getTrustMgrs(VertxInternal vertx, final String tsPath,
                                             final String tsPassword) throws Exception {
    TrustManagerFactory fact = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    KeyStore ts = loadStore(vertx, tsPath, tsPassword);
    fact.init(ts);
    return fact.getTrustManagers();
  }

  private static KeyManager[] getKeyMgrs(VertxInternal vertx, final String ksPath, final String ksPassword) throws Exception {
    KeyManagerFactory fact = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    KeyStore ks = loadStore(vertx, ksPath, ksPassword);
    fact.init(ks, ksPassword != null ? ksPassword.toCharArray(): null);
    return fact.getKeyManagers();
  }

  private static KeyStore loadStore(VertxInternal vertx, String path, final String ksPassword) throws Exception {
    final String ksPath = PathAdjuster.adjust(vertx, path);
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

  // Make sure SSLv3 is NOT enabled due to POODLE issue http://en.wikipedia.org/wiki/POODLE
  private static final String[] ENABLED_PROTOCOLS = {"SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2"};

  private SslHandler createHandler(SSLEngine engine, boolean client) {
    engine.setEnabledProtocols(ENABLED_PROTOCOLS);
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
    } else if (verifyHost) {
      SSLParameters sslParameters = engine.getSSLParameters();
      sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
      engine.setSSLParameters(sslParameters);
    }
    return new SslHandler(engine);
  }

  private SSLContext getContext(VertxInternal vertx) {
    if (sslContext == null) {
      sslContext = createContext(vertx, keyStorePath, keyStorePassword, trustStorePath, trustStorePassword, trustAll);
    }
    return sslContext;
  }

  public SslHandler createSslHandler(VertxInternal vertx, boolean client, String host, int port) {
    SSLEngine engine = getContext(vertx).createSSLEngine(host, port);
    return createHandler(engine, client);
  }

  public SslHandler createSslHandler(VertxInternal vertx, boolean client) {
    SSLEngine engine = getContext(vertx).createSSLEngine();
    return createHandler(engine, client);
  }
}
