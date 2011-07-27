package org.nodex.core.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * User: timfox
 * Date: 26/07/2011
 * Time: 17:03
 */

public class WebsocketHandshakeHelper {
  public static ChannelBuffer calcResponse(String key1, String key2, long c) {
    int a = (int) (Long.parseLong(key1.replaceAll("[^0-9]", "")) / key1.replaceAll("[^ ]", "").length());
    int b = (int) (Long.parseLong(key2.replaceAll("[^0-9]", "")) / key2.replaceAll("[^ ]", "").length());
    ChannelBuffer input = ChannelBuffers.buffer(16);
    input.writeInt(a);
    input.writeInt(b);
    input.writeLong(c);
    try {
      ChannelBuffer output = ChannelBuffers.wrappedBuffer(MessageDigest.getInstance("MD5").digest(input.array()));
      return output;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No such algorithm " + e.getMessage());
    }
  }

  private static String chrs = "!\"#$%&'()*+,-./:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";

  /*
  Whoever dreamt up this insane handshake when they were writing the draft-ietf-hybi-thewebsocketprotocol-00 spec
  was tripping on a LOT of acid that day
   */
  public static String genWSkey() {
    int spaces = 1 + (int) (Math.random() * 12);
    long m = 1 + 2 * (long)Integer.MAX_VALUE;
    long max = m / spaces;
    long number = (long) (Math.random() * (max + 1));
    long prod1 = number * spaces;
    String key = String.valueOf(prod1);
    int numRandomChars = 1 + (int) (Math.random() * 12);
    StringBuilder sb = new StringBuilder(key);
    for (int i = 0; i < numRandomChars; i++) {
      int randomPos = (int) (sb.length() * Math.random());
      char randomChr = chrs.charAt((int)(chrs.length() * Math.random()));
      sb.insert(randomPos, randomChr);
    }
    for (int i = 0; i < spaces; i++) {
      int randomPos = 1 + (int) ((sb.length() - 2) * Math.random());
      sb.insert(randomPos, ' ');
    }
    return sb.toString();
  }
}
