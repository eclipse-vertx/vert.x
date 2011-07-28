package tests;

import org.nodex.core.buffer.Buffer;

/**
 * User: tim
 * Date: 12/07/11
 * Time: 14:52
 */
public class Utils {

  public static Buffer generateRandomBuffer(int length) {
    return generateRandomBuffer(length, false, (byte) 0);
  }

  public static Buffer generateRandomBuffer(int length, boolean avoid, byte avoidByte) {
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
    return Buffer.newWrapped(line);
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
      if (b1.byteAt(i) != b2.byteAt(i)) return false;
    }
    return true;
  }

}
