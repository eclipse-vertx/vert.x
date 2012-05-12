/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 * Modified from original form by Tim Fox
 */

package org.vertx.java.core.http.impl.ws.hybi00;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class WebSocketChallenge00 {
  private long key1;
  private int spaces1;

  private long key2;
  private int spaces2;

  private byte[] key3;

  public WebSocketChallenge00() throws NoSuchAlgorithmException {
    generateKeys();
  }

  public long getKey1() {
    return this.key1;
  }

  public int getSpaces1() {
    return this.spaces1;
  }

  public String getKey1String() {
    return encodeKey(this.key1, this.spaces1);
  }

  public long getKey2() {
    return this.key2;
  }

  public int getSpaces2() {
    return this.spaces2;
  }

  public String getKey2String() {
    return encodeKey(this.key2, this.spaces2);
  }

  public byte[] getKey3() {
    return this.key3;
  }

  protected void generateKeys() {
    int[] key = generateKey();
    this.key1 = key[0];
    this.spaces1 = key[1];

    key = generateKey();
    this.key2 = key[0];
    this.spaces2 = key[1];

    this.key3 = generateKey3();
  }

  public static byte[] solve(String encodedKey1, String encodedKey2, byte[] key3) throws NoSuchAlgorithmException {
    return solve(decodeKey(encodedKey1), decodeKey(encodedKey2), key3);
  }

  public static byte[] solve(long key1, long key2, byte[] key3) throws NoSuchAlgorithmException {
    ChannelBuffer buffer = ChannelBuffers.buffer(ByteOrder.BIG_ENDIAN, 16);
    buffer.writeInt((int) key1);
    buffer.writeInt((int) key2);
    buffer.writeBytes(key3);

    byte[] solution = new byte[16];
    buffer.readBytes(solution);


    MessageDigest digest = MessageDigest.getInstance("MD5");
    byte[] solutionMD5 = digest.digest(solution);

    return solutionMD5;
  }

  public boolean verify(byte[] response) throws NoSuchAlgorithmException {
    byte[] challenge = solve(this.key1, this.key2, this.key3);

    if (challenge.length != response.length) {
      return false;
    }

    for (int i = 0; i < challenge.length; ++i) {
      if (challenge[i] != response[i]) {
        return false;
      }
    }

    return true;
  }

  public static String encodeKey(long baseKey, int spaces) {
    Random random = new Random();
    long product = baseKey * spaces;
    String key = "" + product;

    int additionalJunk = random.nextInt(12) + 1;

    for (int i = 0; i < additionalJunk; ++i) {
      int position = random.nextInt(key.length());
      char junkChar = (char) (random.nextInt(0x7E - 0x3A) + 0x3A);
      key = key.substring(0, position) + junkChar + key.substring(position);
    }

    for (int i = 0; i < spaces; ++i) {
      int position = random.nextInt(key.length() - 2) + 1;
      key = key.substring(0, position) + ' ' + key.substring(position);
    }

    return key;
  }

  public static long decodeKey(String encoded) {
    int numSpaces = 0;

    int len = encoded.length();

    for (int i = 0; i < len; ++i) {
      if (encoded.charAt(i) == ' ') {
        ++numSpaces;
      }
    }

    String digits = encoded.replaceAll("[^0-9]", "");

    long product = Long.parseLong(digits);

    long key = product / numSpaces;

    return key;
  }

  public static int[] generateKey() {
    Random random = new Random();

    int spaces = random.nextInt(12) + 1;
    int max = Integer.MAX_VALUE / spaces;
    int number = random.nextInt(max) + 10;

    return new int[]{number, spaces};
  }

  public static byte[] generateKey3() {
    Random random = new Random();

    byte[] key = new byte[8];

    random.nextBytes(key);

    return key;
  }
}
