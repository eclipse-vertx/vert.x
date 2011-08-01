/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core.stomp;

import org.nodex.core.buffer.Buffer;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

class Frame {
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
    this.headers = new HashMap<>(4);
    this.body = body;
  }

  protected static Frame connectFrame() {
    return connectFrame(null, null);
  }

  protected static Frame connectFrame(String username, String password) {
    Frame frame = new Frame("CONNECT", null);
    frame.headers.put("login", username);
    frame.headers.put("passcode", password);
    return frame;
  }

  protected static Frame connectedFrame(String sessionID) {
    Frame frame = new Frame("CONNECTED", null);
    frame.headers.put("session", sessionID);
    return frame;
  }

  protected static Frame subscribeFrame(String destination) {
    Frame frame = new Frame("SUBSCRIBE", null);
    frame.headers.put("destination", destination);
    return frame;
  }

  protected static Frame unsubscribeFrame(String destination) {
    Frame frame = new Frame("UNSUBSCRIBE", null);
    frame.headers.put("destination", destination);
    return frame;
  }

  protected static Frame sendFrame(String destination, String body) {
    Buffer buff = Buffer.fromString(body, "UTF-8");
    Frame frame = new Frame("SEND", buff);
    frame.headers.put("destination", destination);
    frame.headers.put("content-length", String.valueOf(buff.length()));
    return frame;
  }

  protected static Frame sendFrame(String destination, Buffer body) {
    Frame frame = new Frame("SEND", body);
    frame.headers.put("destination", destination);
    frame.headers.put("content-length", String.valueOf(body.length()));
    return frame;
  }

  protected static Frame receiptFrame(String receipt) {
    Frame frame = new Frame("RECEIPT", null);
    frame.headers.put("receipt-id", receipt);
    return frame;
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
