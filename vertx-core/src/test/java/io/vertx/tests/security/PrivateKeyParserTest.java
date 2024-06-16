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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;

import io.vertx.core.net.impl.pkcs1.PrivateKeyParser;
import org.junit.Assume;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.test.core.TestUtils;


/**
 * Verifies behavior of {@link PrivateKeyParser}.
 *
 */
public class PrivateKeyParserTest {

  /**
   * Verifies that the parser correctly identifies a
   * PKCS#8 encoded RSA private key.
   */
  @Test
  public void testGetPKCS8EncodedKeySpecSupportsRSA() {

    // the following ASN.1 structure only contains
    // the objects relevant for identifying the type of key
    byte[] pkcs8 = new byte[] {
                            0x30, 0x12, // SEQUENCE of 18 bytes
                            0x02, 0x01, 0x00, // version = 0
                            0x30, 0x0D, // SEQUENCE of 13 bytes
                            0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86,
                            (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01, // RSA OID
                            0x05, 0x00 // NULL
    };
    assertKeySpecType(pkcs8, "RSA");
  }

  /**
   * Verifies that the parser correctly identifies a
   * PKCS#8 encoded ECC private key.
   */
  @Test
  public void testGetPKCS8EncodedKeySpecSupportsEC() {

    // the following ASN.1 structure only contains
    // the objects relevant for identifying the type of key
    byte[] pkcs8 = new byte[] {
                            0x30, 0x18, // SEQUENCE of 24 bytes
                            0x02, 0x01, 0x00, // version = 0
                            0x30, 0x13, // SEQUENCE of 19 bytes
                            0x06, 0x07, 0x2A, (byte) 0x86, 0x48,
                            (byte) 0xCE, 0x3D, 0x02, 0x01, // EC OID
                            0x06, 0x08, 0x2A, (byte) 0x86, 0x48,
                            (byte) 0xCE, 0x3D, 0x03, 0x01, 0x07 // prime256v1 OID
    };
    assertKeySpecType(pkcs8, "EC");
  }

  private void assertKeySpecType(byte[] encodedKey, String expectedAlgorithm) {
    String keyAlgorithm = PrivateKeyParser.getPKCS8EncodedKeyAlgorithm(encodedKey);
    assertThat(keyAlgorithm, is(expectedAlgorithm));
  }

  /**
   * Verifies that the parser can read a DER encoded ECPrivateKey.
   *
   * @throws GeneralSecurityException if the JVM does not support
   */
  @Test
  public void testGetECKeySpecSucceedsForDEREncodedECPrivateKey() throws GeneralSecurityException {

    Assume.assumeTrue("ECC is not supported by VM's security providers", TestUtils.isECCSupportedByVM());
    Vertx vertx = Vertx.vertx();
    String b = vertx.fileSystem().readFileBlocking("tls/server-key-ec-pkcs1.pem")
        .toString(StandardCharsets.US_ASCII)
        .replaceAll("-----BEGIN EC PRIVATE KEY-----", "")
        .replaceAll("-----END EC PRIVATE KEY-----", "")
        .replaceAll("\\s", "");
    byte[] derEncoding = Base64.getDecoder().decode(b);
    ECPrivateKeySpec spec = PrivateKeyParser.getECKeySpec(derEncoding);
    KeyFactory factory = KeyFactory.getInstance("EC");
    ECPrivateKey key = (ECPrivateKey) factory.generatePrivate(spec);
    assertThat(key, notNullValue());
    assertThat(key.getAlgorithm(), is("EC"));
  }
}
