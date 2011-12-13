package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Immutable;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Session implements SockJSSocket, Immutable {

  private static final Logger log = Logger.getLogger(Session.class);

  private final Queue<String> pendingWrites = new LinkedList<>();
  private final Queue<String> pendingReads = new LinkedList<>();
  private TransportListener listener;
  private Handler<Buffer> dataHandler;
  private boolean closed;
  private boolean openWritten;
  private final long timeout;
  private final Handler<SockJSSocket> sockHandler;
  private final Handler<Void> timeoutHandler;
  private long heartbeatID = -1;
  private long timeoutTimerID = -1;
  private boolean paused;
  private int maxQueueSize = 64 * 1024; // Message queue size is measured in *characters* (not bytes)
  private int messagesSize;
  private Handler<Void> drainHandler;
  private Handler<Void> endHandler;


  Session(long heartbeatPeriod, Handler<SockJSSocket> sockHandler) {
    this(-1, heartbeatPeriod, sockHandler, null);
  }

  Session(long timeout, long heartbeatPeriod, Handler<SockJSSocket> sockHandler,  Handler<Void> timeoutHandler) {
    this.timeout = timeout;
    this.sockHandler = sockHandler;
    this.timeoutHandler = timeoutHandler;

    // Start a heartbeat

    heartbeatID = Vertx.instance.setPeriodic(heartbeatPeriod, new Handler<Long>() {
      public void handle(Long id) {
        if (listener != null) {
          listener.sendFrame("h");
        }
      }
    });
  }

  public void write(Buffer buffer) {
    String msgStr = buffer.toString();
    pendingWrites.add(msgStr);
    this.messagesSize += msgStr.length();
    if (listener != null) {
      writePendingMessages();
    }
  }

  public void dataHandler(Handler<Buffer> handler) {
    this.dataHandler = handler;
  }

  public void pause() {
    paused = true;
  }

  public void resume() {
    paused = false;

    if (dataHandler != null) {
      for (String msg: this.pendingReads) {
        dataHandler.handle(Buffer.create(msg));
      }
    }
  }

  public void writeBuffer(Buffer data) {
    write(data);
  }

  public void setWriteQueueMaxSize(int maxQueueSize) {
    if (maxQueueSize < 1) {
      throw new IllegalArgumentException("maxQueueSize must be >= 1");
    }
    this.maxQueueSize = maxQueueSize;
  }

  public boolean writeQueueFull() {
    return messagesSize >= maxQueueSize;
  }

  public void drainHandler(Handler<Void> handler) {
    this.drainHandler = handler;
  }

  public void exceptionHandler(Handler<Exception> handler) {
  }

  public void endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
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
      timeoutTimerID = Vertx.instance.setTimer(timeout, new Handler<Long>() {
        public void handle(Long id) {
          Vertx.instance.cancelTimer(heartbeatID);
          if (listener == null) {
            timeoutHandler.handle(null);
          }
          if (endHandler != null) {
            endHandler.handle(null);
          }
        }
      });
    }
  }

  void writePendingMessages() {

    StringBuilder sb = new StringBuilder("a[");
    int count = 0;
    int size = pendingWrites.size();
    for (String msg : pendingWrites) {
      messagesSize -= msg.length();
      sb.append('"').append(msg).append('"');
      if (++count != size) {
        sb.append(',');
      }
    }
    sb.append("]");
    listener.sendFrame(sb.toString());

    pendingWrites.clear();

    if (drainHandler != null && messagesSize <= maxQueueSize / 2) {
      Handler<Void> dh = drainHandler;
      drainHandler = null;
      dh.handle(null);
    }
  }

  void handleMessages(String[] messages) {
    if (dataHandler != null) {
      for (String msg : messages) {
        if (!paused) {
          dataHandler.handle(Buffer.create(msg));
        } else {
          pendingReads.add(msg);
        }
      }
    }
  }

  void writeClosed(TransportListener lst) {
    writeClosed(lst, 3000, "Go away!");
  }

  void writeClosed(TransportListener lst, int code, String msg) {

    StringBuilder sb = new StringBuilder("c[");
    sb.append(String.valueOf(code)).append(",\"");
    sb.append(msg).append("\"]");

    lst.sendFrame(sb.toString());
  }

  void writeOpen(TransportListener lst) {
    StringBuilder sb = new StringBuilder("o");
    lst.sendFrame(sb.toString());
    openWritten = true;
  }

  void register(final TransportListener lst) {

    if (this.listener != null) {
      writeClosed(lst, 2010, "Another connection still open");
    } else {

      this.listener = lst;

      if (timeoutTimerID != -1) {
        Vertx.instance.cancelTimer(timeoutTimerID);
        timeoutTimerID = -1;
      }

      if (!openWritten) {
        writeOpen(lst);
        sockHandler.handle(this);
      }

      if (listener != null) {
        if (closed) {
          // Could have already been closed by the user
          writeClosed(lst);
          resetListener();
        } else {

          if (!pendingWrites.isEmpty()) {
            writePendingMessages();
          }
        }
      }
    }
  }

}
