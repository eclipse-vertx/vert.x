/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.net;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
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
 * Helper class for SSL.
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TLSHelper {

  /*
  If you don't specify a trust store, and you haven't set system properties, the system will try to use either a file
  called jsssecacerts or cacerts in the JDK/JRE security directory.
  You can override this by specifying the javax.echo.ssl.trustStore system property

  If you don't specify a key store, and don't specify a system property no key store will be used
  You can override this by specifying the javax.echo.ssl.keyStore system property
   */
  public static SSLContext createContext(final String ksPath,
                                         final String ksPassword,
                                         final String tsPath,
                                         final String tsPassword,
                                         final boolean trustAll) {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      KeyManager[] keyMgrs = ksPath == null ? null : TLSHelper.getKeyMgrs(ksPath, ksPassword);
      TrustManager[] trustMgrs;
      if (trustAll) {
        trustMgrs = new TrustManager[]{createTrustAllTrustManager()};
      } else {
        trustMgrs = tsPath == null ? null : TLSHelper.getTrustMgrs(tsPath, tsPassword);
      }
      context.init(keyMgrs, trustMgrs, new SecureRandom());
      return context;
    } catch (Exception e) {
      //TODO better logging
      e.printStackTrace();
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
        return null;
      }
    };
  }

  private static TrustManager[] getTrustMgrs(final String tsPath,
                                             final String tsPassword) throws Exception {
    TrustManagerFactory fact = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    KeyStore ts = TLSHelper.loadStore(tsPath, tsPassword);
    fact.init(ts);
    return fact.getTrustManagers();
  }

  private static KeyManager[] getKeyMgrs(final String ksPath, final String ksPassword) throws Exception {
    KeyManagerFactory fact = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    KeyStore ks = loadStore(ksPath, ksPassword);
    fact.init(ks, ksPassword.toCharArray());
    return fact.getKeyManagers();
  }

  private static KeyStore loadStore(final String ksPath, final String ksPassword) throws Exception {
    KeyStore ks = KeyStore.getInstance("JKS");
    InputStream in = null;
    try {
      in = new FileInputStream(new File(ksPath));
      ks.load(in, ksPassword.toCharArray());
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

