package org.nodex.core.parsetools;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;

/**
 * User: tim
 * Date: 27/06/11
 * Time: 17:49
 */
public class LineSplitter extends Callback<Buffer> {

  private final byte delim;
  private final Callback<Buffer> output;
  private Buffer buff;
  private int pos;

  public LineSplitter(byte delim, Callback<Buffer> output) {
    this.delim = delim;
    this.output = output;
  }

  public LineSplitter(byte delim, Buffer buffer, Callback<Buffer> output) {
    this(delim, output);
    this.buff = buffer;
  }

  public Buffer remainingBuffer() {
    return buff;
  }

  public void reset(Buffer buffer) {
    buff = buffer;
    pos = 0;
  }

  public void onEvent(Buffer buffer) {
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
        output.onEvent(ret);
        return;
      }
    }
    pos = len;
  }
}
