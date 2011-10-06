/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.core.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class WebsocketHandshakeHelper {
  public static ChannelBuffer calcResponse(String key1, String key2, long c) {
    int a = (int) (Long.parseLong(key1.replaceAll("[^0-9]", "")) / key1.replaceAll("[^ ]", "").length());
    int b = (int) (Long.parseLong(key2.replaceAll("[^0-9]", "")) / key2.replaceAll("[^ ]", "").length());
    ChannelBuffer input = ChannelBuffers.buffer(16);
    input.writeInt(a);
    input.writeInt(b);
    input.writeLong(c);
    try {
      return ChannelBuffers.wrappedBuffer(MessageDigest.getInstance("MD5").digest(input.array()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No such algorithm " + e.getMessage());
    }
  }

  private static String chrs = "!\"#$%&'()*+,-./:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";

  public static String genWSkey() {
    int spaces = 1 + (int) (Math.random() * 12);
    long m = 1 + 2 * (long) Integer.MAX_VALUE;
    long max = m / spaces;
    long number = (long) (Math.random() * (max + 1));
    long prod1 = number * spaces;
    String key = String.valueOf(prod1);
    int numRandomChars = 1 + (int) (Math.random() * 12);
    StringBuilder sb = new StringBuilder(key);
    for (int i = 0; i < numRandomChars; i++) {
      int randomPos = (int) (sb.length() * Math.random());
      char randomChr = chrs.charAt((int) (chrs.length() * Math.random()));
      sb.insert(randomPos, randomChr);
    }
    for (int i = 0; i < spaces; i++) {
      int randomPos = 1 + (int) ((sb.length() - 2) * Math.random());
      sb.insert(randomPos, ' ');
    }
    return sb.toString();
  }
}
