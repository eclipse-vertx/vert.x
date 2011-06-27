package org.nodex.core.stomp;

import org.nodex.core.buffer.Buffer;

import java.io.UnsupportedEncodingException;
import java.util.Map;

public class Frame {
  public final String command;
  public final Map<String, String> headers;
  public final Buffer body;

  protected Frame(String command, Map<String, String> headers, Buffer body) {
    this.command = command;
    this.headers = headers;
    this.body = body;
  }

  public Buffer toBuffer() {
    StringBuilder sb = new StringBuilder();
    sb.append(command).append('\n');
    for (Map.Entry<String, String> entry: headers.entrySet()) {
      sb.append(entry.getKey()).append(':').append(entry.getValue());
    }
    sb.append('\n');
    try {
      byte[] bytes = sb.toString().getBytes("UTF-8");
      Buffer buff = Buffer.newFixed(bytes.length + body.length() + 1);
      return buff.append(bytes).append(body).append((byte)0);
    } catch (UnsupportedEncodingException thisWillNeverHappen) {
      return null;
    }
  }

}
