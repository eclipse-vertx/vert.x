package org.nodex.core.stomp;

import org.nodex.core.buffer.Buffer;

import java.io.UnsupportedEncodingException;
import java.util.Map;

public class Frame {
  public final String command;
  public final Map<String, String> headers;
  public final Buffer body;

  public Frame(String command, Map<String, String> headers, Buffer body) {
    this.command = command;
    this.headers = headers;
    this.body = body;
  }

  public Buffer toBuffer() {
    try {
      byte[] bytes = headersString().toString().getBytes("UTF-8");
      Buffer buff = Buffer.newFixed(bytes.length + (body == null ? 0 : body.length()) + 1);
      buff.append(bytes);
      if (body != null) buff.append(body);
      buff.append((byte)0);
      return buff;
    } catch (UnsupportedEncodingException thisWillNeverHappen) {
      return null;
    }
  }
  private StringBuilder headersString() {
    StringBuilder sb = new StringBuilder();
    sb.append(command).append('\n');
    for (Map.Entry<String, String> entry: headers.entrySet()) {
      sb.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
    }
    sb.append('\n');
    return sb;
  }

  public String toString() {
    StringBuilder buff = headersString();
    if (body != null) {
      buff.append(body.toString());
    }
    return buff.toString();
  }

}
