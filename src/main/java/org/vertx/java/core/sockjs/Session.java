package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Session implements SockJSSocket {

  final Queue<String> messages = new LinkedList<>();
  TransportConnection tcConn;
  Handler<Buffer> dataHandler;
  boolean closed;

  public void write(Buffer buffer) {
    messages.add(buffer.toString());
    if (tcConn != null) {
      writePendingMessagesToPollResponse();
    }
  }

  public void dataHandler(Handler<Buffer> handler) {
    this.dataHandler = handler;
  }

  public void close() {
    closed = true;
  }

  void writePendingMessagesToPollResponse() {
    tcConn.write(this);
    messages.clear();
  }

  void handleMessages(String[] messages) {
    if (dataHandler != null) {
      for (String msg : messages) {
        dataHandler.handle(Buffer.create(msg));
      }
    }
  }

}
