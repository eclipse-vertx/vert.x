package org.nodex.core.http;

import org.jboss.netty.handler.codec.http.websocket.DefaultWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.ReadStream;
import org.nodex.core.streams.WriteStream;

/**
 * User: timfox
 * Date: 26/07/2011
 * Time: 09:27
 */
public class Websocket implements ReadStream, WriteStream {

  private final AbstractConnection conn;

  private DataHandler dataHandler;
  private Runnable drainHandler;

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

  public void data(DataHandler handler) {
    this.dataHandler = handler;
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

  public void drain(Runnable handler) {
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
}
