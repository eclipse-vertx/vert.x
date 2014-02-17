/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.core.eventbus.impl;

import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.eventbus.ReplyFailure;
import org.vertx.java.core.impl.Closeable;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.impl.management.ManagementRegistry;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.impl.ServerID;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.java.core.spi.cluster.AsyncMultiMap;
import org.vertx.java.core.spi.cluster.ChoosableIterable;
import org.vertx.java.core.spi.cluster.ClusterManager;

import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultEventBus implements EventBus {

  private static final Logger log = LoggerFactory.getLogger(DefaultEventBus.class);

  private static final Buffer PONG = new Buffer(new byte[] { (byte)1 });
  private static final long PING_INTERVAL = 20000;
  private static final long PING_REPLY_INTERVAL = 20000;
  private final VertxInternal vertx;
  private ServerID serverID;
  private NetServer server;
  private AsyncMultiMap<String, ServerID> subs;
  private long defaultReplyTimeout = -1;
  private final ConcurrentMap<ServerID, ConnectionHolder> connections = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Handlers> handlerMap = new ConcurrentHashMap<>();
  private final ClusterManager clusterMgr;
  private final AtomicLong replySequence = new AtomicLong(0);

  public DefaultEventBus(VertxInternal vertx) {
    // Just some dummy server ID
    this.vertx = vertx;
    this.serverID = new ServerID(-1, "localhost");
    this.server = null;
    this.subs = null;
    this.clusterMgr = null;
    ManagementRegistry.registerEventBus(serverID);
  }

  public DefaultEventBus(VertxInternal vertx, int port, String hostname, ClusterManager clusterManager) {
    this(vertx, port, hostname, clusterManager, null);
  }

  public DefaultEventBus(VertxInternal vertx, int port, String hostname, ClusterManager clusterManager,
                         Handler<AsyncResult<Void>> listenHandler) {
    this.vertx = vertx;
    this.clusterMgr = clusterManager;
    this.subs = clusterMgr.getAsyncMultiMap("subs");
    this.server = setServer(port, hostname, listenHandler);
    ManagementRegistry.registerEventBus(serverID);
  }

  @Override
  public EventBus send(String address, Object message, final Handler<Message> replyHandler) {
    // In order to fool the type system we need to wrap the handler - at run time the Message<String> handler
    // will happily handle non String types as types are erased at run-time
    Handler<Message<String>> wrapped = replyHandler == null ? null : new Handler<Message<String>>() {
      @Override
      public void handle(Message msg) {
        replyHandler.handle(msg);
      }
    };
    sendOrPub(createMessage(true, address, message), wrapped);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, Object message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, Object message) {
    sendOrPub(createMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, JsonObject message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new JsonObjectMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, JsonObject message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, JsonObject message) {
    sendOrPub(new JsonObjectMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, JsonArray message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new JsonArrayMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, JsonArray message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, JsonArray message) {
    sendOrPub(new JsonArrayMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, Buffer message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new BufferMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, Buffer message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, Buffer message) {
    sendOrPub(new BufferMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, byte[] message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new ByteArrayMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, byte[] message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, byte[] message) {
    sendOrPub(new ByteArrayMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, String message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new StringMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, String message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, String message) {
    sendOrPub(new StringMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, Integer message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new IntMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, Integer message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, Integer message) {
    sendOrPub(new IntMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, Long message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new LongMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, Long message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, Long message) {
    sendOrPub(new LongMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, Float message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new FloatMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, Float message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, Float message) {
    sendOrPub(new FloatMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, Double message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new DoubleMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, Double message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, Double message) {
    sendOrPub(new DoubleMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, Boolean message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new BooleanMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, Boolean message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, Boolean message) {
    sendOrPub(new BooleanMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, Short message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new ShortMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, Short message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, Short message) {
    sendOrPub(new ShortMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, Character message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new CharacterMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, Character message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, Character message) {
    sendOrPub(new CharacterMessage(true, address, message), null);
    return this;
  }

  @Override
  public <T> EventBus send(String address, Byte message, final Handler<Message<T>> replyHandler) {
    sendOrPub(new ByteMessage(true, address, message), replyHandler);
    return this;
  }

  @Override
  public <T> EventBus sendWithTimeout(String address, Byte message, long timeout, final Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPubWithTimeout(createMessage(true, address, message), replyHandler, timeout);
    return this;
  }

  @Override
  public EventBus send(String address, Byte message) {
    sendOrPub(new ByteMessage(true, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, Object message) {
    sendOrPub(createMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, JsonObject message) {
    sendOrPub(new JsonObjectMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, JsonArray message) {
    sendOrPub(new JsonArrayMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, Buffer message) {
    sendOrPub(new BufferMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, byte[] message) {
    sendOrPub(new ByteArrayMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, String message) {
    sendOrPub(new StringMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, Integer message) {
    sendOrPub(new IntMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, Long message) {
    sendOrPub(new LongMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, Float message) {
    sendOrPub(new FloatMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, Double message) {
    sendOrPub(new DoubleMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, Boolean message) {
    sendOrPub(new BooleanMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, Short message) {
    sendOrPub(new ShortMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, Character message) {
    sendOrPub(new CharacterMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus publish(String address, Byte message) {
    sendOrPub(new ByteMessage(false, address, message), null);
    return this;
  }

  @Override
  public EventBus registerHandler(String address, Handler<? extends Message> handler,
                              Handler<AsyncResult<Void>> completionHandler) {
    registerHandler(address, handler, completionHandler, false, false, -1);
    return this;
  }

  @Override
  public EventBus registerHandler(String address, Handler<? extends Message> handler) {
    registerHandler(address, handler, null);
    return this;
  }

  @Override
  public EventBus registerLocalHandler(String address, Handler<? extends Message> handler) {
    registerHandler(address, handler, null, false, true, -1);
    return this;
  }

  @Override
  public EventBus unregisterHandler(String address, Handler<? extends Message> handler,
                                    Handler<AsyncResult<Void>> completionHandler) {
    checkStarted();
    Handlers handlers = handlerMap.get(address);
    if (handlers != null) {
      synchronized (handlers) {
        int size = handlers.list.size();
        // Requires a list traversal. This is tricky to optimise since we can't use a set since
        // we need fast ordered traversal for the round robin
        for (int i = 0; i < size; i++) {
          HandlerHolder holder = handlers.list.get(i);
          if (holder.handler == handler) {
            if (holder.timeoutID != -1) {
              vertx.cancelTimer(holder.timeoutID);
            }
            handlers.list.remove(i);
            holder.removed = true;
            if (handlers.list.isEmpty()) {
              handlerMap.remove(address);
              if (subs != null && !holder.localOnly) {
                removeSub(address, serverID, completionHandler);
              } else if (completionHandler != null) {
                callCompletionHandler(completionHandler);
              }
            } else if (completionHandler != null) {
              callCompletionHandler(completionHandler);
            }
            holder.context.removeCloseHook(new HandlerEntry(address, handler));
            return this;
          }
        }
      }
    }
    return this;
  }

  @Override
  public EventBus unregisterHandler(String address, Handler<? extends Message> handler) {
    unregisterHandler(address, handler, null);
    return this;
  }

  @Override
  public void close(Handler<AsyncResult<Void>> doneHandler) {
		if (clusterMgr != null) {
			clusterMgr.leave();
		}
		if (server != null) {
			server.close(doneHandler);
		}
  }

  @Override
  public EventBus setDefaultReplyTimeout(long timeoutMs) {
    this.defaultReplyTimeout = timeoutMs;
    return this;
  }

  @Override
  public long getDefaultReplyTimeout() {
    return defaultReplyTimeout;
  }

  <T, U> void sendReply(ServerID dest, BaseMessage<U> message, Handler<Message<T>> replyHandler) {
    sendOrPub(dest, message, replyHandler, -1);
  }

  <T, U> void sendReplyWithTimeout(ServerID dest, BaseMessage<U> message, long timeout, Handler<AsyncResult<Message<T>>> replyHandler) {
    if (message.address == null) {
      sendNoHandlersFailure(replyHandler);
    } else {
      Handler<Message<T>> handler = convertHandler(replyHandler);
      sendOrPub(dest, message, handler, replyHandler, timeout);
    }
  }

  static <U> BaseMessage<U> createMessage(boolean send, String address, U message) {
    BaseMessage bm;
    if (message instanceof String) {
      bm = new StringMessage(send, address, (String)message);
    } else if (message instanceof Buffer) {
      bm = new BufferMessage(send, address, (Buffer)message);
    } else if (message instanceof JsonObject) {
      bm = new JsonObjectMessage(send, address, (JsonObject)message);
    } else if (message instanceof JsonArray) {
      bm = new JsonArrayMessage(send, address, (JsonArray)message);
    } else if (message instanceof byte[]) {
      bm = new ByteArrayMessage(send, address, (byte[])message);
    } else if (message instanceof Integer) {
      bm = new IntMessage(send, address, (Integer)message);
    } else if (message instanceof Long) {
      bm = new LongMessage(send, address, (Long)message);
    } else if (message instanceof Float) {
      bm = new FloatMessage(send, address, (Float)message);
    } else if (message instanceof Double) {
      bm = new DoubleMessage(send, address, (Double)message);
    } else if (message instanceof Boolean) {
      bm = new BooleanMessage(send, address, (Boolean)message);
    } else if (message instanceof Short) {
      bm = new ShortMessage(send, address, (Short)message);
    } else if (message instanceof Character) {
      bm = new CharacterMessage(send, address, (Character)message);
    } else if (message instanceof Byte) {
      bm = new ByteMessage(send, address, (Byte)message);
    } else if (message == null) {
      bm = new StringMessage(send, address, null);
    } else {
      throw new IllegalArgumentException("Cannot send object of class " + message.getClass() + " on the event bus: " + message);
    }
    return bm;
  }

  private NetServer setServer(int port, final String hostName, final Handler<AsyncResult<Void>> listenHandler) {
    final NetServer server = vertx.createNetServer().connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        final RecordParser parser = RecordParser.newFixed(4, null);
        Handler<Buffer> handler = new Handler<Buffer>() {
          int size = -1;
          public void handle(Buffer buff) {
            if (size == -1) {
              size = buff.getInt(0);
              parser.fixedSizeMode(size);
            } else {
              BaseMessage received = MessageFactory.read(buff);
              if (received.type() == MessageFactory.TYPE_PING) {
                // Send back a pong - a byte will do
                socket.write(PONG);
              } else {
                receiveMessage(received, -1, null, null);
              }
              parser.fixedSizeMode(4);
              size = -1;
            }
          }
        };
        parser.setOutput(handler);
        socket.dataHandler(parser);
      }

    });
    server.listen(port, hostName, new AsyncResultHandler<NetServer>() {
      @Override
      public void handle(AsyncResult<NetServer> asyncResult) {
        if (asyncResult.succeeded()) {
          // Obtain system configured public host/port
          int publicPort = Integer.getInteger("vertx.cluster.public.port", -1);
          String publicHost = System.getProperty("vertx.cluster.public.host", null);

          // If using a wilcard port (0) then we ask the server for the actual port:
          int serverPort = (publicPort == -1) ? server.port() : publicPort;
          String serverHost = (publicHost == null) ? hostName : publicHost;
          DefaultEventBus.this.serverID = new ServerID(serverPort, serverHost);
        }
        if (listenHandler != null) {
          if (asyncResult.succeeded()) {
            listenHandler.handle(new DefaultFutureResult<>((Void)null));
          } else {
            listenHandler.handle(new DefaultFutureResult<Void>(asyncResult.cause()));
          }
        } else if (asyncResult.failed()) {
          log.error("Failed to listen", asyncResult.cause());
        }
      }
    });
    return server;
  }

  private <T> void sendToSubs(ChoosableIterable<ServerID> subs, BaseMessage message,
                              long timeoutID,
                              Handler<AsyncResult<Message<T>>> asyncResultHandler,
                              Handler<Message<T>> replyHandler) {
    if (message.send) {
      // Choose one
      ServerID sid = subs.choose();
      if (!sid.equals(serverID)) {  //We don't send to this node
        sendRemote(sid, message);
      } else {
        receiveMessage(message, timeoutID, asyncResultHandler, replyHandler);
      }
    } else {
      // Publish
      for (ServerID sid : subs) {
        if (!sid.equals(serverID)) {  //We don't send to this node
          sendRemote(sid, message);
        } else {
          receiveMessage(message, timeoutID, null, replyHandler);
        }
      }
    }
  }

  private <T, U> void sendOrPubWithTimeout(BaseMessage<U> message,
                                           Handler<AsyncResult<Message<T>>> asyncResultHandler, long timeout) {
    Handler<Message<T>> handler = convertHandler(asyncResultHandler);
    sendOrPub(null, message, handler, asyncResultHandler, timeout);
  }

  private <T, U> void sendOrPub(BaseMessage<U> message, Handler<Message<T>> replyHandler) {
    sendOrPub(null, message, replyHandler, -1);
  }

  private <T, U> void sendOrPub(ServerID replyDest, BaseMessage<U> message, Handler<Message<T>> replyHandler, long timeout) {
    sendOrPub(replyDest, message, replyHandler, null, timeout);
  }

  private String generateReplyAddress() {
    if (clusterMgr != null) {
      // The address is a cryptographically secure id that can't be guessed
      return UUID.randomUUID().toString();
    } else {
      // Just use a sequence - it's faster
      return Long.toString(replySequence.incrementAndGet());
    }
  }

  private <T, U> void sendOrPub(ServerID replyDest, final BaseMessage<U> message, final Handler<Message<T>> replyHandler,
                                final Handler<AsyncResult<Message<T>>> asyncResultHandler, long timeout) {
    checkStarted();
    DefaultContext context = vertx.getOrCreateContext();
    if (timeout == -1) {
      timeout = defaultReplyTimeout;
    }
    try {
      message.sender = serverID;
      long timeoutID = -1;
      if (replyHandler != null) {
        message.replyAddress = generateReplyAddress();
        if (timeout != -1) {
          // Add a timeout to remove the reply handler to prevent leaks in case a reply never comes
          timeoutID = vertx.setTimer(timeout, new Handler<Long>() {
            @Override
            public void handle(Long timerID) {
              log.warn("Message reply handler timed out as no reply was received - it will be removed");
              unregisterHandler(message.replyAddress, replyHandler);
              if (asyncResultHandler != null) {
                asyncResultHandler.handle(new DefaultFutureResult<Message<T>>(new ReplyException(ReplyFailure.TIMEOUT, "Timed out waiting for reply")));
              }
            }
          });
        }
        registerHandler(message.replyAddress, replyHandler, null, true, true, timeoutID);
      }
      if (replyDest != null) {
        if (!replyDest.equals(this.serverID)) {
          sendRemote(replyDest, message);
        } else {
          receiveMessage(message, timeoutID, asyncResultHandler, replyHandler);
        }
      } else {
        if (subs != null) {
          final long fTimeoutID = timeoutID;
          subs.get(message.address, new AsyncResultHandler<ChoosableIterable<ServerID>>() {
            public void handle(AsyncResult<ChoosableIterable<ServerID>> event) {
              if (event.succeeded()) {
                ChoosableIterable<ServerID> serverIDs = event.result();
                if (serverIDs != null && !serverIDs.isEmpty()) {
                  sendToSubs(serverIDs, message, fTimeoutID, asyncResultHandler, replyHandler);
                } else {
                  receiveMessage(message, fTimeoutID, asyncResultHandler, replyHandler);
                }
              } else {
                log.error("Failed to send message", event.cause());
              }
            }
          });
        } else {
          // Not clustered
          receiveMessage(message, timeoutID, asyncResultHandler, replyHandler);
        }
      }

    } finally {
      // Reset the context id - send can cause messages to be delivered in different contexts so the context id
      // of the current thread can change
      if (context != null) {
        vertx.setContext(context);
      }
    }
  }

  private <T> Handler<Message<T>> convertHandler(final Handler<AsyncResult<Message<T>>> handler) {
    return new Handler<Message<T>>() {
      @Override
      public void handle(Message<T> reply) {
        DefaultFutureResult<Message<T>> result;
        if (reply.body() instanceof ReplyException) {
          // This is kind of clunky - but hey-ho
          result = new DefaultFutureResult<>((ReplyException)reply.body());
        } else {
          result = new DefaultFutureResult<>(reply);
        }
        handler.handle(result);
      }
    };
  }

  private void registerHandler(String address, Handler<? extends Message> handler,
                               Handler<AsyncResult<Void>> completionHandler,
                               boolean replyHandler, boolean localOnly, long timeoutID) {
    checkStarted();
    if (address == null) {
      throw new NullPointerException("address");
    }
    DefaultContext context = vertx.getContext();
    boolean hasContext = context != null;
    if (!hasContext) {
      context = vertx.createEventLoopContext();
    }
    Handlers handlers = handlerMap.get(address);
    if (handlers == null) {
      handlers = new Handlers();
      Handlers prevHandlers = handlerMap.putIfAbsent(address, handlers);
      if (prevHandlers != null) {
        handlers = prevHandlers;
      }
      if (completionHandler == null) {
        completionHandler = new Handler<AsyncResult<Void>>() {
          public void handle(AsyncResult<Void> event) {
            if (event.failed()) {
              log.error("Failed to remove entry", event.cause());
            }
          }
        };
      }
      handlers.list.add(new HandlerHolder(handler, replyHandler, localOnly, context, timeoutID));
      if (subs != null && !replyHandler && !localOnly) {
        // Propagate the information
        subs.add(address, serverID, completionHandler);
      } else {
        callCompletionHandler(completionHandler);
      }
    } else {
      handlers.list.add(new HandlerHolder(handler, replyHandler, localOnly, context, timeoutID));
      if (completionHandler != null) {
        callCompletionHandler(completionHandler);
      }
    }
    if (hasContext) {
      HandlerEntry entry = new HandlerEntry(address, handler);
      context.addCloseHook(entry);
    }
  }

  private void callCompletionHandler(Handler<AsyncResult<Void>> completionHandler) {
    completionHandler.handle(new DefaultFutureResult<>((Void) null));
  }

  private void cleanSubsForServerID(ServerID theServerID) {
    if (subs != null) {
      subs.removeAllForValue(theServerID, new Handler<AsyncResult<Void>>() {
        public void handle(AsyncResult<Void> event) {
        }
      });
    }
  }

  private void cleanupConnection(ServerID theServerID,
                                 ConnectionHolder holder,
                                 boolean failed) {
    if (holder.timeoutID != -1) {
      vertx.cancelTimer(holder.timeoutID);
    }
    if (holder.pingTimeoutID != -1) {
      vertx.cancelTimer(holder.pingTimeoutID);
    }
    try {
      holder.socket.close();
    } catch (Exception ignore) {
    }

    // The holder can be null or different if the target server is restarted with same serverid
    // before the cleanup for the previous one has been processed
    // So we only actually remove the entry if no new entry has been added
    if (connections.remove(theServerID, holder)) {
      log.debug("Cluster connection closed: " + theServerID + " holder " + holder);

      if (failed) {
        cleanSubsForServerID(theServerID);
      }
    }
  }

  private void sendRemote(final ServerID theServerID, final BaseMessage message) {
    // We need to deal with the fact that connecting can take some time and is async, and we cannot
    // block to wait for it. So we add any sends to a pending list if not connected yet.
    // Once we connect we send them.
    // This can also be invoked concurrently from different threads, so it gets a little
    // tricky
    ConnectionHolder holder = connections.get(theServerID);
    if (holder == null) {
      NetClient client = vertx.createNetClient();
      // When process is creating a lot of connections this can take some time
      // so increase the timeout
      client.setConnectTimeout(60 * 1000);
      holder = new ConnectionHolder(client);
      ConnectionHolder prevHolder = connections.putIfAbsent(theServerID, holder);
      if (prevHolder != null) {
        // Another one sneaked in
        holder = prevHolder;
      }
      else {
        holder.connect(client, theServerID);
      }
    }
    holder.writeMessage(message);
  }

  private void schedulePing(final ConnectionHolder holder) {
    holder.pingTimeoutID = vertx.setTimer(PING_INTERVAL, new Handler<Long>() {
      public void handle(Long ignore) {
        // If we don't get a pong back in time we close the connection
        holder.timeoutID = vertx.setTimer(PING_REPLY_INTERVAL, new Handler<Long>() {
          public void handle(Long timerID) {
            // Didn't get pong in time - consider connection dead
            log.warn("No pong from server " + serverID + " - will consider it dead, timerID: " + timerID + " holder " + holder);
            cleanupConnection(holder.theServerID, holder, true);
          }
        });
        new PingMessage(serverID).write(holder.socket);
      }
    });
  }

  private void removeSub(String subName, ServerID theServerID, final Handler<AsyncResult<Void>> completionHandler) {
    subs.remove(subName, theServerID, completionHandler);
  }

  // Called when a message is incoming
  private <T> void receiveMessage(BaseMessage msg, long timeoutID, Handler<AsyncResult<Message<T>>> asyncResultHandler,
                                  Handler<Message<T>> replyHandler) {
    msg.bus = this;
    final Handlers handlers = handlerMap.get(msg.address);
    if (handlers != null) {
      if (msg.send) {
        //Choose one
        HandlerHolder holder = handlers.choose();
        if (holder != null) {
          doReceive(msg, holder);
        }
      } else {
        // Publish
        for (HandlerHolder holder: handlers.list) {
          doReceive(msg, holder);
        }
      }
    } else {
      // no handlers
      if (asyncResultHandler != null) {
        sendNoHandlersFailure(asyncResultHandler);
        if (timeoutID != -1) {
          vertx.cancelTimer(timeoutID);
        }
        if (replyHandler != null) {
          unregisterHandler(msg.replyAddress, replyHandler);
        }
      }
    }
  }

  private <T> void sendNoHandlersFailure(final Handler<AsyncResult<Message<T>>> handler) {
    vertx.runOnContext(new Handler<Void>() {
      @Override
      public void handle(Void v) {
        handler.handle(new DefaultFutureResult<Message<T>>(new ReplyException(ReplyFailure.NO_HANDLERS)));
      }
    });
  }


  private <T> void doReceive(final BaseMessage<T> msg, final HandlerHolder<T> holder) {
    // Each handler gets a fresh copy
    final Message<T> copied = msg.copy();

    holder.context.execute(new Runnable() {
      public void run() {
        // Need to check handler is still there - the handler might have been removed after the message were sent but
        // before it was received
        try {
          if (!holder.removed) {
            holder.handler.handle(copied);
          }
        } finally {
          if (holder.replyHandler) {
            unregisterHandler(msg.address, holder.handler);
          }
        }
      }
    });
  }

  private void checkStarted() {
    if (serverID == null) {
      throw new IllegalStateException("Event Bus is not started");
    }
  }

  private static class HandlerHolder<T> {
    final DefaultContext context;
    final Handler<Message<T>> handler;
    final boolean replyHandler;
    final boolean localOnly;
    final long timeoutID;
    boolean removed;

    HandlerHolder(Handler<Message<T>> handler, boolean replyHandler, boolean localOnly, DefaultContext context, long timeoutID) {
      this.context = context;
      this.handler = handler;
      this.replyHandler = replyHandler;
      this.localOnly = localOnly;
      this.timeoutID = timeoutID;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      HandlerHolder that = (HandlerHolder) o;
      return handler.equals(that.handler);
    }

    @Override
    public int hashCode() {
      return handler.hashCode();
    }

  }

  private class ConnectionHolder {
    final NetClient client;
    volatile NetSocket socket;
    final Queue<BaseMessage> pending = new ConcurrentLinkedQueue<>();
    volatile boolean connected;
    long timeoutID = -1;
    long pingTimeoutID = -1;
    ServerID theServerID;

    private ConnectionHolder(NetClient client) {
      this.client = client;
    }

    void writeMessage(BaseMessage message) {
      if (connected) {
        message.write(socket);
      } else {
        synchronized (this) {
          if (connected) {
            message.write(socket);
          } else {
            pending.add(message);
          }
        }
      }
    }

    synchronized void connected(final ServerID theServerID, NetSocket socket) {
      this.socket = socket;
      this.theServerID = theServerID;
      connected = true;
      socket.exceptionHandler(new Handler<Throwable>() {
        public void handle(Throwable t) {
          cleanupConnection(theServerID, ConnectionHolder.this, true);
        }
      });
      socket.closeHandler(new VoidHandler() {
        public void handle() {
          cleanupConnection(theServerID, ConnectionHolder.this, false);
        }
      });
      socket.dataHandler(new Handler<Buffer>() {
        public void handle(Buffer data) {
          // Got a pong back
          vertx.cancelTimer(timeoutID);
          schedulePing(ConnectionHolder.this);
        }
      });
      // Start a pinger
      schedulePing(ConnectionHolder.this);
      for (BaseMessage message : pending) {
        message.write(socket);
      }
      pending.clear();
    }

    void connect(NetClient client, final ServerID theServerID) {
      client.connect(theServerID.port, theServerID.host, new AsyncResultHandler<NetSocket>() {
        public void handle(AsyncResult<NetSocket> res) {
          if (res.succeeded()) {
            connected(theServerID, res.result());
          } else {
            cleanupConnection(theServerID, ConnectionHolder.this, true);
          }
        }
      });
    }
  }

  private static class Handlers {

    final List<HandlerHolder> list = new CopyOnWriteArrayList<>();
    final AtomicInteger pos = new AtomicInteger(0);
    HandlerHolder choose() {
      while (true) {
        int size = list.size();
        if (size == 0) {
          return null;
        }
        int p = pos.getAndIncrement();
        if (p >= size - 1) {
          pos.set(0);
        }
        try {
          return list.get(p);
        } catch (IndexOutOfBoundsException e) {
          // Can happen
          pos.set(0);
        }
      }
    }
  }

  private class HandlerEntry implements Closeable {
    final String address;
    final Handler<? extends Message> handler;

    private HandlerEntry(String address, Handler<? extends Message> handler) {
      this.address = address;
      this.handler = handler;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (getClass() != o.getClass()) return false;
      HandlerEntry entry = (HandlerEntry) o;
      if (!address.equals(entry.address)) return false;
      if (!handler.equals(entry.handler)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = address != null ? address.hashCode() : 0;
      result = 31 * result + (handler != null ? handler.hashCode() : 0);
      return result;
    }

    // Called by context on undeploy
    public void close(Handler<AsyncResult<Void>> doneHandler) {
      unregisterHandler(this.address, this.handler);
      doneHandler.handle(new DefaultFutureResult<>((Void)null));
    }
  }
}

