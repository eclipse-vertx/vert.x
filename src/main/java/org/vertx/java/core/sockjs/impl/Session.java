/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.sockjs.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Session extends SockJSSocket {

  private static final Logger log = LoggerFactory.getLogger(Session.class);

  private final Queue<String> pendingWrites = new LinkedList<>();
  private final Queue<String> pendingReads = new LinkedList<>();
  private TransportListener listener;
  private Handler<Buffer> dataHandler;
  private boolean closed;
  private boolean openWritten;
  private final String id;
  private final long timeout;
  private final Handler<SockJSSocket> sockHandler;
  private Handler<Void> timeoutHandler;
  private long heartbeatID = -1;
  private long timeoutTimerID = -1;
  private boolean paused;
  private int maxQueueSize = 64 * 1024; // Message queue size is measured in *characters* (not bytes)
  private int messagesSize;
  private Handler<Void> drainHandler;
  private Handler<Void> endHandler;

  Session(long heartbeatPeriod, Handler<SockJSSocket> sockHandler) {
    this(null, -1, heartbeatPeriod, sockHandler);
  }

  Session(String id, long timeout, long heartbeatPeriod, Handler<SockJSSocket> sockHandler) {
    this.id = id;
    this.timeout = timeout;
    this.sockHandler = sockHandler;

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

  public void shutdown() {
    if (heartbeatID != -1) {
      Vertx.instance.cancelTimer(heartbeatID);
    }
    if (timeoutTimerID != -1) {
      Vertx.instance.cancelTimer(timeoutTimerID);
    }
  }

  public void close() {
    closed = true;
  }

  public String getID() {
    return id;
  }

  boolean isClosed() {
    return closed;
  }

  void setTimeoutHandler(Handler<Void> timeoutHandler) {
    this.timeoutHandler = timeoutHandler;
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
          if (listener != null) {
            listener.close();
          }
        }
      });
    }
  }

  void writePendingMessages() {
    String json = JsonCodec.encode(pendingWrites.toArray());
    listener.sendFrame("a" + json);
    pendingWrites.clear();
    if (drainHandler != null && messagesSize <= maxQueueSize / 2) {
      Handler<Void> dh = drainHandler;
      drainHandler = null;
      dh.handle(null);
    }
  }

  void register(final TransportListener lst) {

    if (closed) {
      // Closed by the application
      writeClosed(lst);
      // And close the listener request
      lst.close();
    } else if (this.listener != null) {
      writeClosed(lst, 2010, "Another connection still open");
      // And close the listener request
      lst.close();
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
          lst.close();
        } else {
          if (!pendingWrites.isEmpty()) {
            writePendingMessages();
          }
        }
      }
    }
  }


  private String[] parseMessageString(String msgs) {
    try {
      String[] parts;
      if (msgs.startsWith("[")) {
        //JSON array
        parts = (String[])JsonCodec.decodeValue(msgs, String[].class);
      } else {
        //JSON string
        String str = (String)JsonCodec.decodeValue(msgs, String.class);
        parts = new String[] { str };
      }
      return parts;
    } catch (DecodeException e) {
      return null;
    }
  }

  boolean handleMessages(String messages) {

    String[] msgArr = parseMessageString(messages);

    if (msgArr == null) {
      return false;
    } else {
      if (dataHandler != null) {
        for (String msg : msgArr) {
          if (!paused) {
            dataHandler.handle(Buffer.create(msg));
          } else {
            pendingReads.add(msg);
          }
        }
      }
      return true;
    }
  }

  private void writeClosed(TransportListener lst) {
    writeClosed(lst, 3000, "Go away!");
  }

  private void writeClosed(TransportListener lst, int code, String msg) {

    StringBuilder sb = new StringBuilder("c[");
    sb.append(String.valueOf(code)).append(",\"");
    sb.append(msg).append("\"]");

    lst.sendFrame(sb.toString());
  }

  private void writeOpen(TransportListener lst) {
    StringBuilder sb = new StringBuilder("o");
    lst.sendFrame(sb.toString());
    openWritten = true;
  }

}
