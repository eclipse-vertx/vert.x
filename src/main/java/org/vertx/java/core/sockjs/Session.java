package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Immutable;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Session implements SockJSSocket, Immutable {

  private static final Logger log = Logger.getLogger(Session.class);

  private final Queue<String> messages = new LinkedList<>();
  private TransportListener listener;
  private Handler<Buffer> dataHandler;
  private boolean closed;
  private boolean openWritten;
  private final long timeout;
  private final long heartbeatPeriod;
  private final Handler<SockJSSocket> sockHandler;
  private final Handler<Void> timeoutHandler;
  private long heartbeatID = -1;

  Session(long heartbeatPeriod, Handler<SockJSSocket> sockHandler) {
    this(-1, heartbeatPeriod, sockHandler, null);
  }

  Session(long timeout, long heartbeatPeriod, Handler<SockJSSocket> sockHandler,  Handler<Void> timeoutHandler) {
    this.timeout = timeout;
    this.heartbeatPeriod = heartbeatPeriod;
    this.sockHandler = sockHandler;
    this.timeoutHandler = timeoutHandler;
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

    if (timeout != -1) {
      Vertx.instance.setTimer(timeout, new Handler<Long>() {
        public void handle(Long id) {
          if (listener == null) {
            timeoutHandler.handle(null);
          }
        }
      });
    }

    if (heartbeatID != -1) {
      Vertx.instance.cancelTimer(heartbeatID);
    }
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
    listener.sendFrame(sb.toString());

    messages.clear();
  }

  void handleMessages(String[] messages) {
    if (dataHandler != null) {
      for (String msg : messages) {
        dataHandler.handle(Buffer.create(msg));
      }
    }
  }

  void writeClosed(TransportListener lst) {
    writeClosed(lst, 3000, "Go away!");
  }

  void writeClosed(TransportListener lst, int code, String msg) {

    StringBuffer sb = new StringBuffer("c[");
    sb.append(String.valueOf(code)).append(",\"");
    sb.append(msg).append("\"]");

    lst.sendFrame(sb.toString());
  }

  void writeOpen(TransportListener lst) {
    StringBuffer sb = new StringBuffer("o");
    lst.sendFrame(sb.toString());
    openWritten = true;
  }

  void register(final TransportListener lst) {

    if (this.listener != null) {
      writeClosed(lst, 2010, "Another connection still open");
    } else {

      this.listener = lst;

      if (!openWritten) {
        writeOpen(lst);
        sockHandler.handle(this);
      }

      if (closed) {
        // Could have already been closed by the user
        writeClosed(lst);
        this.listener = null;
      } else {

        if (!messages.isEmpty()) {
          writePendingMessagesToPollResponse();
        }

        // Start a heartbeat

        heartbeatID = Vertx.instance.setPeriodic(heartbeatPeriod, new Handler<Long>() {
          public void handle(Long id) {
            lst.sendFrame("h");
          }
        });
      }
    }
  }

}
