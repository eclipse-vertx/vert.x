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
import org.nodex.core.ExceptionHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.ReadStream;
import org.nodex.core.streams.WriteStream;

public class Websocket implements ReadStream, WriteStream {

  private final AbstractConnection conn;

  private DataHandler dataHandler;
  private Runnable drainHandler;
  private ExceptionHandler exceptionHandler;
  private Runnable endHandler;

  Websocket(String uri, AbstractConnection conn) {
    this.uri = uri;
    this.conn = conn;
  }

  public final String uri;

  public void writeBinaryFrame(Buffer data) {
    WebSocketFrame frame = new DefaultWebSocketFrame(0x80, data._toChannelBuffer());
    conn.write(frame);
  }

  public void writeTextFrame(String str) {
    WebSocketFrame frame = new DefaultWebSocketFrame(str);
    conn.write(frame);
  }

  public void dataHandler(DataHandler handler) {
    this.dataHandler = handler;
  }

  public void endHandler(Runnable handler) {
    this.endHandler = handler;
  }

  public void exceptionHandler(ExceptionHandler handler) {
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

  public void drainHandler(Runnable handler) {
    this.drainHandler = handler;
  }

  void handleFrame(WebSocketFrame frame) {
    if (dataHandler != null) {
      dataHandler.onData(Buffer.fromChannelBuffer(frame.getBinaryData()));
    }
  }

  void writable() {
    if (drainHandler != null) {
      drainHandler.run();
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.onException(e);
    }
  }
}
