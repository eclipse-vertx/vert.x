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
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TrustOptions;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class KeyStoreHelper {

  // Dummy password for encrypting pem based stores in memory
  private static final String DUMMY_PASSWORD = "dummy";

  public static KeyStoreHelper create(VertxInternal vertx, KeyCertOptions options) throws Exception {
    if (options instanceof JksOptions) {
      JksOptions jks = (JksOptions) options;
      Supplier<Buffer> value;
      if (jks.getPath() != null) {
        value = () -> vertx.fileSystem().readFileBlocking(vertx.resolveFile(jks.getPath()).getAbsolutePath());
      } else if (jks.getValue() != null) {
        value = jks::getValue;
      } else {
        return null;
      }
      return new KeyStoreHelper(loadJKSOrPKCS12("JKS", jks.getPassword(), value), jks.getPassword());
    } else if (options instanceof PfxOptions) {
      PfxOptions pkcs12 = (PfxOptions) options;
      Supplier<Buffer> value;
      if (pkcs12.getPath() != null) {
        value = () -> vertx.fileSystem().readFileBlocking(vertx.resolveFile(pkcs12.getPath()).getAbsolutePath());
      } else if (pkcs12.getValue() != null) {
        value = pkcs12::getValue;
      } else {
        return null;
      }
      return new KeyStoreHelper(loadJKSOrPKCS12("PKCS12", pkcs12.getPassword(), value), pkcs12.getPassword());
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
      return new KeyStoreHelper(loadKeyCert(key, cert), DUMMY_PASSWORD);
    } else {
      return null;
    }
  }

  public static KeyStoreHelper create(VertxInternal vertx, TrustOptions options) throws Exception {
    if (options instanceof KeyCertOptions) {
      return create(vertx, (KeyCertOptions) options);
    } else if (options instanceof PemTrustOptions) {
      PemTrustOptions trustOptions = (PemTrustOptions) options;
      Stream<Buffer> certValues = trustOptions.
          getCertPaths().
          stream().
          map(path -> vertx.resolveFile(path).getAbsolutePath()).
          map(vertx.fileSystem()::readFileBlocking);
      certValues = Stream.concat(certValues, trustOptions.getCertValues().stream());
      return new KeyStoreHelper(loadCA(certValues), null);
    } else {
      return null;
    }
  }

  private final String password;
  private final KeyStore store;
  private final Map<String, X509KeyManager> mgrMap = new HashMap<>();

  public KeyStoreHelper(KeyStore ks, String password) throws Exception {

    //
    Enumeration<String> en = ks.aliases();
    while (en.hasMoreElements()) {
      String alias = en.nextElement();
      Certificate cert = ks.getCertificate(alias);
      if (cert instanceof X509Certificate) {
        X509Certificate x509Cert = (X509Certificate) cert;
        String dn = x509Cert.getSubjectX500Principal().getName();
        LdapName ldapDN = new LdapName(dn);
        for (Rdn rdn : ldapDN.getRdns()) {
          if (rdn.getType().equalsIgnoreCase("cn")) {
            String name = rdn.getValue().toString();
            PrivateKey key = (PrivateKey) ks.getKey(alias, password != null ? password.toCharArray() : null);
            Certificate[] certificateChain = ks.getCertificateChain(alias);
            List<X509Certificate> list = new ArrayList<>();
            if (certificateChain != null) {
              for (Certificate certificate : certificateChain) {
                if (certificate instanceof X509Certificate) {
                  list.add((X509Certificate) certificate);
                }
              }
            }
            X509KeyManager mgr = new X509KeyManager() {
              @Override
              public String[] getClientAliases(String s, Principal[] principals) {
                throw new UnsupportedOperationException();
              }
              @Override
              public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
                throw new UnsupportedOperationException();
              }
              @Override
              public String[] getServerAliases(String s, Principal[] principals) {
                throw new UnsupportedOperationException();
              }
              @Override
              public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
                throw new UnsupportedOperationException();
              }
              @Override
              public X509Certificate[] getCertificateChain(String s) {
                return list.toArray(new X509Certificate[list.size()]);
              }
              @Override
              public PrivateKey getPrivateKey(String s) {
                return key;
              }
            };
            mgrMap.put(name, mgr);
          }
        }
      }
    }

    this.store = ks;
    this.password = password;
  }

  public KeyManagerFactory getKeyMgrFactory() throws Exception {
    KeyManagerFactory fact = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    fact.init(store, password != null ? password.toCharArray(): null);
    return fact;
  }

  public X509KeyManager getKeyMgr(String serverName) {
    return mgrMap.get(serverName);
  }

  public KeyManager[] getKeyMgr() throws Exception {
    return getKeyMgrFactory().getKeyManagers();
  }

  public TrustManagerFactory getTrustMgrFactory(VertxInternal vertx) throws Exception {
    TrustManagerFactory fact = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    fact.init(store);
    return fact;
  }

  public TrustManager[] getTrustMgrs(VertxInternal vertx) throws Exception {
    return getTrustMgrFactory(vertx).getTrustManagers();
  }

  /**
   * @return the store
   */
  public KeyStore store() throws Exception {
    return store;
  }

  private static KeyStore loadJKSOrPKCS12(String type, String password, Supplier<Buffer> value) throws Exception {
    KeyStore ks = KeyStore.getInstance(type);
    InputStream in = null;
    try {
      in = new ByteArrayInputStream(value.get().getBytes());
      ks.load(in, password != null ? password.toCharArray(): null);
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

  private static KeyStore loadKeyCert(Supplier<Buffer> keyValue, Supplier<Buffer> certValue) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("jks");
    keyStore.load(null, null);
    PrivateKey key = loadPrivateKey(keyValue);
    Certificate[] chain = loadCerts(certValue);
    keyStore.setEntry("dummy-entry", new KeyStore.PrivateKeyEntry(key, chain), new KeyStore.PasswordProtection(DUMMY_PASSWORD.toCharArray()));
    return keyStore;
  }

  public static PrivateKey loadPrivateKey(Supplier<Buffer> keyValue) throws Exception {
    if (keyValue == null) {
      throw new RuntimeException("Missing private key path");
    }
    byte[] value = loadPems(keyValue.get(), "PRIVATE KEY").get(0);
    KeyFactory rsaKeyFactory = KeyFactory.getInstance("RSA");
    return rsaKeyFactory.generatePrivate(new PKCS8EncodedKeySpec(value));
  }

  public static X509Certificate[] loadCerts(Supplier<Buffer> certValue) throws Exception {
    return KeyStoreHelper.loadCerts(certValue.get());
  }

  private static KeyStore loadCA(Stream<Buffer> certValues) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("jks");
    keyStore.load(null, null);
    int count = 0;
    Iterable<Buffer> iterable = certValues::iterator;
    for (Buffer certValue : iterable) {
      for (Certificate cert : loadCerts(certValue)) {
        keyStore.setCertificateEntry("cert-" + count++, cert);
      }
    }
    return keyStore;
  }

  private static List<byte[]> loadPems(Buffer data, String delimiter) throws IOException {
    String pem = data.toString();
    String beginDelimiter = "-----BEGIN " + delimiter + "-----";
    String endDelimiter = "-----END " + delimiter + "-----";
    List<byte[]> pems = new ArrayList<>();
    int index = 0;
    while (true) {
      index = pem.indexOf(beginDelimiter, index);
      if (index == -1) {
        break;
      }
      index += beginDelimiter.length();
      int end = pem.indexOf(endDelimiter, index);
      if (end == -1) {
        throw new RuntimeException("Missing " + endDelimiter + " delimiter");
      }
      String content = pem.substring(index, end);
      content = content.replaceAll("\\s", "");
      if (content.length() == 0) {
        throw new RuntimeException("Empty pem file");
      }
      index = end + 1;
      pems.add(Base64.getDecoder().decode(content));
    }
    if (pems.isEmpty()) {
      throw new RuntimeException("Missing " + beginDelimiter + " delimiter");
    }
    return pems;
  }

  private static X509Certificate[] loadCerts(Buffer buffer) throws Exception {
    if (buffer == null) {
      throw new RuntimeException("Missing X.509 certificate path");
    }
    List<byte[]> pems = loadPems(buffer, "CERTIFICATE");
    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
    List<X509Certificate> certs = new ArrayList<>(pems.size());
    for (byte[] pem : pems) {
      for (Certificate cert : certFactory.generateCertificates(new ByteArrayInputStream(pem))) {
        certs.add((X509Certificate) cert);
      }
    }
    return certs.toArray(new X509Certificate[certs.size()]);
  }
}
