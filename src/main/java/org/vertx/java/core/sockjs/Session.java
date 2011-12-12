package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Immutable;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Session implements SockJSSocket, Immutable {

  private static final Logger log = Logger.getLogger(Session.class);

  private final Queue<String> messages = new LinkedList<>();
  private TransportListener listener;
  private Handler<Buffer> dataHandler;
  private boolean closed;
  private boolean openWritten;
  private final Handler<SockJSSocket> sockHandler;

  public Session(Handler<SockJSSocket> sockHandler) {
    this.sockHandler = sockHandler;
  }

  public void write(Buffer buffer) {
    messages.add(buffer.toString());
    if (listener != null) {
      writePendingMessagesToPollResponse();
    }
  }

  public void dataHandler(Handler<Buffer> handler) {
    this.dataHandler = handler;
  }

  public void close() {
    closed = true;
  }

  boolean isClosed() {
    return closed;
  }

  void resetListener() {
    listener = null;
  }

  void writePendingMessagesToPollResponse() {

    StringBuffer sb = new StringBuffer("a[");
    int count = 0;
    int size = messages.size();
    for (String msg : messages) {
      sb.append('"').append(msg).append('"');
      if (++count != size) {
        sb.append(',');
      }
    }
    sb.append("]");
    listener.sendFrame(sb);

    messages.clear();
  }

  void handleMessages(String[] messages) {
    if (dataHandler != null) {
      for (String msg : messages) {
        dataHandler.handle(Buffer.create(msg));
      }
    }
  }

  void writeClosed(TransportListener list) {
    writeClosed(list, 3000, "Go away!");
  }

  void writeClosed(TransportListener list, int code, String msg) {

    StringBuffer sb = new StringBuffer("c[");
    sb.append(String.valueOf(code)).append(",\"");
    sb.append(msg).append("\"]");

    list.sendFrame(sb);
  }

  void writeOpen(TransportListener list) {
    StringBuffer sb = new StringBuffer("o");
    list.sendFrame(sb);
    openWritten = true;
  }

  void register(TransportListener list) {

    if (this.listener != null) {
      writeClosed(list, 2010, "Another connection still open");
    } else {

      this.listener = list;

      if (!openWritten) {
        writeOpen(list);
        sockHandler.handle(this);
      }

      if (closed) {
        // Could have already been closed by the user
        writeClosed(list);
        this.listener = null;
      } else {

        if (!messages.isEmpty()) {
          writePendingMessagesToPollResponse();
        }
      }
    }
  }

}
