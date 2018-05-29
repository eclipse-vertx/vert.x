/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.util.NetUtil;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.net.*;
import io.vertx.test.netty.TestLoggerFactory;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.cert.X509Certificate;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestUtils {

  private static Random random = new Random();

  /**
   * Creates a Buffer of random bytes.
   *
   * @param length The length of the Buffer
   * @return the Buffer
   */
  public static Buffer randomBuffer(int length) {
    return randomBuffer(length, false, (byte) 0);
  }

  /**
   * Create an array of random bytes
   *
   * @param length The length of the created array
   * @return the byte array
   */
  public static byte[] randomByteArray(int length) {
    return randomByteArray(length, false, (byte) 0);
  }

  /**
   * Create an array of random bytes
   *
   * @param length    The length of the created array
   * @param avoid     If true, the resulting array will not contain avoidByte
   * @param avoidByte A byte that is not to be included in the resulting array
   * @return an array of random bytes
   */
  public static byte[] randomByteArray(int length, boolean avoid, byte avoidByte) {
    byte[] line = new byte[length];
    for (int i = 0; i < length; i++) {
      byte rand;
      do {
        rand = randomByte();
      } while (avoid && rand == avoidByte);

      line[i] = rand;
    }
    return line;
  }

  /**
   * Creates a Buffer containing random bytes
   *
   * @param length    the size of the Buffer to create
   * @param avoid     if true, the resulting Buffer will not contain avoidByte
   * @param avoidByte A byte that is not to be included in the resulting array
   * @return a Buffer of random bytes
   */
  public static Buffer randomBuffer(int length, boolean avoid, byte avoidByte) {
    byte[] line = randomByteArray(length, avoid, avoidByte);
    return Buffer.buffer(line);
  }

  /**
   * @return a random byte
   */
  public static byte randomByte() {
    return (byte) ((int) (Math.random() * 255) - 128);
  }

  /**
   * @return a random int
   */
  public static int randomInt() {
    return random.nextInt();
  }

  /**
   * @return a random port
   */
  public static int randomPortInt() {
    return random.nextInt(65536);
  }

  /**
   * @return a random port > 1024
   */
  public static int randomHighPortInt() {
    return random.nextInt(65536 - 1024) + 1024;
  }

  /**
   * @return a random positive int
   */
  public static int randomPositiveInt() {
    while (true) {
      int rand = random.nextInt();
      if (rand > 0) {
        return rand;
      }
    }
  }

  /**
   * @return a random positive long
   */
  public static long randomPositiveLong() {
    while (true) {
      long rand = random.nextLong();
      if (rand > 0) {
        return rand;
      }
    }
  }

  /**
   * @return a random long
   */
  public static long randomLong() {
    return random.nextLong();
  }

  /**
   * @return a random boolean
   */
  public static boolean randomBoolean() {
    return random.nextBoolean();
  }

  /**
   * @return a random char
   */
  public static char randomChar() {
    return (char) (random.nextInt(16));
  }

  /**
   * @return a random short
   */
  public static short randomShort() {
    return (short) (random.nextInt(1 << 15));
  }

  /**
   * @return a random random float
   */
  public static float randomFloat() {
    return random.nextFloat();
  }

  /**
   * @return a random random double
   */
  public static double randomDouble() {
    return random.nextDouble();
  }

  /**
   * Creates a String containing random unicode characters
   *
   * @param length The length of the string to create
   * @return a String of random unicode characters
   */
  public static String randomUnicodeString(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      char c;
      do {
        c = (char) (0xFFFF * Math.random());
      } while ((c >= 0xFFFE && c <= 0xFFFF) || (c >= 0xD800 && c <= 0xDFFF)); //Illegal chars
      builder.append(c);
    }
    return builder.toString();
  }

  /**
   * Creates a random string of ascii alpha characters
   *
   * @param length the length of the string to create
   * @return a String of random ascii alpha characters
   */
  public static String randomAlphaString(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      char c = (char) (65 + 25 * Math.random());
      builder.append(c);
    }
    return builder.toString();
  }

  /**
   * Create random {@link Http2Settings} with valid values.
   *
   * @return the random settings
   */
  public static Http2Settings randomHttp2Settings() {
    long headerTableSize = 10 + randomPositiveInt() % (Http2CodecUtil.MAX_HEADER_TABLE_SIZE - 10);
    boolean enablePush = randomBoolean();
    long maxConcurrentStreams = 10 + randomPositiveLong() % (Http2CodecUtil.MAX_CONCURRENT_STREAMS - 10);
    int initialWindowSize = 10 + randomPositiveInt() % (Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE - 10);
    int maxFrameSize = Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND + randomPositiveInt() % (Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND - Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND);
    long maxHeaderListSize = 10 + randomPositiveLong() % (Http2CodecUtil.MAX_HEADER_LIST_SIZE - 10);
    Http2Settings settings = new Http2Settings();
    settings.setHeaderTableSize(headerTableSize);
    settings.setPushEnabled(enablePush);
    settings.setMaxConcurrentStreams(maxConcurrentStreams);
    settings.setInitialWindowSize(initialWindowSize);
    settings.setMaxFrameSize(maxFrameSize);
    settings.setMaxHeaderListSize(maxHeaderListSize);
    settings.set('\u0007', (randomPositiveLong() & 0xFFFFFFFFL));
    return settings;
  }

  public static <E extends Enum<E>> Set<E> randomEnumSet(Class<E> enumType) {
    EnumSet<E> set = EnumSet.noneOf(enumType);
    for (E e : EnumSet.allOf(enumType)) {
      if (randomPositiveInt() % 2 == 1) {
        set.add(e);
      }
    }
    return set;
  }

  public static <E> E randomElement(E[] array) {
    return array[randomPositiveInt() % array.length];
  }

  /**
   * Determine if two byte arrays are equal
   *
   * @param b1 The first byte array to compare
   * @param b2 The second byte array to compare
   * @return true if the byte arrays are equal
   */
  public static boolean byteArraysEqual(byte[] b1, byte[] b2) {
    if (b1.length != b2.length) return false;
    for (int i = 0; i < b1.length; i++) {
      if (b1[i] != b2[i]) return false;
    }
    return true;
  }

  /**
   * Asserts that an IllegalArgumentException is thrown by the code block.
   *
   * @param runnable code block to execute
   */
  public static void assertIllegalArgumentException(Runnable runnable) {
    try {
      runnable.run();
      fail("Should throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  /**
   * Asserts that a NullPointerException is thrown by the code block.
   *
   * @param runnable code block to execute
   */
  public static void assertNullPointerException(Runnable runnable) {
    try {
      runnable.run();
      fail("Should throw NullPointerException");
    } catch (NullPointerException e) {
      // OK
    }
  }

  /**
   * Asserts that an IllegalStateException is thrown by the code block.
   *
   * @param runnable code block to execute
   */
  public static void assertIllegalStateException(Runnable runnable) {
    try {
      runnable.run();
      fail("Should throw IllegalStateException");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  /**
   * Asserts that an IndexOutOfBoundsException is thrown by the code block.
   *
   * @param runnable code block to execute
   */
  public static void assertIndexOutOfBoundsException(Runnable runnable) {
    try {
      runnable.run();
      fail("Should throw IndexOutOfBoundsException");
    } catch (IndexOutOfBoundsException e) {
      // OK
    }
  }

  /**
   * @param source
   * @return gzipped data
   * @throws Exception
   */
  public static byte[] compressGzip(String source) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GZIPOutputStream gos = new GZIPOutputStream(baos);
    gos.write(source.getBytes());
    gos.close();
    return baos.toByteArray();
  }

  public static KeyCertOptions randomKeyCertOptions() {
    KeyCertOptions keyCertOptions;
    switch (TestUtils.randomPositiveInt() % 3) {
      case 0:
        keyCertOptions = new JksOptions();
        String jksPassword = TestUtils.randomAlphaString(100);
        ((JksOptions) keyCertOptions).setPassword(jksPassword);
        break;
      case 1:
        keyCertOptions = new PemKeyCertOptions();
        Buffer keyValue = TestUtils.randomBuffer(100);
        ((PemKeyCertOptions) keyCertOptions).setKeyValue(keyValue);
        break;
      default:
        keyCertOptions = new PfxOptions();
        String pfxPassword = TestUtils.randomAlphaString(100);
        ((PfxOptions) keyCertOptions).setPassword(pfxPassword);
    }
    return keyCertOptions;
  }

  public static TrustOptions randomTrustOptions() {
    TrustOptions trustOptions;
    switch (TestUtils.randomPositiveInt() % 3) {
      case 0:
        trustOptions = new JksOptions();
        String tsPassword = TestUtils.randomAlphaString(100);
        ((JksOptions) trustOptions).setPassword(tsPassword);
        break;
      case 1:
        trustOptions = new PemTrustOptions();
        Buffer keyValue = TestUtils.randomBuffer(100);
        ((PemTrustOptions) trustOptions).addCertValue(keyValue);
        break;
      default:
        trustOptions = new PfxOptions();
        String pfxPassword = TestUtils.randomAlphaString(100);
        ((PfxOptions) trustOptions).setPassword(pfxPassword);
    }
    return trustOptions;
  }

  public static Buffer leftPad(int padding, Buffer buffer) {
    return Buffer.buffer(Unpooled.buffer()
      .writerIndex(padding)
      .readerIndex(padding)
      .writeBytes(buffer.getByteBuf())
    );
  }

  public static String cnOf(X509Certificate cert) throws Exception {
    String dn = cert.getSubjectDN().getName();
    LdapName ldapDN = new LdapName(dn);
    for (Rdn rdn : ldapDN.getRdns()) {
      if (rdn.getType().equalsIgnoreCase("cn")) {
        return rdn.getValue().toString();
      }
    }
    return null;
  }

  /**
   * @return the loopback address for testing
   */
  public static String loopbackAddress() {
    return NetUtil.LOCALHOST4.getHostAddress();
  }

  /**
   * Create a temp file that does not exists.
   */
  public static File tmpFile(String prefix, String suffix) throws Exception {
    File tmp = Files.createTempFile(prefix, suffix).toFile();
    assertTrue(tmp.delete());
    return tmp;
  }

  public static TestLoggerFactory testLogging(Runnable runnable) {
    InternalLoggerFactory prev = InternalLoggerFactory.getDefaultFactory();
    TestLoggerFactory factory = new TestLoggerFactory();
    InternalLoggerFactory.setDefaultFactory(factory);
    try {
      runnable.run();
    } finally {
      InternalLoggerFactory.setDefaultFactory(prev);
    }
    return factory;
  }

}
