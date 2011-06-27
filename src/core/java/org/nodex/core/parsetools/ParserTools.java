package org.nodex.core.parsetools;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;

/**
 * User: tim
 * Date: 27/06/11
 * Time: 14:53.
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
  public static Callback<Buffer> splitOnDelimiter(final byte delim, final Callback<Buffer> output) {
    return new LineSplitter(delim, output);
  }
}
