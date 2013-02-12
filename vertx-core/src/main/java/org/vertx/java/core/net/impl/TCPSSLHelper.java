/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.net.impl;

import org.jboss.netty.channel.FixedReceiveBufferSizePredictor;
import org.vertx.java.core.file.impl.PathAdjuster;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for TCP and SSL attributes
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TCPSSLHelper {

  private static final Logger log = LoggerFactory.getLogger(TCPSSLHelper.class);

  private boolean ssl;
  private boolean verifyHost = true;
  private String keyStorePath;
  private String keyStorePassword;
  private String trustStorePath;
  private String trustStorePassword;
  private boolean trustAll;
  private ClientAuth clientAuth = ClientAuth.NONE;

  private Boolean tcpNoDelay = true;
  private Integer tcpSendBufferSize;
  private Integer tcpReceiveBufferSize;
  private Boolean tcpKeepAlive = true;
  private Boolean reuseAddress;
  private Boolean soLinger;
  private Integer trafficClass;
  private Integer acceptBackLog;
  private Long connectTimeout;

  private SSLContext sslContext;

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

  public Map<String, Object> generateConnectionOptions(boolean server) {
    Map<String, Object> options = new HashMap<>();
    String prefix = (server ? "child." : "");
    if (tcpNoDelay != null) {
      options.put(prefix +"tcpNoDelay", tcpNoDelay);
    }
    if (tcpSendBufferSize != null) {
      options.put(prefix + "sendBufferSize", tcpSendBufferSize);
    }
    if (tcpReceiveBufferSize != null) {
      options.put(prefix + "receiveBufferSize", tcpReceiveBufferSize);
      // We need to set a FixedReceiveBufferSizePredictor, since otherwise
      // Netty will ignore our setting and use an adaptive buffer which can
      // get very large
      options.put(prefix + "receiveBufferSizePredictor", new FixedReceiveBufferSizePredictor(1024));
    }
    if (soLinger != null) {
      options.put(prefix + "soLinger", soLinger);
    }
    if (trafficClass != null) {
      options.put(prefix + "trafficClass", trafficClass);
    }
    if (server) {
      if (reuseAddress != null) {
        options.put("reuseAddress", reuseAddress);
      }
      if (acceptBackLog != null) {
        options.put("backlog", acceptBackLog);
      }
    }
    if (!server && connectTimeout != null) {
      options.put("connectTimeoutMillis", connectTimeout);
    }
    return options;
  }

  public Boolean isTCPNoDelay() {
    return tcpNoDelay;
  }

  public Integer getSendBufferSize() {
    return tcpSendBufferSize;
  }

  public Integer getReceiveBufferSize() {
    return tcpReceiveBufferSize;
  }

  public Boolean isTCPKeepAlive() {
    return tcpKeepAlive;
  }

  public Boolean isReuseAddress() {
    return reuseAddress;
  }

  public Boolean isSoLinger() {
    return soLinger;
  }

  public Integer getTrafficClass() {
    return trafficClass;
  }

  public void setTCPNoDelay(Boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
  }

  public void setSendBufferSize(Integer size) {
    if (size < 1) {
      throw new IllegalArgumentException("TCP send buffer size must be >= 1");
    }
    this.tcpSendBufferSize = size;
  }

  public void setReceiveBufferSize(Integer size) {
    if (size < 1) {
      throw new IllegalArgumentException("TCP receive buffer size must be >= 1");
    }
    this.tcpReceiveBufferSize = size;
  }

  public void setTCPKeepAlive(Boolean keepAlive) {
    this.tcpKeepAlive = keepAlive;
  }

  public void setReuseAddress(Boolean reuse) {
    this.reuseAddress = reuse;
  }

  public void setSoLinger(Boolean linger) {
    this.soLinger = linger;
  }

  public void setTrafficClass(Integer trafficClass) {
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

  public SSLContext getSSLContext() {
    return sslContext;
  }

  public void setSSL(boolean ssl) {
    this.ssl = ssl;
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

  public Integer getAcceptBacklog() {
    return acceptBackLog;
  }

  public Long getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Long connectTimeout) {
    if (connectTimeout < 0) {
      throw new IllegalArgumentException("connectTimeout must be >= 0");
    }
    this.connectTimeout = connectTimeout;
  }

  public void setAcceptBacklog(Integer acceptBackLog) {
    if (acceptBackLog < 0) {
      throw new IllegalArgumentException("acceptBackLog must be >= 0");
    }
    this.acceptBackLog = acceptBackLog;
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
  private TrustManager createTrustAllTrustManager() {
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

  private TrustManager[] getTrustMgrs(VertxInternal vertx, final String tsPath,
                                             final String tsPassword) throws Exception {
    TrustManagerFactory fact = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    KeyStore ts = loadStore(vertx, tsPath, tsPassword);
    fact.init(ts);
    return fact.getTrustManagers();
  }

  private KeyManager[] getKeyMgrs(VertxInternal vertx, final String ksPath, final String ksPassword) throws Exception {
    KeyManagerFactory fact = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    KeyStore ks = loadStore(vertx, ksPath, ksPassword);
    fact.init(ks, ksPassword != null ? ksPassword.toCharArray(): null);
    return fact.getKeyManagers();
  }

  private KeyStore loadStore(VertxInternal vertx, String path, final String ksPassword) throws Exception {
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
}
