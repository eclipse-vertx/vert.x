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

import io.vertx.core.file.impl.PathAdjuster;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.*;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class KeyStoreHelper {

  // Dummy password for encrypting pem based stores in memory
  private static final String DUMMY_PASSWORD = "dummy";

  public static KeyStoreHelper create(KeyStoreOptions options) {
    if (options instanceof JKSOptions) {
      JKSOptions jks = (JKSOptions) options;
      return new JKSOrPKCS12("JKS", jks.getPassword(), jks.getPath());
    } else if (options instanceof PKCS12Options) {
      PKCS12Options pkcs12 = (PKCS12Options) options;
      return new JKSOrPKCS12("PKCS12", pkcs12.getPassword(), pkcs12.getPath());
    } else if (options instanceof KeyCertOptions) {
      KeyCertOptions keyCert = (KeyCertOptions) options;
      return new KeyCert(DUMMY_PASSWORD, keyCert.getKeyPath(), keyCert.getCertPath());
    } else {
      return null;
    }
  }

  public static KeyStoreHelper create(TrustStoreOptions options) {
    if (options instanceof KeyStoreOptions) {
      return create((KeyStoreOptions) options);
    } else if (options instanceof CaOptions) {
      return new CA(((CaOptions) options).getCertPaths());
    } else {
      return null;
    }
  }

  private String password;

  public KeyStoreHelper(String password) {
    this.password = password;
  }

  public KeyManager[] getKeyMgrs(VertxInternal vertx) throws Exception {
    KeyManagerFactory fact = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    KeyStore ks = loadStore(vertx, password);
    fact.init(ks, password != null ? password.toCharArray(): null);
    return fact.getKeyManagers();
  }

  public TrustManager[] getTrustMgrs(VertxInternal vertx) throws Exception {
    TrustManagerFactory fact = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    KeyStore ts = loadStore(vertx, password);
    fact.init(ts);
    return fact.getTrustManagers();
  }

  protected abstract KeyStore loadStore(VertxInternal vertx, String password) throws Exception ;

  static class JKSOrPKCS12 extends KeyStoreHelper {

    private String type;
    private String path;

    JKSOrPKCS12(String type, String password, String path) {
      super(password);
      this.type = type;
      this.path = path;
    }

    protected KeyStore loadStore(VertxInternal vertx, String ksPassword) throws Exception {
      String ksPath = PathAdjuster.adjust(vertx, path);
      KeyStore ks = KeyStore.getInstance(type);
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

  static class KeyCert extends KeyStoreHelper {

    private String keyPath;
    private String certPath;

    KeyCert(String password, String keyPath, String certPath) {
      super(password);
      this.keyPath = keyPath;
      this.certPath = certPath;
    }

    @Override
    protected KeyStore loadStore(VertxInternal vertx, String password) throws Exception {
      KeyStore keyStore = KeyStore.getInstance("jks");
      keyStore.load(null, null);
      PrivateKey key = loadPrivateKey(this.keyPath);
      Certificate[] chain = loadCert(this.certPath);
      keyStore.setEntry("foo", new KeyStore.PrivateKeyEntry(key, chain), new KeyStore.PasswordProtection(DUMMY_PASSWORD.toCharArray()));
      return keyStore;
    }
  }

  static class CA extends KeyStoreHelper {

    private List<String> list;

    CA(List<String> list) {
      super(null);
      this.list = list;
    }

    @Override
    protected KeyStore loadStore(VertxInternal vertx, String password) throws Exception {
      KeyStore keyStore = KeyStore.getInstance("jks");
      keyStore.load(null, null);
      int count = 0;
      for (String path : list) {
        for (Certificate certificate : loadCert(path)) {
          keyStore.setCertificateEntry("cert-" + count, certificate);
        }
      }
      return keyStore;
    }
  }

  private static byte[] loadPem(String path, String delimiter) throws IOException {
    String pem = new String(Files.readAllBytes(new File(path).toPath()));
    String beginDelimiter = "-----BEGIN " + delimiter + "-----";
    String endDelimiter = "-----END " + delimiter + "-----";
    int begin = pem.indexOf(beginDelimiter);
    if (begin == -1) {
      throw new RuntimeException("Missing " + beginDelimiter + " delimiter");
    }
    begin += beginDelimiter.length();
    int end = pem.indexOf(endDelimiter, begin);
    if (end == -1) {
      throw new RuntimeException("Missing " + endDelimiter + " delimiter");
    }
    String content = pem.substring(begin, end);
    content = content.replaceAll("\\s", "");
    if (content.length() == 0) {
      throw new RuntimeException("Empty pem file");
    }
    return Base64.getDecoder().decode(content);
  }

  private static PrivateKey loadPrivateKey(String path) throws Exception {
    if (path == null) {
      throw new RuntimeException("Missing private key path");
    }
    byte[] value = loadPem(path, "PRIVATE KEY");
    KeyFactory rsaKeyFactory = KeyFactory.getInstance("RSA");
    return rsaKeyFactory.generatePrivate(new PKCS8EncodedKeySpec(value));
  }

  private static Certificate[] loadCert(String path) throws Exception {
    if (path == null) {
      throw new RuntimeException("Missing X.509 certificate path");
    }
    byte[] value = loadPem(path, "CERTIFICATE");
    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
    return certFactory.generateCertificates(new ByteArrayInputStream(value)).toArray(new Certificate[0]);
  }
}
