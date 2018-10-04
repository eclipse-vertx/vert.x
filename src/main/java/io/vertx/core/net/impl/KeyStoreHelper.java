/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
import io.vertx.core.net.impl.pkcs1.PrivateKeyParser;

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
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class KeyStoreHelper {

  // Dummy password for encrypting pem based stores in memory
  private static final String DUMMY_PASSWORD = "dummy";
  private static final String DUMMY_CERT_ALIAS = "cert-";

  private static final Pattern BEGIN_PATTERN = Pattern.compile("-----BEGIN ([A-Z ]+)-----");
  private static final Pattern END_PATTERN = Pattern.compile("-----END ([A-Z ]+)-----");

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
  private final Map<String, TrustManagerFactory> trustMgrMap = new HashMap<>();

  public KeyStoreHelper(KeyStore ks, String password) throws Exception {
    Enumeration<String> en = ks.aliases();
    while (en.hasMoreElements()) {
      String alias = en.nextElement();
      Certificate cert = ks.getCertificate(alias);
      if (ks.isCertificateEntry(alias) && ! alias.startsWith(DUMMY_CERT_ALIAS)){
        final KeyStore keyStore = createEmptyKeyStore();
        keyStore.setCertificateEntry("cert-1", cert);
        TrustManagerFactory fact = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        fact.init(keyStore);
        trustMgrMap.put(alias, fact);
      }
      if (ks.isKeyEntry(alias) && cert instanceof X509Certificate) {
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

  public TrustManager[] getTrustMgr(String serverName) {
    TrustManagerFactory fact = trustMgrMap.get(serverName);
    return fact != null ? fact.getTrustManagers() : null;
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
    final KeyStore keyStore = createEmptyKeyStore();
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

  private static PrivateKey loadPrivateKey(Buffer keyValue) throws Exception {
    if (keyValue == null) {
      throw new RuntimeException("Missing private key path");
    }
    KeyFactory rsaKeyFactory = KeyFactory.getInstance("RSA");
    List<PrivateKey> pems = loadPems(keyValue, (delimiter, content) -> {
      try {
        switch (delimiter) {
          case "RSA PRIVATE KEY":
            return Collections.singletonList(rsaKeyFactory.generatePrivate(PrivateKeyParser.getRSAKeySpec(content)));
          case "PRIVATE KEY":
            return Collections.singletonList(rsaKeyFactory.generatePrivate(new PKCS8EncodedKeySpec(content)));
          default:
            return Collections.emptyList();
        }
      } catch (InvalidKeySpecException e) {
        throw new VertxException(e);
      }
    });
    if (pems.isEmpty()) {
      throw new RuntimeException("Missing -----BEGIN PRIVATE KEY----- or -----BEGIN RSA PRIVATE KEY----- delimiter");
    }
    return pems.get(0);
  }

  private static KeyStore loadCA(Stream<Buffer> certValues) throws Exception {
    final KeyStore keyStore = createEmptyKeyStore();
    keyStore.load(null, null);
    int count = 0;
    Iterable<Buffer> iterable = certValues::iterator;
    for (Buffer certValue : iterable) {
      for (Certificate cert : loadCerts(certValue)) {
        keyStore.setCertificateEntry(DUMMY_CERT_ALIAS + count++, cert);
      }
    }
    return keyStore;
  }

  private static <P> List<P> loadPems(Buffer data, BiFunction<String, byte[], Collection<P>> pemFact) throws IOException {
    String pem = data.toString();
    List<P> pems = new ArrayList<>();
    Matcher beginMatcher = BEGIN_PATTERN.matcher(pem);
    Matcher endMatcher = END_PATTERN.matcher(pem);
    while (true) {
      boolean begin = beginMatcher.find();
      if (!begin) {
        break;
      }
      String beginDelimiter = beginMatcher.group(1);
      boolean end = endMatcher.find();
      if (!end) {
        throw new RuntimeException("Missing -----END " + beginDelimiter + "----- delimiter");
      } else {
        String endDelimiter = endMatcher.group(1);
        if (!beginDelimiter.equals(endDelimiter)) {
          throw new RuntimeException("Missing -----END " + beginDelimiter + "----- delimiter");
        } else {
          String content = pem.substring(beginMatcher.end(), endMatcher.start());
          content = content.replaceAll("\\s", "");
          if (content.length() == 0) {
            throw new RuntimeException("Empty pem file");
          }
          Collection<P> pemItems = pemFact.apply(endDelimiter, Base64.getDecoder().decode(content));
          pems.addAll(pemItems);
        }
      }
    }
    return pems;
  }

  private static X509Certificate[] loadCerts(Buffer buffer) throws Exception {
    if (buffer == null) {
      throw new RuntimeException("Missing X.509 certificate path");
    }
    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
    List<X509Certificate> certs = loadPems(buffer, (delimiter, content) -> {
      try {
        switch (delimiter) {
          case "CERTIFICATE":
            return (Collection<X509Certificate>) certFactory.generateCertificates(new ByteArrayInputStream(content));
          default:
            return Collections.emptyList();
        }
      } catch (CertificateException e) {
        throw new VertxException(e);
      }
    });
    if (certs.isEmpty()) {
      throw new RuntimeException("Missing -----BEGIN CERTIFICATE----- delimiter");
    }
    return certs.toArray(new X509Certificate[certs.size()]);
  }

  /**
   * Creates a empty key store using the industry standard PCKS12.
   *
   * The format is the default format for keystores for Java >=9 and available on GraalVM.
   *
   * PKCS12 is an extensible, standard, and widely-supported format for storing cryptographic keys.
   * As of JDK 8, PKCS12 keystores can store private keys, trusted public key certificates, and
   * secret keys.
   *
   * The "old" default "JKS" (available since Java 1.2) can only store private keys and trusted
   * public-key certificates, and they are based on a proprietary format that is not easily
   * extensible to new cryptographic algorithms.

   * @return keystore instance
   *
   * @throws KeyStoreException if the underlying engine cannot create an instance
   */
  private static KeyStore createEmptyKeyStore() throws KeyStoreException {
    final KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try {
      keyStore.load(null, null);
    } catch (CertificateException | NoSuchAlgorithmException | IOException e) {
      // these exceptions should never be thrown as there is no initial data
      // provided to the initialization of the keystore
      throw new KeyStoreException("Failed to initialize the keystore", e);
    }
    return keyStore;
  }
}
