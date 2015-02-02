/*
 * Copyright (c) 2011-2014 The original author or authors
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
package io.vertx.core.net.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.PemCaOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.CaOptions;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class KeyStoreHelper {

  // Dummy password for encrypting pem based stores in memory
  private static final String DUMMY_PASSWORD = "dummy";

  public static KeyStoreHelper create(VertxInternal vertx, KeyCertOptions options) {
    if (options instanceof JksOptions) {
      JksOptions jks = (JksOptions) options;
      Supplier<Buffer> value;
      if (jks.getPath() != null) {
        value = () -> vertx.fileSystem().readFileBlocking(vertx.resolveFile(jks.getPath()).getAbsolutePath());
      } else if (jks.getValue() != null) {
        value = () -> jks.getValue();
      } else {
        return null;
      }
      return new JKSOrPKCS12("JKS", jks.getPassword(), value);
    } else if (options instanceof PfxOptions) {
      PfxOptions pkcs12 = (PfxOptions) options;
      Supplier<Buffer> value;
      if (pkcs12.getPath() != null) {
        value = () -> vertx.fileSystem().readFileBlocking(vertx.resolveFile(pkcs12.getPath()).getAbsolutePath());
      } else if (pkcs12.getValue() != null) {
        value = () -> pkcs12.getValue();
      } else {
        return null;
      }
      return new JKSOrPKCS12("PKCS12", pkcs12.getPassword(), value);
    } else if (options instanceof PemKeyCertOptions) {
      PemKeyCertOptions keyCert = (PemKeyCertOptions) options;
      Supplier<Buffer> key = () -> {
        if (keyCert.getKeyPath() != null) {
          return vertx.fileSystem().readFileBlocking(vertx.resolveFile(keyCert.getKeyPath()).getAbsolutePath());
        } else if (keyCert.getKeyValue() != null) {
          return keyCert.getKeyValue();
        } else {
          throw new RuntimeException("Missing private key");
        }
      };
      Supplier<Buffer> cert = () -> {
        if (keyCert.getCertPath() != null) {
          return vertx.fileSystem().readFileBlocking(vertx.resolveFile(keyCert.getCertPath()).getAbsolutePath());
        } else if (keyCert.getCertValue() != null) {
          return keyCert.getCertValue();
        } else {
          throw new RuntimeException("Missing X.509 certificate");
        }
      };
      return new KeyCert(DUMMY_PASSWORD, key, cert);
    } else {
      return null;
    }
  }

  public static KeyStoreHelper create(VertxInternal vertx, CaOptions options) {
    if (options instanceof KeyCertOptions) {
      return create(vertx, (KeyCertOptions) options);
    } else if (options instanceof PemCaOptions) {
      PemCaOptions caOptions = (PemCaOptions) options;
      Stream<Buffer> certValues = caOptions.
          getCertPaths().
          stream().
          map(path -> vertx.resolveFile(path).getAbsolutePath()).
          map(vertx.fileSystem()::readFileBlocking);
      certValues = Stream.concat(certValues, caOptions.getCertValues().stream());
      return new CA(certValues);
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
    private Supplier<Buffer> value;

    JKSOrPKCS12(String type, String password, Supplier<Buffer> value) {
      super(password);
      this.type = type;
      this.value = value;
    }

    protected KeyStore loadStore(VertxInternal vertx, String ksPassword) throws Exception {
      KeyStore ks = KeyStore.getInstance(type);
      InputStream in = null;
      try {
        in = new ByteArrayInputStream(value.get().getBytes());
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

    private Supplier<Buffer> keyValue;
    private Supplier<Buffer> certValue;

    KeyCert(String password, Supplier<Buffer> keyValue, Supplier<Buffer> certValue) {
      super(password);
      this.keyValue = keyValue;
      this.certValue = certValue;
    }

    @Override
    protected KeyStore loadStore(VertxInternal vertx, String password) throws Exception {
      KeyStore keyStore = KeyStore.getInstance("jks");
      keyStore.load(null, null);
      PrivateKey key = loadPrivateKey(this.keyValue.get());
      Certificate[] chain = loadCert(this.certValue.get());
      keyStore.setEntry("dummy-entry", new KeyStore.PrivateKeyEntry(key, chain), new KeyStore.PasswordProtection(DUMMY_PASSWORD.toCharArray()));
      return keyStore;
    }
  }

  static class CA extends KeyStoreHelper {

    private Stream<Buffer> certValues;

    CA(Stream<Buffer> certValues) {
      super(null);
      this.certValues = certValues;
    }

    @Override
    protected KeyStore loadStore(VertxInternal vertx, String password) throws Exception {
      KeyStore keyStore = KeyStore.getInstance("jks");
      keyStore.load(null, null);
      int count = 0;
      Iterable<Buffer> iterable = certValues::iterator;
      for (Buffer certValue : iterable) {
        for (Certificate cert : loadCert(certValue)) {
          keyStore.setCertificateEntry("cert-" + count, cert);
        }
      }
      return keyStore;
    }
  }

  private static byte[] loadPem(Buffer data, String delimiter) throws IOException {
    String pem = data.toString();
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

  private static PrivateKey loadPrivateKey(Buffer key) throws Exception {
    if (key == null) {
      throw new RuntimeException("Missing private key path");
    }
    byte[] value = loadPem(key, "PRIVATE KEY");
    KeyFactory rsaKeyFactory = KeyFactory.getInstance("RSA");
    return rsaKeyFactory.generatePrivate(new PKCS8EncodedKeySpec(value));
  }

  private static Certificate[] loadCert(Buffer cert) throws Exception {
    if (cert == null) {
      throw new RuntimeException("Missing X.509 certificate path");
    }
    byte[] value = loadPem(cert, "CERTIFICATE");
    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
    return certFactory.generateCertificates(new ByteArrayInputStream(value)).toArray(new Certificate[0]);
  }
}
