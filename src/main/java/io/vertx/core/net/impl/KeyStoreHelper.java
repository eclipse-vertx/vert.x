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

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class KeyStoreHelper {

  // Dummy password for encrypting pem based stores in memory
  private static final String DUMMY_PASSWORD = "dummy";
  private static final Pattern BEGIN_PATTERN = Pattern.compile("-----BEGIN ([A-Z ]+)-----");
  private static final Pattern END_PATTERN = Pattern.compile("-----END ([A-Z ]+)-----");

  private static class Pem {
    private String delimitier;
    private byte[] content;
  }
  
  public static KeyStoreHelper create(VertxInternal vertx, KeyCertOptions options) {
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
      return new JKSOrPKCS12("JKS", jks.getPassword(), value);
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

  public static KeyStoreHelper create(VertxInternal vertx, TrustOptions options) {
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
      return new CA(certValues);
    } else {
      return null;
    }
  }

  protected final String password;

  public KeyStoreHelper(String password) {
    this.password = password;
  }

  public KeyManagerFactory getKeyMgrFactory(VertxInternal vertx) throws Exception {
    KeyManagerFactory fact = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    fact.getProvider();
    KeyStore ks = loadStore(vertx);
    fact.init(ks, password != null ? password.toCharArray(): null);
    return fact;
  }

  public KeyManager[] getKeyMgrs(VertxInternal vertx) throws Exception {
    return getKeyMgrFactory(vertx).getKeyManagers();
  }

  public TrustManagerFactory getTrustMgrFactory(VertxInternal vertx) throws Exception {
    TrustManagerFactory fact = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    KeyStore ts = loadStore(vertx);
    fact.init(ts);
    return fact;
  }

  public TrustManager[] getTrustMgrs(VertxInternal vertx) throws Exception {
    return getTrustMgrFactory(vertx).getTrustManagers();
  }

  /**
   * Load the keystore.
   *
   * @param vertx the vertx instance
   * @return the key store
   */
  public abstract KeyStore loadStore(VertxInternal vertx) throws Exception;

  static class JKSOrPKCS12 extends KeyStoreHelper {

    private String type;
    private Supplier<Buffer> value;

    JKSOrPKCS12(String type, String password, Supplier<Buffer> value) {
      super(password);
      this.type = type;
      this.value = value;
    }

    public KeyStore loadStore(VertxInternal vertx) throws Exception {
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
    public KeyStore loadStore(VertxInternal vertx) throws Exception {
      KeyStore keyStore = KeyStore.getInstance("jks");
      keyStore.load(null, null);
      PrivateKey key = loadPrivateKey();
      Certificate[] chain = loadCerts();
      keyStore.setEntry("dummy-entry", new KeyStore.PrivateKeyEntry(key, chain), new KeyStore.PasswordProtection(DUMMY_PASSWORD.toCharArray()));
      return keyStore;
    }

    public PrivateKey loadPrivateKey() throws Exception {
      if (keyValue == null) {
        throw new RuntimeException("Missing private key path");
      }      
      List<Pem> pems = loadPems(keyValue.get(), "PRIVATE KEY");
      KeyFactory rsaKeyFactory = KeyFactory.getInstance("RSA");
      Pem pem = pems.get(0);
      if("RSA PRIVATE KEY".equals(pem.delimitier))
        return rsaKeyFactory.generatePrivate(parseRsa(pem.content));
      else
        return rsaKeyFactory.generatePrivate(new PKCS8EncodedKeySpec(pem.content));
    }

    private RSAPrivateCrtKeySpec parseRsa(byte[] keyBytes) {
        List<BigInteger> rsaIntegers = new ArrayList<BigInteger>(8);
        ASN1Parse(keyBytes, rsaIntegers);
        if (rsaIntegers.size() < 8)  {
          throw new RuntimeException("Invalid formatted RSA key");
        }
        
        BigInteger modulus = rsaIntegers.get(1);
        BigInteger pubExp = rsaIntegers.get(2);
        BigInteger privExp = rsaIntegers.get(3);
        BigInteger primeP = rsaIntegers.get(4);
        BigInteger primeQ = rsaIntegers.get(5);
        BigInteger primeExponentP = rsaIntegers.get(6);
        BigInteger primeExponentQ = rsaIntegers.get(7);
        BigInteger crtCoefficient = rsaIntegers.get(8);
        
        RSAPrivateCrtKeySpec privSpec = new RSAPrivateCrtKeySpec(
                modulus, pubExp, privExp,
                primeP, primeQ,
                primeExponentP, primeExponentQ,
                crtCoefficient);
        return privSpec;
    }

    /**
     * @author jeho0815
     */
    private static void ASN1Parse(byte[] b, List<BigInteger> integers) {            
      int pos = 0;
      while (pos < b.length)  {
        byte tag = b[pos++];
        int length = b[pos++];
        if ((length & 0x80) != 0)  {
          int extLen = 0;
          for (int i = 0; i < (length & 0x7F); i++)  {
            extLen = (extLen << 8) | (b[pos++] & 0xFF);
          }
          length = extLen;
        }
        byte[] contents = new byte[length];
        System.arraycopy(b, pos, contents, 0, length);
        pos += length;

        if (tag == 0x30)  {  // sequence
          ASN1Parse(contents, integers);
        } else if (tag == 0x02)  {  // Integer
          BigInteger i = new BigInteger(contents);
          integers.add(i);
        } else  {
          throw new RuntimeException("Unsupported ASN.1 tag " + tag + " encountered.  Is this a " +
            "valid RSA key?");
        }
      }
    }
    
    public X509Certificate[] loadCerts() throws Exception {
      return KeyStoreHelper.loadCerts(certValue.get());
    }
  }

  static class CA extends KeyStoreHelper {

    private Stream<Buffer> certValues;

    CA(Stream<Buffer> certValues) {
      super(null);
      this.certValues = certValues;
    }

    @Override
    public KeyStore loadStore(VertxInternal vertx) throws Exception {
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
  }
    
  private static List<Pem> loadPems(Buffer data, String delimiter) throws IOException {
    String pem = data.toString();
    List<Pem> pems = new ArrayList<>();
    int index = 0;
    Matcher beginMatcher = BEGIN_PATTERN.matcher(pem);
    Matcher endMatcher = END_PATTERN.matcher(pem);
    
    while (true) {
      boolean begin = beginMatcher.find();
      if (!begin) {
        break;
      }
      
      String beginDelimiter = beginMatcher.group(1);
      index += beginMatcher.end();
      boolean end = endMatcher.find();
      
      if (!end) {
        throw new RuntimeException("Missing -----END" + beginDelimiter + "----- delimiter");
      } else {
        String endDelimiter = endMatcher.group(1);
        if(!beginDelimiter.equals(endDelimiter)) { 
          throw new RuntimeException("Missing -----END" + beginDelimiter + "----- delimiter");  
        } else if(endDelimiter.contains(delimiter)) {
          String content = pem.substring(index, endMatcher.start());
          content = content.replaceAll("\\s", "");
          if (content.length() == 0) {
            throw new RuntimeException("Empty pem file");
          }
          Pem pemItem = new Pem();
          pemItem.content = Base64.getDecoder().decode(content);
          pemItem.delimitier = endDelimiter;
          pems.add(pemItem);
        }
        
        index = endMatcher.end() + 1;        
      }
    }
    if (pems.isEmpty()) {
      throw new RuntimeException("Missing -----BEGIN " + delimiter + "----- delimiter");
    }
    return pems;
  }

  private static X509Certificate[] loadCerts(Buffer buffer) throws Exception {
    if (buffer == null) {
      throw new RuntimeException("Missing X.509 certificate path");
    }
    String delimitier = "CERTIFICATE";
    List<Pem> pems = loadPems(buffer, delimitier);
    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
    List<X509Certificate> certs = new ArrayList<>(pems.size());
    for (Pem pem : pems) {
      if(delimitier.equals(pem.delimitier)) {
        for (Certificate cert : certFactory.generateCertificates(new ByteArrayInputStream(pem.content))) {
          certs.add((X509Certificate) cert);
        }
      }
    }
    return certs.toArray(new X509Certificate[certs.size()]);
  }
}
