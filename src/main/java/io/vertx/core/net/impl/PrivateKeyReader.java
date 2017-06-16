/****************************************************************************
 * Copyright (c) 1998-2010 AOL Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ****************************************************************************/
package io.vertx.core.net.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.jmeter.services.FileServer;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;


/**
 * Class for reading RSA private key from PEM file. It uses
 * the JMeter FileServer to find the file. So the file should
 * be located in the same directory as the test plan if the
 * path is relative.
 *
 * <p/>There is a cache so each file is only read once. If file
 * is changed, it will not take effect until the program
 * restarts.
 *
 * <p/>It can read PEM files with PKCS#8 or PKCS#1 encodings.
 * It doesn't support encrypted PEM files.
 *
 */
public class PrivateKeyReader {

  private static final Logger log = LoggingManager.getLoggerForClass();

  // Private key file using PKCS #1 encoding
  public static final String P1_BEGIN_MARKER
    = "-----BEGIN RSA PRIVATE KEY"; //$NON-NLS-1$
  public static final String P1_END_MARKER
    = "-----END RSA PRIVATE KEY"; //$NON-NLS-1$

  // Private key file using PKCS #8 encoding
  public static final String P8_BEGIN_MARKER
    = "-----BEGIN PRIVATE KEY"; //$NON-NLS-1$
  public static final String P8_END_MARKER
    = "-----END PRIVATE KEY"; //$NON-NLS-1$

  private static Map<String, PrivateKey> keyCache =
    Collections.synchronizedMap(new HashMap<String, PrivateKey>());

  protected final FileServer server = FileServer.getFileServer();
  protected final String fileName;

  /**
   * Create a PEM private key file reader.
   *
   * @param fileName The name of the PEM file
   */
  public PrivateKeyReader(String fileName) {
    this.fileName = fileName;
  }

  /**
   * Get a Private Key for the file.
   *
   * @return Private key
   * @throws IOException
   */
  public PrivateKey getPrivateKey() throws IOException {

    PrivateKey key = keyCache.get(fileName);
    if (key != null) {
      log.debug("Key file " + fileName + " found in cache");  //$NON-NLS-1$ //$NON-NLS-2$
      return key;
    }

    // Load file
    server.reserveFile(fileName, "UTF-8", fileName); // $NON-NLS-1$ //$NON-NLS-1$
    key = read();
    server.closeFile(fileName);

    // Populate cache
    keyCache.put(fileName, key);
    log.debug("Key file " + fileName + " loaded in cache"); //$NON-NLS-1$ //$NON-NLS-2$

    return key;
  }


  /**
   * Read the PEM file and return the key
   *
   * @return
   * @throws IOException
   */
  private PrivateKey read() throws IOException {

    String  line;

    KeyFactory factory;
    try {
      factory = KeyFactory.getInstance("RSA"); //$NON-NLS-1$
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("JCE error: " + e.getMessage()); //$NON-NLS-1$
    }

    while ((line = server.readLine(fileName)) != null)
    {
      if (line.indexOf(P1_BEGIN_MARKER) != -1)
      {
        byte[] keyBytes = readKeyMaterial(P1_END_MARKER);
        RSAPrivateCrtKeySpec keySpec = getRSAKeySpec(keyBytes);

        try {
          return factory.generatePrivate(keySpec);
        } catch (InvalidKeySpecException e) {
          throw new IOException("Invalid PKCS#1 PEM file: " + e.getMessage()); //$NON-NLS-1$
        }
      }

      if (line.indexOf(P8_BEGIN_MARKER) != -1)
      {
        byte[] keyBytes = readKeyMaterial(P8_END_MARKER);
        EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

        try {
          return factory.generatePrivate(keySpec);
        } catch (InvalidKeySpecException e) {
          throw new IOException("Invalid PKCS#8 PEM file: " + e.getMessage()); //$NON-NLS-1$
        }
      }

    }

    throw new IOException("Invalid PEM file: no begin marker"); //$NON-NLS-1$
  }


  /**
   * Read the PEM file and convert it into binary DER stream
   *
   * @return
   * @throws IOException
   */
  private byte[] readKeyMaterial(String endMarker) throws IOException
  {
    String          line = null;
    StringBuffer    buf = new StringBuffer();

    while ((line = server.readLine(fileName)) != null)
    {
      if (line.indexOf(endMarker) != -1) {

        return new Base64().decode(buf.toString().getBytes());
      }

      buf.append(line.trim());
    }

    throw new IOException("Invalid PEM file: No end marker"); //$NON-NLS-1$
  }

