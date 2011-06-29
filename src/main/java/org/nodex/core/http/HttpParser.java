package org.nodex.core.http;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;

/**
 * User: tim
 * Date: 27/06/11
 * Time: 15:57
 */
public class HttpParser {
  public static Callback<Buffer> parseHttp(final HttpCallback emitter) {
    return new Callback<Buffer>() {
      public void onEvent(Buffer buffer) {
      }
    };
  }
}
