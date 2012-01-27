package org.vertx.java.core.eventbus;

import org.jboss.netty.util.CharsetUtil;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class StringMessage extends Message<String> {

  private static final Logger log = Logger.getLogger(StringMessage.class);

  StringMessage(String address, String payload) {
    super(address, payload);
  }

  public StringMessage(Buffer readBuff) {
    super(readBuff);
  }

  protected String readBody(int pos, Buffer readBuff) {
    int strLength = readBuff.getInt(pos);
    pos += 4;
    byte[] bytes = readBuff.getBytes(pos, pos + strLength);
    return new String(bytes, CharsetUtil.UTF_8);
  }

  protected void writeBody(Buffer buff) {
    writeString(buff, body);
  }

  protected int getBodyLength() {
    // Just give it enough space - doesn't have to be exact
    return 4 + 2 * body.length();
  }

  protected Message copy() {
    // No need to copy since everything is immutable
    return this;
  }

  protected byte type() {
    return TYPE_STRING;
  }

}