  /**
   * Convert PKCS#1 encoded private key into RSAPrivateCrtKeySpec.
   *
   * <p/>The ASN.1 syntax for the private key with CRT is
   *
   * <pre>
   * --
   * -- Representation of RSA private key with information for the CRT algorithm.
   * --
   * RSAPrivateKey ::= SEQUENCE {
   *   version           Version,
   *   modulus           INTEGER,  -- n
   *   publicExponent    INTEGER,  -- e
   *   privateExponent   INTEGER,  -- d
   *   prime1            INTEGER,  -- p
   *   prime2            INTEGER,  -- q
   *   exponent1         INTEGER,  -- d mod (p-1)
   *   exponent2         INTEGER,  -- d mod (q-1)
   *   coefficient       INTEGER,  -- (inverse of q) mod p
   *   otherPrimeInfos   OtherPrimeInfos OPTIONAL
   * }
   * </pre>
   *
   * @param keyBytes PKCS#1 encoded key
   * @return KeySpec
   * @throws IOException
   */

  private RSAPrivateCrtKeySpec getRSAKeySpec(byte[] keyBytes) throws IOException  {

    DerParser parser = new DerParser(keyBytes);

    Asn1Object sequence = parser.read();
    if (sequence.getType() != DerParser.SEQUENCE)
      throw new IOException("Invalid DER: not a sequence"); //$NON-NLS-1$

    // Parse inside the sequence
    parser = sequence.getParser();

    parser.read(); // Skip version
    BigInteger modulus = parser.read().getInteger();
    BigInteger publicExp = parser.read().getInteger();
    BigInteger privateExp = parser.read().getInteger();
    BigInteger prime1 = parser.read().getInteger();
    BigInteger prime2 = parser.read().getInteger();
    BigInteger exp1 = parser.read().getInteger();
    BigInteger exp2 = parser.read().getInteger();
    BigInteger crtCoef = parser.read().getInteger();

    RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(
      modulus, publicExp, privateExp, prime1, prime2,
      exp1, exp2, crtCoef);

    return keySpec;
  }
}

/**
 * A bare-minimum ASN.1 DER decoder, just having enough functions to
 * decode PKCS#1 private keys. Especially, it doesn't handle explicitly
 * tagged types with an outer tag.
 *
 * <p/>This parser can only handle one layer. To parse nested constructs,
 * get a new parser for each layer using <code>Asn1Object.getParser()</code>.
 *
 * <p/>There are many DER decoders in JRE but using them will tie this
 * program to a specific JCE/JVM.
 *
 * @author zhang
 *
 */
class DerParser {

  // Classes
  public final static int UNIVERSAL = 0x00;
  public final static int APPLICATION = 0x40;
  public final static int CONTEXT = 0x80;
  public final static int PRIVATE = 0xC0;

  // Constructed Flag
  public final static int CONSTRUCTED = 0x20;

  // Tag and data types
  public final static int ANY = 0x00;
  public final static int BOOLEAN = 0x01;
  public final static int INTEGER = 0x02;
  public final static int BIT_STRING = 0x03;
  public final static int OCTET_STRING = 0x04;
  public final static int NULL = 0x05;
  public final static int OBJECT_IDENTIFIER = 0x06;
  public final static int REAL = 0x09;
  public final static int ENUMERATED = 0x0a;
  public final static int RELATIVE_OID = 0x0d;

  public final static int SEQUENCE = 0x10;
  public final static int SET = 0x11;

  public final static int NUMERIC_STRING = 0x12;
  public final static int PRINTABLE_STRING = 0x13;
  public final static int T61_STRING = 0x14;
  public final static int VIDEOTEX_STRING = 0x15;
  public final static int IA5_STRING = 0x16;
  public final static int GRAPHIC_STRING = 0x19;
  public final static int ISO646_STRING = 0x1A;
  public final static int GENERAL_STRING = 0x1B;

  public final static int UTF8_STRING = 0x0C;
  public final static int UNIVERSAL_STRING = 0x1C;
  public final static int BMP_STRING = 0x1E;

  public final static int UTC_TIME = 0x17;
  public final static int GENERALIZED_TIME = 0x18;

  protected InputStream in;

  /**
   * Create a new DER decoder from an input stream.
   *
   * @param in
   *            The DER encoded stream
   */
  public DerParser(InputStream in) throws IOException {
    this.in = in;
  }

  /**
   * Create a new DER decoder from a byte array.
   *
   * @param The
   *            encoded bytes
   * @throws IOException
   */
  public DerParser(byte[] bytes) throws IOException {
    this(new ByteArrayInputStream(bytes));
  }

  /**
   * Read next object. If it's constructed, the value holds
   * encoded content and it should be parsed by a new
   * parser from <code>Asn1Object.getParser</code>.
   *
   * @return A object
   * @throws IOException
   */
  public Asn1Object read() throws IOException {
    int tag = in.read();

    if (tag == -1)
      throw new IOException("Invalid DER: stream too short, missing tag"); //$NON-NLS-1$

    int length = getLength();

    byte[] value = new byte[length];
    int n = in.read(value);
    if (n < length)
      throw new IOException("Invalid DER: stream too short, missing value"); //$NON-NLS-1$

    Asn1Object o = new Asn1Object(tag, length, value);

    return o;
  }

