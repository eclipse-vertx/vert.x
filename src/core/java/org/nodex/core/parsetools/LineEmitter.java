package org.nodex.core.parsetools;

import org.nodex.core.Callback;
import org.nodex.core.buffer.Buffer;

/**
 * Created by IntelliJ IDEA.
 * User: timfox
 * Date: 25/06/2011
 * Time: 09:48
 * To change this template use File | Settings | File Templates.
 */
public class LineEmitter extends Callback<Buffer> {

  private final byte delim;
  private final Callback<Buffer> emitter;
  private Buffer buff;
  private int pos;

  public LineEmitter(byte delim, Callback<Buffer> emitter) {
    this.delim = delim;
    this.emitter = emitter;
  }

  public void onEvent(Buffer buffer) {
    if (buff == null) {
      buff = Buffer.newDynamic(buffer.length());
    }
    buff.append(buffer);
    int len = buff.length();
    System.out.println("buf len is " + len);
    for (int i = pos; i < len; i++) {
      if (buff.byteAt(i) == delim) {
        System.out.println("got delim");
        Buffer ret = buff.slice(0, i - 1);
        if (i == len -1) {
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
}
