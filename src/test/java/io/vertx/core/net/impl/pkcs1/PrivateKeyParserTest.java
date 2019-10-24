/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */


package io.vertx.core.net.impl.pkcs1;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;


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
}
