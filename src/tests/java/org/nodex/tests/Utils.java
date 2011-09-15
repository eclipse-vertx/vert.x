/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.tests;

import org.nodex.java.core.buffer.Buffer;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Utils {

  public static Buffer generateRandomBuffer(int length) {
    return generateRandomBuffer(length, false, (byte) 0);
  }

  public static byte[] generateRandomByteArray(int length) {
    return generateRandomByteArray(length, false, (byte) 0);
  }

  public static byte[] generateRandomByteArray(int length, boolean avoid, byte avoidByte) {
    byte[] line = new byte[length];
    for (int i = 0; i < length; i++) {
      //Choose a random byte - if we're generating delimited lines then make sure we don't
      //choose first byte of delim
      byte rand;
      do {
        rand = (byte) ((int) (Math.random() * 255) - 128);
      } while (avoid && rand == avoidByte);

      line[i] = rand;
    }
    return line;
  }

  public static Buffer generateRandomBuffer(int length, boolean avoid, byte avoidByte) {
    byte[] line = generateRandomByteArray(length, avoid, avoidByte);
    return Buffer.create(line);
  }

  public static String randomAlphaString(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      char c = (char) (65 + 25 * Math.random());
      builder.append(c);
    }
    return builder.toString();
  }

  public static boolean buffersEqual(Buffer b1, Buffer b2) {
    if (b1.length() != b2.length()) return false;
    for (int i = 0; i < b1.length(); i++) {
      if (b1.getByte(i) != b2.getByte(i)) return false;
    }
    return true;
  }

  public static boolean byteArraysEqual(byte[] b1, byte[] b2) {
    if (b1.length != b2.length) return false;
    for (int i = 0; i < b1.length; i++) {
      if (b1[i] != b2[i]) return false;
    }
    return true;
  }

}
