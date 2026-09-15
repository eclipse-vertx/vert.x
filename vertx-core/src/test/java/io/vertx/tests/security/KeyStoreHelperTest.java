/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */


package io.vertx.tests.security;

import org.assertj.core.api.Assertions;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Enumeration;

import io.vertx.core.VertxException;
import io.vertx.core.net.PemKeyCertOptions;
import org.junit.Assume;
import org.junit.Test;

import io.vertx.core.net.impl.KeyStoreHelper;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;


/**
 * Verifies behavior of {@link KeyStoreHelper}.
 *
 */
public class KeyStoreHelperTest extends VertxTestBase {

  /**
   * Verifies that the key store helper can read a PKCS#8 encoded RSA private key
   * from a PEM file.
   *
   * @throws Exception if the key cannot be read.
   */
  @Test
  public void testKeyStoreHelperSupportsRSAPrivateKeys() throws Exception {
    PemKeyCertOptions options = new PemKeyCertOptions()
            .addKeyPath("tls/server-key.pem")
            .addCertPath("tls/server-cert.pem");
    KeyStoreHelper helper = options.getHelper(vertx);
    assertKeyType(helper.store(), RSAPrivateKey.class);
  }

  /**
   * Verifies that the key store helper can read a PKCS#8 encoded EC private key
   * from a PEM file.
   *
   * @throws Exception if the key cannot be read.
   */
  @Test
  public void testKeyStoreHelperSupportsPKCS8ECPrivateKey() throws Exception {

    Assume.assumeTrue("ECC is not supported by VM's security providers", TestUtils.isECCSupportedByVM());
    PemKeyCertOptions options = new PemKeyCertOptions()
            .addKeyPath("tls/server-key-ec.pem")
            .addCertPath("tls/server-cert-ec.pem");
    KeyStoreHelper helper = options.getHelper(vertx);
    assertKeyType(helper.store(), ECPrivateKey.class);
  }

  /**
   * Verifies that the key store helper can read a DER encoded EC private key
   * from a PEM file.
   */
  @Test
  public void testKeyStoreHelperSupportsReadingECPrivateKeyFromPEMFile() throws Exception {

    Assume.assumeTrue("ECC is not supported by VM's security providers", TestUtils.isECCSupportedByVM());
    PemKeyCertOptions options = new PemKeyCertOptions()
            .addKeyPath("tls/server-key-ec-pkcs1.pem")
            .addCertPath("tls/server-cert-ec.pem");
    KeyStoreHelper helper = options.getHelper(vertx);
    assertKeyType(helper.store(), ECPrivateKey.class);
  }

  /**
   * Verifies that the key store helper can read a PKCS#8 encoded ML-DSA private key
   * from a PEM file when the JVM supports ML-DSA, or fails with a clear error message
   * when the JVM does not.
   */
  @Test
  public void testKeyStoreHelperSupportsMLDSAPrivateKey() throws Exception {

    PemKeyCertOptions options = new PemKeyCertOptions()
            .addKeyPath("tls/server-key-mldsa.pem")
            .addCertPath("tls/server-cert-mldsa.pem");
    boolean mldsaSupported;
    try {
      KeyFactory.getInstance("ML-DSA");
      mldsaSupported = true;
    } catch (NoSuchAlgorithmException e) {
      mldsaSupported = false;
    }
    if (mldsaSupported) {
      KeyStoreHelper helper = options.getHelper(vertx);
      assertTrue(helper.store().size() > 0);
      for (Enumeration<String> e = helper.store().aliases(); e.hasMoreElements(); ) {
        String alias = e.nextElement();
        PrivateKey key = (PrivateKey) helper.store().getKey(alias, KeyStoreHelper.DUMMY_PASSWORD.toCharArray());
        Assertions.assertThat(key).isNotNull();
        Assertions.assertThat(key.getAlgorithm()).isEqualTo("ML-DSA");
      }
    } else {
      try {
        options.getHelper(vertx);
        fail("Expected VertxException");
      } catch (VertxException e) {
        Assertions.assertThat(e.getMessage()).isEqualTo("ML-DSA algorithm is not supported by this JVM");
        Assertions.assertThat(e.getCause()).isInstanceOf(NoSuchAlgorithmException.class);
      }
    }
  }

  private void assertKeyType(KeyStore store, Class<?> expectedKeyType) throws KeyStoreException, GeneralSecurityException {
    assertTrue(store.size() > 0);
    for (Enumeration<String> e = store.aliases(); e.hasMoreElements(); ) {
      String alias = e.nextElement();
      // "dummdummydummydummydummydummydummy" is the password set by KeyStoreHelper when importing the
      // keys into the internal key store
      Assertions.assertThat(store.getKey(alias, "dummdummydummydummydummydummydummy".toCharArray())).isInstanceOf(expectedKeyType);
      Assertions.assertThat(store.getCertificate(alias)).isInstanceOf(X509Certificate.class);
    }
  }
}
