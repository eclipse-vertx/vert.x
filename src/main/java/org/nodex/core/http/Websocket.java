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

package org.nodex.core.http;

import org.jboss.netty.handler.codec.http.websocket.DefaultWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.nodex.core.EventHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.streams.ReadStream;
import org.nodex.core.streams.WriteStream;

public class Websocket implements ReadStream, WriteStream {

  private final AbstractConnection conn;

  private EventHandler<Buffer> dataHandler;
  private EventHandler<Void> drainHandler;
  private EventHandler<Exception> exceptionHandler;
  private EventHandler<Void> endHandler;

  Websocket(String uri, AbstractConnection conn) {
    this.uri = uri;
    this.conn = conn;
  }

  public final String uri;

  public void writeBinaryFrame(Buffer data) {
    WebSocketFrame frame = new DefaultWebSocketFrame(0x80, data._getChannelBuffer());
    conn.write(frame);
  }

  public void writeTextFrame(String str) {
    WebSocketFrame frame = new DefaultWebSocketFrame(str);
    conn.write(frame);
  }

  public void dataHandler(EventHandler<Buffer> handler) {
    this.dataHandler = handler;
  }

  public void endHandler(EventHandler<Void> handler) {
    this.endHandler = handler;
  }

  public void exceptionHandler(EventHandler<Exception> handler) {
    this.exceptionHandler = handler;
  }

  public void pause() {
    conn.pause();
  }

  public void resume() {
    conn.resume();
  }

  public void setWriteQueueMaxSize(int maxSize) {
    conn.setWriteQueueMaxSize(maxSize);
  }

  public boolean writeQueueFull() {
    return conn.writeQueueFull();
  }

  public void writeBuffer(Buffer data) {
    writeBinaryFrame(data);
  }

  public void drainHandler(EventHandler<Void> handler) {
    this.drainHandler = handler;
  }

  public void close() {
    conn.close();
  }

  void handleFrame(WebSocketFrame frame) {
    if (dataHandler != null) {
      dataHandler.onEvent(new Buffer(frame.getBinaryData()));
    }
  }

  void writable() {
    if (drainHandler != null) {
      drainHandler.onEvent(null);
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.onEvent(e);
    }
  }
}