  /**
   * Decode the length of the field. Can only support length
   * encoding up to 4 octets.
   *
   * <p/>In BER/DER encoding, length can be encoded in 2 forms,
   * <ul>
   * <li>Short form. One octet. Bit 8 has value "0" and bits 7-1
   * give the length.
   * <li>Long form. Two to 127 octets (only 4 is supported here).
   * Bit 8 of first octet has value "1" and bits 7-1 give the
   * number of additional length octets. Second and following
   * octets give the length, base 256, most significant digit first.
   * </ul>
   * @return The length as integer
   * @throws IOException
   */
  private int getLength() throws IOException {

    int i = in.read();
    if (i == -1)
      throw new IOException("Invalid DER: length missing"); //$NON-NLS-1$

    // A single byte short length
    if ((i & ~0x7F) == 0)
      return i;

    int num = i & 0x7F;

    // We can't handle length longer than 4 bytes
    if ( i >= 0xFF || num > 4)
      throw new IOException("Invalid DER: length field too big (" //$NON-NLS-1$
        + i + ")"); //$NON-NLS-1$

    byte[] bytes = new byte[num];
    int n = in.read(bytes);
    if (n < num)
      throw new IOException("Invalid DER: length too short"); //$NON-NLS-1$

    return new BigInteger(1, bytes).intValue();
  }

}


/**
 * An ASN.1 TLV. The object is not parsed. It can
 * only handle integers and strings.
 *
 * @author zhang
 *
 */
class Asn1Object {

  protected final int type;
  protected final int length;
  protected final byte[] value;
  protected final int tag;

  /**
   * Construct a ASN.1 TLV. The TLV could be either a
   * constructed or primitive entity.
   *
   * <p/>The first byte in DER encoding is made of following fields,
   * <pre>
   *-------------------------------------------------
   *|Bit 8|Bit 7|Bit 6|Bit 5|Bit 4|Bit 3|Bit 2|Bit 1|
   *-------------------------------------------------
   *|  Class    | CF  |     +      Type             |
   *-------------------------------------------------
   * </pre>
   * <ul>
   * <li>Class: Universal, Application, Context or Private
   * <li>CF: Constructed flag. If 1, the field is constructed.
   * <li>Type: This is actually called tag in ASN.1. It
   * indicates data type (Integer, String) or a construct
   * (sequence, choice, set).
   * </ul>
   *
   * @param tag Tag or Identifier
   * @param length Length of the field
   * @param value Encoded octet string for the field.
   */
  public Asn1Object(int tag, int length, byte[] value) {
    this.tag = tag;
    this.type = tag & 0x1F;
    this.length = length;
    this.value = value;
  }

  public int getType() {
    return type;
  }

  public int getLength() {
    return length;
  }

  public byte[] getValue() {
    return value;
  }

  public boolean isConstructed() {
    return  (tag & DerParser.CONSTRUCTED) == DerParser.CONSTRUCTED;
  }

  /**
   * For constructed field, return a parser for its content.
   *
   * @return A parser for the construct.
   * @throws IOException
   */
  public DerParser getParser() throws IOException {
    if (!isConstructed())
      throw new IOException("Invalid DER: can't parse primitive entity"); //$NON-NLS-1$

    return new DerParser(value);
  }

  /**
   * Get the value as integer
   *
   * @return BigInteger
   * @throws IOException
   */
  public BigInteger getInteger() throws IOException {
    if (type != DerParser.INTEGER)
      throw new IOException("Invalid DER: object is not integer"); //$NON-NLS-1$

    return new BigInteger(value);
  }

  /**
   * Get value as string. Most strings are treated
   * as Latin-1.
   *
   * @return Java string
   * @throws IOException
   */
  public String getString() throws IOException {

    String encoding;

    switch (type) {

      // Not all are Latin-1 but it's the closest thing
      case DerParser.NUMERIC_STRING:
      case DerParser.PRINTABLE_STRING:
      case DerParser.VIDEOTEX_STRING:
      case DerParser.IA5_STRING:
      case DerParser.GRAPHIC_STRING:
      case DerParser.ISO646_STRING:
      case DerParser.GENERAL_STRING:
        encoding = "ISO-8859-1"; //$NON-NLS-1$
        break;

      case DerParser.BMP_STRING:
        encoding = "UTF-16BE"; //$NON-NLS-1$
        break;

      case DerParser.UTF8_STRING:
        encoding = "UTF-8"; //$NON-NLS-1$
        break;

      case DerParser.UNIVERSAL_STRING:
        throw new IOException("Invalid DER: can't handle UCS-4 string"); //$NON-NLS-1$

      default:
        throw new IOException("Invalid DER: object is not a string"); //$NON-NLS-1$
    }

    return new String(value, encoding);
  }
}
