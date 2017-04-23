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

import io.vertx.core.VertxException;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
      List<Buffer> keys = new ArrayList<>();
      for (String keyPath : keyCert.getKeyPaths()) {
        keys.add(vertx.fileSystem().readFileBlocking(vertx.resolveFile(keyPath).getAbsolutePath()));
      }
      keys.addAll(keyCert.getKeyValues());
      List<Buffer> certs = new ArrayList<>();
      for (String certPath : keyCert.getCertPaths()) {
        certs.add(vertx.fileSystem().readFileBlocking(vertx.resolveFile(certPath).getAbsolutePath()));
      }
      certs.addAll(keyCert.getCertValues());
      return new KeyStoreHelper(loadKeyCert(keys, certs), DUMMY_PASSWORD);
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
  private final Map<String, X509KeyManager> wildcardMgrMap = new HashMap<>();
  private final Map<String, X509KeyManager> mgrMap = new HashMap<>();

  public KeyStoreHelper(KeyStore ks, String password) throws Exception {
    Enumeration<String> en = ks.aliases();
    while (en.hasMoreElements()) {
      String alias = en.nextElement();
      Certificate cert = ks.getCertificate(alias);
      if (cert instanceof X509Certificate) {
        X509Certificate x509Cert = (X509Certificate) cert;
        Collection<List<?>> ans = x509Cert.getSubjectAlternativeNames();
        List<String> domains = new ArrayList<>();
        if (ans != null) {
          for (List<?> l : ans) {
            if (l.size() == 2 && l.get(0) instanceof Number && ((Number) l.get(0)).intValue() == 2) {
              String dns = l.get(1).toString();
              domains.add(dns);
            }
          }
        }
        String dn = x509Cert.getSubjectX500Principal().getName();
        LdapName ldapDN = new LdapName(dn);
        for (Rdn rdn : ldapDN.getRdns()) {
          if (rdn.getType().equalsIgnoreCase("cn")) {
            String name = rdn.getValue().toString();
            domains.add(name);
          }
        }
        if (domains.size() > 0) {
          PrivateKey key = (PrivateKey) ks.getKey(alias, password != null ? password.toCharArray() : null);
          Certificate[] tmp = ks.getCertificateChain(alias);
          if (tmp == null) {
            // It's a private key
            continue;
          }
          List<X509Certificate> chain = Arrays.asList(tmp)
              .stream()
              .map(c -> (X509Certificate)c)
              .collect(Collectors.toList());
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
              return chain.toArray(new X509Certificate[chain.size()]);
            }
            @Override
            public PrivateKey getPrivateKey(String s) {
              return key;
            }
          };
          for (String domain : domains) {
            if (domain.startsWith("*.")) {
              wildcardMgrMap.put(domain.substring(2), mgr);
            } else {
              mgrMap.put(domain, mgr);
            }
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
    X509KeyManager mgr = mgrMap.get(serverName);
    if (mgr == null && !wildcardMgrMap.isEmpty()) {
      int index = serverName.indexOf('.') + 1;
      if (index > 0) {
        String s = serverName.substring(index);
        mgr = wildcardMgrMap.get(s);
      }
    }
    return mgr;
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

  private static KeyStore loadKeyCert(List<Buffer> keyValue, List<Buffer> certValue) throws Exception {
    if (keyValue.size() < certValue.size()) {
      throw new VertxException("Missing private key");
    } else if (keyValue.size() > certValue.size()) {
      throw new VertxException("Missing X.509 certificate");
    }
    KeyStore keyStore = KeyStore.getInstance("jks");
    keyStore.load(null, null);
    Iterator<Buffer> keyValueIt = keyValue.iterator();
    Iterator<Buffer> certValueIt = certValue.iterator();
    int index = 0;
    while (keyValueIt.hasNext() && certValueIt.hasNext()) {
      PrivateKey key = loadPrivateKey(keyValueIt.next());
      Certificate[] chain = loadCerts(certValueIt.next());
      keyStore.setEntry("dummy-entry-" + index++, new KeyStore.PrivateKeyEntry(key, chain), new KeyStore.PasswordProtection(DUMMY_PASSWORD.toCharArray()));
    }
    return keyStore;
  }

  public static PrivateKey loadPrivateKey(Buffer keyValue) throws Exception {
    if (keyValue == null) {
      throw new RuntimeException("Missing private key path");
    }
    byte[] value = loadPems(keyValue, "PRIVATE KEY").get(0);
    KeyFactory rsaKeyFactory = KeyFactory.getInstance("RSA");
    return rsaKeyFactory.generatePrivate(new PKCS8EncodedKeySpec(value));
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
