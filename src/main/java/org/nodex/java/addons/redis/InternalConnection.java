/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.addons.redis;

import org.nodex.java.core.ConnectionPool;
import org.nodex.java.core.DeferredAction;
import org.nodex.java.core.Handler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.internal.NodexInternal;
import org.nodex.java.core.net.NetSocket;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class InternalConnection implements Handler<RedisReply>{

  private final LinkedList<ReplyHandler> deferredQueue = new LinkedList<>();
  private final NetSocket socket;
  private final ConnectionPool<InternalConnection> pool;
  private boolean closed;
  private TxReplyHandler currentTXSendingHandler;
  private boolean subscriber;
  private ReplyHandler currentReplyHandler;
  Handler<Buffer> subscriberHandler;

  InternalConnection(ConnectionPool<InternalConnection> pool, NetSocket socket) {
    this.pool = pool;
    this.socket = socket;
    socket.dataHandler(new ReplyParser(this));
  }

  void close(DeferredAction<Void> deferred) {
    if (subscriber) {
      deferred.setException(new RedisException("Please unsubscribe from all channels before closing connection"));
    } else if (currentTXSendingHandler != null) {
      deferred.setException(new RedisException("Please complete the transaction before closing connection"));
    } else {
      System.out.println("Closing conneciton");
      closed = true;
      pool.returnConnection(InternalConnection.this);
      deferred.setResult(null);
    }
  }

  void sendRequest(final RedisConnection.RedisDeferred<?> deferred, Buffer buffer, long contextID) {
    sendRequest(deferred, buffer, false, contextID);
  }

  void sendRequest(final RedisConnection.RedisDeferred<?> deferred, final Buffer buffer, boolean subscribe, long contextID) {
    if (!closed) {
      if (subscriber && !subscribe) {
        deferred.setException(new RedisException("It is not legal to send commands other than SUBSCRIBE and UNSUBSCRIBE when in subscribe mode"));
      } else {
        switch (deferred.commandType) {
          case MULTI: {
            if (currentTXSendingHandler != null) {
              throw new IllegalStateException("Already in tx");
            }
            deferredQueue.add(deferred);
            currentTXSendingHandler = new TxReplyHandler(contextID);
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

        System.out.println("Writing request: " + buffer);
        socket.write(buffer);
      }
    }
  }

  public void handle(final RedisReply reply) {

    System.out.println("Handling r reply " + reply);

    if (currentReplyHandler != null) {
      currentReplyHandler.handleReply(reply);
    } else {
      ReplyHandler handler = deferredQueue.poll();
      if (handler == null) {
        System.err.println("Unsolicited response");
      } else {
        handler.handleReply(reply);
      }
    }
  }

  void subscribe(long contextID) {
    if (!this.subscriber) {
      subscriber = true;
      this.currentReplyHandler = new SubscriberHandler(contextID);
    }
  }

  void unsubscribe() {
    if (subscriber) {
      subscriber = false;
      this.currentReplyHandler = null;
    }
  }


  abstract class BaseReplyHandler implements Runnable, ReplyHandler {
    final long contextID;

    BaseReplyHandler(long contextID) {
      this.contextID = contextID;
    }

    public abstract void run();

    RedisReply reply;

    public void handleReply(RedisReply reply) {
      this.reply = reply;
      NodexInternal.instance.executeOnContext(contextID, this);
    }
  }

  class SubscriberHandler extends BaseReplyHandler {

    SubscriberHandler(long contextID) {
      super(contextID);
    }

    public void run() {
      switch (reply.type) {
        case INTEGER: {
          // unsubscribe or subscribe
          ReplyHandler handler = deferredQueue.poll();
          if (handler == null) {
            System.err.println("Protocol error");
          } else {
            handler.handleReply(reply);
          }
          break;
        } case MULTI_BULK: {
          // A message
          if (subscriberHandler != null) {
            subscriberHandler.handle(reply.multiBulkResult[2]);
          }
        }
      }
    }
  }

  class TxReplyHandler extends BaseReplyHandler {

    final Queue<RedisConnection.RedisDeferred<?>> deferreds = new LinkedList<>();
    RedisConnection.RedisDeferred<?> endDeferred; // The Deferred corresponding to the EXEC/DISCARD
    boolean discarded;

    TxReplyHandler(long contextID) {
      super(contextID);
    }

    public void run() {

      currentReplyHandler = this;

      System.out.println("Handling tx reply: " + reply);

      if (reply.type == RedisReply.Type.ONE_LINE) {
        if (reply.line.equals("QUEUED")) {
          return;
        }
      }
      if (discarded) {
        for (RedisConnection.RedisDeferred<?> deferred: deferreds) {
          deferred.setException(new RedisException("Transaction discarded"));
        }
        sendEnd();
      } else {
        RedisConnection.RedisDeferred<?> deferred = deferreds.poll();
        if (deferred != null) {
          //We are already on correct context so we can run it directly
          deferred.handleReplyDirect(reply);
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
