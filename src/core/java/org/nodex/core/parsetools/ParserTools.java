package org.nodex.core.parsetools;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;

/**
 * Created by IntelliJ IDEA.
 * User: tim
 * Date: 27/06/11
 * Time: 14:53
 * To change this template use File | Settings | File Templates.
 */
public class ParserTools {

  //FIXME fix all this to work properly with multiple delimiters
  public static Callback<Buffer> splitOnDelimiter(final String delims, final Callback<Buffer> emitter) {
    return splitOnDelimiter(delims.charAt(0), emitter);
  }

  public static Callback<Buffer> splitOnDelimiter(final char delim, final Callback<Buffer> emitter) {
    return splitOnDelimiter((byte) delim, emitter);
  }

  //FIXME - this assumes single byte chars currently - update to use proper encoding
  public static Callback<Buffer> splitOnDelimiter(final byte delim, final Callback<Buffer> emitter) {
    System.out.println("calling split on delim " + delim);
    return new Callback<Buffer>() {
      private Buffer buff;
      private int pos;
      public void onEvent(Buffer buffer) {
        System.out.println("got a buffer " + buffer);
        if (buff == null) {
          buff = Buffer.newDynamic(buffer.length());
        }
        buff.append(buffer);
        int len = buff.length();
        for (int i = pos; i < len; i++) {
          if (buff.byteAt(i) == delim) {
            Buffer ret = buff.slice(0, i - 1);
            if (i == len - 1) {
              buff = null;
            } else {
              buff = buff.copy(i, len);
              pos = 0;
            }
            emitter.onEvent(ret);
            return;
          }
        }
        pos = len;
      }
    };
  }
}
