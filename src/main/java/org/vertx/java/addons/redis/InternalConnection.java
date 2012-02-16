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

package org.vertx.java.addons.redis;

import org.vertx.java.core.ConnectionPool;
import org.vertx.java.core.DeferredAction;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;
import org.vertx.java.core.net.NetSocket;

import java.util.LinkedList;
import java.util.Queue;

/**
 *
 * This is the actual connection which gets pooled, RedisConnection is just a handle to an instance of InternalConnection
 *
 * [Note: This class may seem a little overcomplex for what it does - this is because it was originally
 * designed to be used safely across multiple contexts, but now that is not the case. The class could probably
 * be simplified further]
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class InternalConnection implements Handler<RedisReply>{

  private static final Logger log = LoggerFactory.getLogger(InternalConnection.class);

  private final LinkedList<ReplyHandler> deferredQueue = new LinkedList<>();
  private final NetSocket socket;
  private final ConnectionPool<InternalConnection> pool;
  private TxReplyHandler currentTXSendingHandler;
  private boolean subscriber;
  private ReplyHandler currentReplyHandler;
  Handler<Buffer> subscriberHandler;
  private boolean closed;
  private Handler<Void> closedHandler;

  InternalConnection(final ConnectionPool<InternalConnection> pool, final NetSocket socket) {
    this.pool = pool;
    this.socket = socket;
    socket.dataHandler(new ReplyParser(this));
    socket.closedHandler(new SimpleHandler() {
      public void handle() {
        socket.close();
        pool.connectionClosed();
        closed = true;
        if (closedHandler != null) {
          closedHandler.handle(null);
        }
        socket.closedHandler(null);
      }
    });
  }

  boolean isClosed() {
    return closed;
  }

  void close(DeferredAction<Void> deferred) {
    if (subscriber) {
      deferred.setException(new RedisException("Please unsubscribe from all channels before closing connection"));
    } else if (currentTXSendingHandler != null) {
      deferred.setException(new RedisException("Please complete the transaction before closing connection"));
    } else {
      closedHandler = null;
      socket.closedHandler(new SimpleHandler() {
        public void handle() {
          //The socket has died while in the pool - we just close the socket
          //If a user retrieves a connection with a closed socket, this will detected the first time they
          //try and send a command on it, at which point a new connection will be requested
          socket.close();
          closed = true;
        }
      });

      pool.returnConnection(InternalConnection.this);
      deferred.setResult(null);
    }
  }

  void closedHandler(Handler<Void> handler) {
    this.closedHandler = handler;
  }

  void sendRequest(final RedisDeferred<?> deferred, final Buffer buffer, boolean subscribe) {
    if (closed) {
      log.warn("Socket is closed");
      return;
    }
    if (subscriber && !subscribe) {
      deferred.setException(new RedisException("It is not legal to send commands other than SUBSCRIBE and UNSUBSCRIBE when in subscribe mode"));
    } else {
      switch (deferred.commandType) {
        case MULTI: {
          if (currentTXSendingHandler != null) {
            throw new IllegalStateException("Already in tx");
          }
          deferredQueue.add(deferred);
          currentTXSendingHandler = new TxReplyHandler();
          deferredQueue.add(currentTXSendingHandler);
          break;
        } case EXEC: {
          if (currentTXSendingHandler == null) {
            throw new IllegalStateException("Not in tx");
          }
          currentTXSendingHandler.endDeferred = deferred;
          currentTXSendingHandler = null;
          break;
        } case DISCARD: {
          if (currentTXSendingHandler == null) {
            throw new IllegalStateException("Not in tx");
          }
          currentTXSendingHandler.endDeferred = deferred;
          currentTXSendingHandler.discarded = true;
          currentTXSendingHandler = null;
          break;
        }
        case OTHER: {
          if (currentTXSendingHandler != null) {
            //BODY OF TX
            currentTXSendingHandler.deferreds.add(deferred);
          } else {
            //Non transacted
            deferredQueue.add(deferred);
          }
        }
      }
      socket.write(buffer);
    }
  }

  public void handle(final RedisReply reply) {
    if (currentReplyHandler != null) {
      currentReplyHandler.handleReply(reply);
    } else {
      ReplyHandler handler = deferredQueue.poll();
      if (handler == null) {
        log.warn("Unsolicited response");
      } else {
        handler.handleReply(reply);
      }
    }
  }

  void subscribe() {
    if (!subscriber) {
      subscriber = true;
      this.currentReplyHandler = new SubscriberHandler();
    }
  }

  void unsubscribe() {
    if (subscriber) {
      subscriber = false;
      this.currentReplyHandler = null;
    }
  }


  private abstract class BaseReplyHandler implements Runnable, ReplyHandler {

    public abstract void run();

    RedisReply reply;

    public void handleReply(RedisReply reply) {
      this.reply = reply;
      run();
    }

  }

  private class SubscriberHandler extends BaseReplyHandler {

    public void run() {
      switch (reply.type) {
        case INTEGER: {
          // unsubscribe or subscribe
          ReplyHandler handler = deferredQueue.poll();
          if (handler == null) {
            log.warn("Protocol error");
          } else {
            handler.handleReply(reply);
          }
          break;
        } case MULTI_BULK: {
          // A message
          String type = reply.multiBulkResult[0].toString();
          switch (type) {
            case "message": {
              deliverMessage(reply.multiBulkResult[2]);
              break;
            }
            case "pmessage": {
              deliverMessage(reply.multiBulkResult[3]);
              break;
            }
          }
        }
      }
    }

    protected void preHandle() {
    }
  }

  private void deliverMessage(Buffer msg) {
    if (subscriberHandler != null) {
      subscriberHandler.handle(msg);
    }
  }

  private class TxReplyHandler extends BaseReplyHandler {

    final Queue<RedisDeferred<?>> deferreds = new LinkedList<>();
    RedisDeferred<?> endDeferred; // The Deferred corresponding to the EXEC/DISCARD
    boolean discarded;

    public void run() {
      currentReplyHandler = this;

      if (reply.type == RedisReply.Type.ONE_LINE) {
        if (reply.line.equals("QUEUED")) {
          return;
        }
      }
      if (discarded) {
        for (RedisDeferred<?> deferred: deferreds) {
          deferred.setException(new RedisException("Transaction discarded"));
        }
        sendEnd();
      } else {
        RedisDeferred<?> deferred = deferreds.poll();
        if (deferred != null) {
          deferred.handleReply(reply);
          if (deferreds.isEmpty()) {
            sendEnd();
          }
        } else {
          sendEnd();
        }
      }
    }

    private void sendEnd() {
      if (endDeferred != null) {
        endDeferred.setResult(null);
        endDeferred = null;
        currentReplyHandler = null;
      } else {
        throw new IllegalStateException("Invalid tx response");
      }
    }
  }
}

