package org.nodex.core.stomp;

import org.nodex.core.buffer.Buffer;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class Frame {
  public String command;
  public final Map<String, String> headers;
  public final Buffer body;

  public Frame(String command, Map<String, String> headers, Buffer body) {
    this.command = command;
    this.headers = headers;
    this.body = body;
  }

  public Frame(String command, Buffer body) {
    this.command = command;
    this.headers = new HashMap<String, String>(4);
    this.body = body;
  }

  public static Frame connectFrame() {
    return connectFrame(null, null);
  }

  public static Frame connectFrame(String username, String password) {
    Frame frame = new Frame("CONNECT", null);
    frame.putHeader("login", username);
    frame.putHeader("passcode", password);
    return frame;
  }

  public static Frame connectedFrame(String sessionID) {
    Frame frame = new Frame("CONNECTED", null);
    frame.putHeader("session", sessionID);
    return frame;
  }

  public static Frame subscribeFrame(String destination) {
    Frame frame = new Frame("SUBSCRIBE", null);
    frame.putHeader("destination", destination);
    return frame;
  }

  public static Frame unsubscribeFrame(String destination) {
    Frame frame = new Frame("UNSUBSCRIBE", null);
    frame.putHeader("destination", destination);
    return frame;
  }

  public static Frame sendFrame(String destination, String body) {
    Buffer buff = Buffer.fromString(body, "UTF-8");
    Frame frame = new Frame("SEND", buff);
    frame.putHeader("destination", destination);
    frame.putHeader("content-length", String.valueOf(buff.length()));
    return frame;
  }

  public static Frame sendFrame(String destination, Buffer body) {
    Frame frame = new Frame("SEND", body);
    frame.putHeader("destination", destination);
    frame.putHeader("content-length", String.valueOf(body.length()));
    return frame;
  }

  public static Frame receiptFrame(String receipt) {
    Frame frame = new Frame("RECEIPT", null);
    frame.putHeader("receipt-id", receipt);
    return frame;
  }

  public void putHeader(String key, String val) {
    headers.put(key, val);
  }

  public void removeHeader(String key) {
    headers.remove(key);
  }

  public Buffer toBuffer() {
    try {
      byte[] bytes = headersString().toString().getBytes("UTF-8");
      Buffer buff = Buffer.newFixed(bytes.length + (body == null ? 0 : body.length()) + 1);
      buff.append(bytes);
      if (body != null) buff.append(body);
      buff.append((byte) 0);
      return buff;
    } catch (UnsupportedEncodingException thisWillNeverHappen) {
      return null;
    }
  }

  private StringBuilder headersString() {
    StringBuilder sb = new StringBuilder();
    sb.append(command).append('\n');
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        sb.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
      }
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
