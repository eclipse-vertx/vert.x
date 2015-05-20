/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.eventbus.impl;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.*;
import io.vertx.core.eventbus.impl.codecs.*;
import io.vertx.core.impl.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetClientImpl;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.streams.ReadStream;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>                                                                                        T
 */
public class EventBusImpl implements EventBus, MetricsProvider {

  private static final Logger log = LoggerFactory.getLogger(EventBusImpl.class);

  // The standard message codecs
  private static final MessageCodec<String, String> PING_MESSAGE_CODEC = new PingMessageCodec();
  private static final MessageCodec<String, String> NULL_MESSAGE_CODEC = new NullMessageCodec();
  private static final MessageCodec<String, String> STRING_MESSAGE_CODEC = new StringMessageCodec();
  private static final MessageCodec<Buffer, Buffer> BUFFER_MESSAGE_CODEC = new BufferMessageCodec();
  private static final MessageCodec<JsonObject, JsonObject> JSON_OBJECT_MESSAGE_CODEC = new JsonObjectMessageCodec();
  private static final MessageCodec<JsonArray, JsonArray> JSON_ARRAY_MESSAGE_CODEC = new JsonArrayMessageCodec();
  private static final MessageCodec<byte[], byte[]> BYTE_ARRAY_MESSAGE_CODEC = new ByteArrayMessageCodec();
  private static final MessageCodec<Integer, Integer> INT_MESSAGE_CODEC = new IntMessageCodec();
  private static final MessageCodec<Long, Long> LONG_MESSAGE_CODEC = new LongMessageCodec();
  private static final MessageCodec<Float, Float> FLOAT_MESSAGE_CODEC = new FloatMessageCodec();
  private static final MessageCodec<Double, Double> DOUBLE_MESSAGE_CODEC = new DoubleMessageCodec();
  private static final MessageCodec<Boolean, Boolean> BOOLEAN_MESSAGE_CODEC = new BooleanMessageCodec();
  private static final MessageCodec<Short, Short> SHORT_MESSAGE_CODEC = new ShortMessageCodec();
  private static final MessageCodec<Character, Character> CHAR_MESSAGE_CODEC = new CharMessageCodec();
  private static final MessageCodec<Byte, Byte> BYTE_MESSAGE_CODEC = new ByteMessageCodec();
  private static final MessageCodec<ReplyException, ReplyException> REPLY_EXCEPTION_MESSAGE_CODEC = new ReplyExceptionMessageCodec();

  private static final Buffer PONG = Buffer.buffer(new byte[] { (byte)1 });
  private static final String PING_ADDRESS = "__vertx_ping";

  private final VertxInternal vertx;
  private final long pingInterval;
  private final long pingReplyInterval;
  private final ConcurrentMap<ServerID, ConnectionHolder> connections = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Handlers> handlerMap = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, MessageCodec> userCodecMap = new ConcurrentHashMap<>();
  private final ConcurrentMap<Class, MessageCodec> defaultCodecMap = new ConcurrentHashMap<>();
  private final HAManager haManager;
  private final ClusterManager clusterMgr;
  private final AtomicLong replySequence = new AtomicLong(0);
  private final EventBusMetrics metrics;
  private final AsyncMultiMap<String, ServerID> subs;
  private final MessageCodec[] systemCodecs;
  private final ServerID serverID;
  private final NetServer server;
  private final Context sendNoContext;
  private volatile boolean sendPong = true;

  public EventBusImpl(VertxInternal vertx) {
    this.vertx = vertx;
    this.pingInterval = -1;
    this.pingReplyInterval = -1;
    // Just some dummy server ID
    this.serverID = new ServerID(-1, "localhost");
    this.server = null;
    this.subs = null;
    this.clusterMgr = null;
    this.haManager = null;
    this.metrics = vertx.metricsSPI().createMetrics(this);
    this.systemCodecs = systemCodecs();
    this.sendNoContext = vertx.getOrCreateContext();
  }

  public EventBusImpl(VertxInternal vertx, long pingInterval, long pingReplyInterval, ClusterManager clusterManager,
                      HAManager haManager,
                      AsyncMultiMap<String, ServerID> subs, ServerID serverID,
                      EventBusNetServer server) {
    this.vertx = vertx;
    this.clusterMgr = clusterManager;
    this.haManager = haManager;
    this.metrics = vertx.metricsSPI().createMetrics(this);
    this.pingInterval = pingInterval;
    this.pingReplyInterval = pingReplyInterval;
    this.subs = subs;
    this.systemCodecs = systemCodecs();
    this.serverID = serverID;
    this.server = server.netServer;
    this.sendNoContext = vertx.getOrCreateContext();
    setServerHandler(server);
    addFailoverCompleteHandler();
  }

  @Override
  public EventBus send(String address, Object message) {
    return send(address, message, new DeliveryOptions(), null);
  }

  @Override
  public <T> EventBus send(String address, Object message, Handler<AsyncResult<Message<T>>> replyHandler) {
    return send(address, message, new DeliveryOptions(), replyHandler);
  }

  @Override
  public <T> EventBus send(String address, Object message, DeliveryOptions options) {
    return send(address, message, options, null);
  }

  @Override
  public <T> EventBus send(String address, Object message, DeliveryOptions options, Handler<AsyncResult<Message<T>>> replyHandler) {
    sendOrPub(null, createMessage(true, address, options.getHeaders(), message, options.getCodecName()), options, replyHandler);
    return this;
  }

  @Override
  public <T> MessageProducer<T> sender(String address) {
    Objects.requireNonNull(address, "address");
    return new MessageProducerImpl<>(this, address, true, new DeliveryOptions());
  }

  @Override
  public <T> MessageProducer<T> sender(String address, DeliveryOptions options) {
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(options, "options");
    return new MessageProducerImpl<>(this, address, true, options);
  }

  @Override
  public <T> MessageProducer<T> publisher(String address) {
    Objects.requireNonNull(address, "address");
    return new MessageProducerImpl<>(this, address, false, new DeliveryOptions());
  }

  @Override
  public <T> MessageProducer<T> publisher(String address, DeliveryOptions options) {
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(options, "options");
    return new MessageProducerImpl<>(this, address, false, options);
  }

  @Override
  public EventBus publish(String address, Object message) {
    return publish(address, message, new DeliveryOptions());
  }

  @Override
  public EventBus publish(String address, Object message, DeliveryOptions options) {
    sendOrPub(null, createMessage(false, address, options.getHeaders(), message, options.getCodecName()), options, null);
    return this;
  }

  @Override
  public <T> MessageConsumer<T> consumer(String address) {
    Objects.requireNonNull(address, "address");
    return new HandlerRegistration<>(address, false, false, -1);
  }

  @Override
  public <T> MessageConsumer<T> consumer(String address, Handler<Message<T>> handler) {
    Objects.requireNonNull(handler, "handler");
    MessageConsumer<T> consumer = consumer(address);
    consumer.handler(handler);
    return consumer;
  }

  @Override
  public <T> MessageConsumer<T> localConsumer(String address) {
    Objects.requireNonNull(address, "address");
    return new HandlerRegistration<>(address, false, true, -1);
  }

  @Override
  public <T> MessageConsumer<T> localConsumer(String address, Handler<Message<T>> handler) {
    Objects.requireNonNull(handler, "handler");
    MessageConsumer<T> consumer = localConsumer(address);
    consumer.handler(handler);
    return consumer;
  }

  @Override
  public EventBus registerCodec(MessageCodec codec) {
    Objects.requireNonNull(codec, "codec");
    Objects.requireNonNull(codec.name(), "code.name()");
    checkSystemCodec(codec);
    if (userCodecMap.containsKey(codec.name())) {
      throw new IllegalStateException("Already a codec registered with name " + codec.name());
    }
    userCodecMap.put(codec.name(), codec);
    return this;
  }

  @Override
  public EventBus unregisterCodec(String name) {
    Objects.requireNonNull(name);
    userCodecMap.remove(name);
    return this;
  }

  @Override
  public <T> EventBus registerDefaultCodec(Class<T> clazz, MessageCodec<T, ?> codec) {
    Objects.requireNonNull(clazz);
    Objects.requireNonNull(codec, "codec");
    Objects.requireNonNull(codec.name(), "code.name()");
    checkSystemCodec(codec);
    if (defaultCodecMap.containsKey(clazz)) {
      throw new IllegalStateException("Already a default codec registered for class " + clazz);
    }
    if (userCodecMap.containsKey(codec.name())) {
      throw new IllegalStateException("Already a codec registered with name " + codec.name());
    }
    defaultCodecMap.put(clazz, codec);
    userCodecMap.put(codec.name(), codec);
    return this;
  }

  @Override
  public EventBus unregisterDefaultCodec(Class clazz) {
    Objects.requireNonNull(clazz);
    MessageCodec codec = defaultCodecMap.remove(clazz);
    if (codec != null) {
      userCodecMap.remove(codec.name());
    }
    return this;
  }

  @Override
  public void close(Handler<AsyncResult<Void>> completionHandler) {
    unregisterAllHandlers();
    if (metrics != null) {
      metrics.close();
    }
    if (server != null) {
      server.close(ar -> {
        if (ar.failed()) {
          log.error("Failed to close server", ar.cause());
        }
        // Close all outbound connections explicitly - don't rely on context hooks
        for (ConnectionHolder holder: connections.values()) {
          holder.close();
        }
        closeClusterManager(completionHandler);
      });
    } else {
      closeClusterManager(completionHandler);
    }
  }

  private void unregisterAllHandlers() {
    // Unregister all handlers explicitly - don't rely on context hooks
    for (Handlers handlers: handlerMap.values()) {
      for (HandlerHolder holder: handlers.list) {
        holder.handler.unregister(true);
      }
    }
  }

  @Override
  public boolean isMetricsEnabled() {
    return metrics != null && metrics.isEnabled();
  }

  @Override
  public EventBusMetrics<?> getMetrics() {
    return metrics;
  }

  <T> void sendReply(ServerID dest, MessageImpl message, DeliveryOptions options, Handler<AsyncResult<Message<T>>> replyHandler) {
    if (message.address() == null) {
      sendNoHandlersFailure(null, replyHandler);
    } else {
      sendOrPub(dest, message, options, replyHandler);
    }
  }

  // Used in testing
  public void simulateUnresponsive() {
    sendPong = false;
  }

  MessageImpl createMessage(boolean send, String address, MultiMap headers, Object body, String codecName) {
    Objects.requireNonNull(address, "no null address accepted");
    MessageCodec codec;
    if (codecName != null) {
      codec = userCodecMap.get(codecName);
      if (codec == null) {
        throw new IllegalArgumentException("No message codec for name: " + codecName);
      }
    } else if (body == null) {
      codec = NULL_MESSAGE_CODEC;
    } else if (body instanceof String) {
      codec = STRING_MESSAGE_CODEC;
    } else if (body instanceof Buffer) {
      codec = BUFFER_MESSAGE_CODEC;
    } else if (body instanceof JsonObject) {
      codec = JSON_OBJECT_MESSAGE_CODEC;
    } else if (body instanceof JsonArray) {
      codec = JSON_ARRAY_MESSAGE_CODEC;
    } else if (body instanceof byte[]) {
      codec = BYTE_ARRAY_MESSAGE_CODEC;
    } else if (body instanceof Integer) {
      codec = INT_MESSAGE_CODEC;
    } else if (body instanceof Long) {
      codec = LONG_MESSAGE_CODEC;
    } else if (body instanceof Float) {
      codec = FLOAT_MESSAGE_CODEC;
    } else if (body instanceof Double) {
      codec = DOUBLE_MESSAGE_CODEC;
    } else if (body instanceof Boolean) {
      codec = BOOLEAN_MESSAGE_CODEC;
    } else if (body instanceof Short) {
      codec = SHORT_MESSAGE_CODEC;
    } else if (body instanceof Character) {
      codec = CHAR_MESSAGE_CODEC;
    } else if (body instanceof Byte) {
      codec = BYTE_MESSAGE_CODEC;
    } else if (body instanceof ReplyException) {
      codec = REPLY_EXCEPTION_MESSAGE_CODEC;
    } else {
      codec = defaultCodecMap.get(body.getClass());
      if (codec == null) {
        throw new IllegalArgumentException("No message codec for type: " + body.getClass());
      }
    }
    @SuppressWarnings("unchecked")
    MessageImpl msg = new MessageImpl(serverID, address, null, headers, body, codec, send);
    return msg;
  }

  private void checkSystemCodec(MessageCodec codec) {
    if (codec.systemCodecID() != -1) {
      throw new IllegalArgumentException("Can't register a system codec");
    }
  }

  private void closeClusterManager(Handler<AsyncResult<Void>> completionHandler) {
    if (clusterMgr != null) {
      clusterMgr.leave(ar -> {
        if (ar.failed()) {
          log.error("Failed to leave cluster", ar.cause());
        }
        if (completionHandler != null) {
          vertx.runOnContext(v -> completionHandler.handle(Future.succeededFuture()));
        }
      });
    } else if (completionHandler != null) {
      vertx.runOnContext(v -> completionHandler.handle(Future.succeededFuture()));
    }
  }

  private void setServerHandler(EventBusNetServer server) {
    Handler<NetSocket> sockHandler = socket -> {
      RecordParser parser = RecordParser.newFixed(4, null);
      Handler<Buffer> handler = new Handler<Buffer>() {
        int size = -1;
        public void handle(Buffer buff) {
          if (size == -1) {
            size = buff.getInt(0);
            parser.fixedSizeMode(size);
          } else {
            MessageImpl received = new MessageImpl();
            received.readFromWire(socket, buff, userCodecMap, systemCodecs);
            metrics.messageRead(received.address(), buff.length());
            parser.fixedSizeMode(4);
            size = -1;
            if (received.codec() == PING_MESSAGE_CODEC) {
              // Just send back pong directly on connection
              if (sendPong) {
                socket.write(PONG);
              }
            } else {
              receiveMessage(received, -1, null, null, false);
            }
          }
        }
      };
      parser.setOutput(handler);
      socket.handler(parser);
    };
    server.setHandler(sockHandler);
  }

  private void addFailoverCompleteHandler() {
    haManager.setRemoveSubsHandler((failedNodeID, haInfo, failed) -> {
      JsonObject jsid = haInfo.getJsonObject("server_id");
      if (jsid != null) {
        ServerID sid = new ServerID(jsid.getInteger("port"), jsid.getString("host"));
        cleanSubsForServerID(sid);
      }
    });
  }

  private <T> void sendToSubs(ChoosableIterable<ServerID> subs, MessageImpl message,
                              long timeoutID,
                              Handler<AsyncResult<Message<T>>> asyncResultHandler,
                              Handler<Message<T>> replyHandler) {
    if (message.send()) {
      // Choose one
      ServerID sid = subs.choose();
      if (!sid.equals(serverID)) {  //We don't send to this node
        metrics.messageSent(message.address(), false, false, true);
        sendRemote(sid, message);
      } else {
        metrics.messageSent(message.address(), false, true, false);
        receiveMessage(message, timeoutID, asyncResultHandler, replyHandler, true);
      }
    } else {
      // Publish
      boolean local = false;
      boolean remote = false;
      for (ServerID sid : subs) {
        if (!sid.equals(serverID)) {  //We don't send to this node
          remote = true;
          sendRemote(sid, message);
        } else {
          local = true;
        }
      }
      metrics.messageSent(message.address(), true, local, remote);
      if (local) {
        receiveMessage(message, timeoutID, null, replyHandler, true);
      }
    }
  }

  private MessageCodec[] systemCodecs() {
    return codecs(NULL_MESSAGE_CODEC, PING_MESSAGE_CODEC, STRING_MESSAGE_CODEC, BUFFER_MESSAGE_CODEC, JSON_OBJECT_MESSAGE_CODEC, JSON_ARRAY_MESSAGE_CODEC,
      BYTE_ARRAY_MESSAGE_CODEC, INT_MESSAGE_CODEC, LONG_MESSAGE_CODEC, FLOAT_MESSAGE_CODEC, DOUBLE_MESSAGE_CODEC,
      BOOLEAN_MESSAGE_CODEC, SHORT_MESSAGE_CODEC, CHAR_MESSAGE_CODEC, BYTE_MESSAGE_CODEC, REPLY_EXCEPTION_MESSAGE_CODEC);
  }

  private MessageCodec[] codecs(MessageCodec... codecs) {
    MessageCodec[] arr = new MessageCodec[codecs.length];
    for (MessageCodec codec: codecs) {
      arr[codec.systemCodecID()] = codec;
    }
    return arr;
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

  private <T> void sendOrPub(ServerID replyDest, MessageImpl message, DeliveryOptions options,
                             Handler<AsyncResult<Message<T>>> replyHandler) {
    checkStarted();
    Handler<Message<T>> simpleReplyHandler = null;
    long timeoutID = -1;
    if (replyHandler != null) {
      message.setReplyAddress(generateReplyAddress());
      AtomicReference<MessageConsumer> refReg = new AtomicReference<>();
      // Add a timeout to remove the reply handler to prevent leaks in case a reply never comes
      timeoutID = vertx.setTimer(options.getSendTimeout(), timerID -> {
        log.warn("Message reply handler timed out as no reply was received - it will be removed");
        refReg.get().unregister();
        metrics.replyFailure(message.address(), ReplyFailure.TIMEOUT);
        replyHandler.handle(Future.failedFuture(new ReplyException(ReplyFailure.TIMEOUT, "Timed out waiting for reply")));
      });
      simpleReplyHandler = convertHandler(replyHandler);
      MessageConsumer registration = registerHandler(message.replyAddress(), simpleReplyHandler, true, true, timeoutID);
      refReg.set(registration);
    }
    if (replyDest != null) {
      if (!replyDest.equals(this.serverID)) {
        metrics.messageSent(message.address(), !message.send(), false, true);
        sendRemote(replyDest, message);
      } else {
        metrics.messageSent(message.address(), !message.send(), true, false);
        receiveMessage(message, timeoutID, replyHandler, simpleReplyHandler, true);
      }
    } else {
      if (subs != null) {
        long fTimeoutID = timeoutID;
        Handler<Message<T>> fSimpleReplyHandler = simpleReplyHandler;
        Handler<AsyncResult<ChoosableIterable<ServerID>>> resultHandler = asyncResult -> {
          if (asyncResult.succeeded()) {
            ChoosableIterable<ServerID> serverIDs = asyncResult.result();
            if (serverIDs != null && !serverIDs.isEmpty()) {
              sendToSubs(serverIDs, message, fTimeoutID, replyHandler, fSimpleReplyHandler);
            } else {
              metrics.messageSent(message.address(), !message.send(), true, false);
              receiveMessage(message, fTimeoutID, replyHandler, fSimpleReplyHandler, true);
            }
          } else {
            log.error("Failed to send message", asyncResult.cause());
          }
        };
        if (Vertx.currentContext() == null) {
          // Guarantees the order when there is no current context
          sendNoContext.runOnContext(v -> {
            subs.get(message.address(), resultHandler);
          });
        } else {
          subs.get(message.address(), resultHandler);
        }
      } else {
        // Not clustered
        metrics.messageSent(message.address(), !message.send(), true, false);
        receiveMessage(message, timeoutID, replyHandler, simpleReplyHandler, true);
      }
    }
  }

  private <T> Handler<Message<T>> convertHandler(Handler<AsyncResult<Message<T>>> handler) {
    return reply -> {
      Future<Message<T>> result;
      if (reply.body() instanceof ReplyException) {
        // This is kind of clunky - but hey-ho
        ReplyException exception = (ReplyException) reply.body();
        metrics.replyFailure(reply.address(), exception.failureType());
        result = Future.failedFuture(exception);
      } else {
        result = Future.succeededFuture(reply);
      }
      handler.handle(result);
    };
  }

  private <T> MessageConsumer registerHandler(String address, Handler<Message<T>> handler,
                                       boolean replyHandler, boolean localOnly, long timeoutID) {
    HandlerRegistration<T> registration = new HandlerRegistration<>(address, replyHandler, localOnly, timeoutID);
    registration.handler(handler);
    return registration;
  }

  private <T> void registerHandler(String address, HandlerRegistration<T> registration,
                                   boolean replyHandler, boolean localOnly, long timeoutID) {
    checkStarted();
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(registration.handler, "handler");
    ContextImpl context = vertx.getContext();
    boolean hasContext = context != null;
    if (!hasContext) {
      // Embedded
      context = vertx.createEventLoopContext(null, new JsonObject(), Thread.currentThread().getContextClassLoader());
    }
    HandlerHolder holder = new HandlerHolder<T>(registration, replyHandler, localOnly, context, timeoutID);

    Handlers handlers = handlerMap.get(address);
    if (handlers == null) {
      handlers = new Handlers();
      Handlers prevHandlers = handlerMap.putIfAbsent(address, handlers);
      if (prevHandlers != null) {
        handlers = prevHandlers;
      }
      if (subs != null && !replyHandler && !localOnly) {
        // Propagate the information
        subs.add(address, serverID, registration::setResult);
      } else {
        registration.setResult(Future.succeededFuture());
      }
    } else {
      registration.setResult(Future.succeededFuture());
    }

    handlers.list.add(holder);

    if (hasContext) {
      HandlerEntry entry = new HandlerEntry<T>(address, registration);
      context.addCloseHook(entry);
    }
  }

  private <T> void unregisterHandler(String address, Handler<Message<T>> handler, Handler<AsyncResult<Void>> completionHandler) {
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
            holder.setRemoved();
            if (handlers.list.isEmpty()) {
              handlerMap.remove(address);
              if (subs != null && !holder.localOnly) {
                removeSub(address, serverID, completionHandler);
              } else {
                callCompletionHandlerAsync(completionHandler);
              }
            } else {
              callCompletionHandlerAsync(completionHandler);
            }
            holder.context.removeCloseHook(new HandlerEntry<T>(address, handler));
            break;
          }
        }
      }
    }
  }

  private <T> void unregisterHandler(String address, Handler<Message<T>> handler) {
    unregisterHandler(address, handler, null);
  }

  private void callCompletionHandlerAsync(Handler<AsyncResult<Void>> completionHandler) {
    if (completionHandler != null) {
      vertx.runOnContext(v -> {
        completionHandler.handle(Future.succeededFuture());
      });
    }
  }

  private void cleanSubsForServerID(ServerID theServerID) {
    if (subs != null) {
      subs.removeAllForValue(theServerID, ar -> {
      });
    }
  }


  private void sendRemote(ServerID theServerID, MessageImpl message) {
    // We need to deal with the fact that connecting can take some time and is async, and we cannot
    // block to wait for it. So we add any sends to a pending list if not connected yet.
    // Once we connect we send them.
    // This can also be invoked concurrently from different threads, so it gets a little
    // tricky
    ConnectionHolder holder = connections.get(theServerID);
    if (holder == null) {
      // When process is creating a lot of connections this can take some time
      // so increase the timeout
      holder = new ConnectionHolder(theServerID);
      ConnectionHolder prevHolder = connections.putIfAbsent(theServerID, holder);
      if (prevHolder != null) {
        // Another one sneaked in
        holder = prevHolder;
      } else {
        holder.connect();
      }
    }
    holder.writeMessage(message);
  }

  private void removeSub(String subName, ServerID theServerID, Handler<AsyncResult<Void>> completionHandler) {
    subs.remove(subName, theServerID, ar -> {
      if (!ar.succeeded()) {
        log.error("Failed to remove sub", ar.cause());
      } else {
        if (ar.result()) {
          if (completionHandler != null) {
            completionHandler.handle(Future.succeededFuture());
          }
        } else {
          if (completionHandler != null) {
            completionHandler.handle(Future.failedFuture("sub not found"));
          }
        }

      }
    });
  }

  // Called when a message is incoming
  private <T> void receiveMessage(MessageImpl msg, long timeoutID, Handler<AsyncResult<Message<T>>> replyHandler,
                                  Handler<Message<T>> simpleReplyHandler, boolean local) {
    msg.setBus(this);
    Handlers handlers = handlerMap.get(msg.address());
    if (handlers != null) {
      if (msg.send()) {
        //Choose one
        HandlerHolder holder = handlers.choose();
        if (holder != null) {
          metrics.messageReceived(msg.address(), !msg.send(), local, 1);
          doReceive(msg, holder, local);
        }
      } else {
        // Publish
        metrics.messageReceived(msg.address(), !msg.send(), local, handlers.list.size());
        for (HandlerHolder holder: handlers.list) {
          doReceive(msg, holder, local);
        }
      }
    } else {
      metrics.messageReceived(msg.address(), !msg.send(), local, 0);
      // no handlers
      if (replyHandler != null) {
        sendNoHandlersFailure(msg.address(), replyHandler);
        if (timeoutID != -1) {
          vertx.cancelTimer(timeoutID);
        }
        if (simpleReplyHandler != null) {
          unregisterHandler(msg.replyAddress(), simpleReplyHandler);
        }
      }
    }
  }

  private <T> void sendNoHandlersFailure(String address, Handler<AsyncResult<Message<T>>> handler) {
    vertx.runOnContext(v -> {
      metrics.replyFailure(address, ReplyFailure.NO_HANDLERS);
      handler.handle(Future.failedFuture(new ReplyException(ReplyFailure.NO_HANDLERS)));
    });
  }


  private <T> void doReceive(MessageImpl msg, HandlerHolder<T> holder, boolean local) {
    // Each handler gets a fresh copy
    @SuppressWarnings("unchecked")
    Message<T> copied = msg.copyBeforeReceive();

    holder.context.runOnContext((v) -> {
      // Need to check handler is still there - the handler might have been removed after the message were sent but
      // before it was received
      try {
        if (!holder.isRemoved()) {
          holder.handler.handle(copied);
        }
      } finally {
        if (holder.replyHandler) {
          unregisterHandler(msg.address(), holder.handler);
        }
      }
    });
  }

  private void checkStarted() {
    if (serverID == null) {
      throw new IllegalStateException("Event Bus is not started");
    }
  }

  private class HandlerHolder<T> {
    final ContextImpl context;
    final HandlerRegistration<T> handler;
    final boolean replyHandler;
    final boolean localOnly;
    final long timeoutID;
    boolean removed;

    // We use a synchronized block to protect removed as it can be unregistered from a different thread
    void setRemoved() {
      boolean unregisterMetric = false;
      synchronized (this) {
        if (!removed) {
          removed = true;
          unregisterMetric = true;
        }
      }
      if (unregisterMetric) {
        metrics.handlerUnregistered(handler.metric);
      }
    }

    // Because of biased locks the overhead of the synchronized lock should be very low as it's almost always
    // called by the same event loop
    synchronized boolean isRemoved() {
      return removed;
    }

    HandlerHolder(HandlerRegistration<T> handler, boolean replyHandler, boolean localOnly, ContextImpl context, long timeoutID) {
      this.context = context;
      this.handler = handler;
      this.replyHandler = replyHandler;
      this.localOnly = localOnly;
      this.timeoutID = timeoutID;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      HandlerHolder that = (HandlerHolder) o;
      if (handler != null ? !handler.equals(that.handler) : that.handler != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return handler != null ? handler.hashCode() : 0;
    }
  }

  private class ConnectionHolder {
    final NetClient client;
    final Queue<MessageImpl> pending = new ArrayDeque<>();
    final ServerID theServerID;
    volatile NetSocket socket;
    volatile boolean connected;
    long timeoutID = -1;
    long pingTimeoutID = -1;

    private ConnectionHolder(ServerID serverID) {
      this.theServerID = serverID;
      client = new NetClientImpl(vertx, new NetClientOptions().setConnectTimeout(60 * 1000), false);
    }

    void close() {
      if (timeoutID != -1) {
        vertx.cancelTimer(timeoutID);
      }
      if (pingTimeoutID != -1) {
        vertx.cancelTimer(pingTimeoutID);
      }

      try {
        client.close();
      } catch (Exception ignore) {
      }

      // The holder can be null or different if the target server is restarted with same serverid
      // before the cleanup for the previous one has been processed
      if (connections.remove(theServerID, this)) {
        log.debug("Cluster connection closed: " + theServerID + " holder " + this);
      }
    }

    void schedulePing() {
      pingTimeoutID = vertx.setTimer(pingInterval, id1 -> {
        // If we don't get a pong back in time we close the connection
        timeoutID = vertx.setTimer(pingReplyInterval, id2 -> {
          // Didn't get pong in time - consider connection dead
          log.warn("No pong from server " + serverID + " - will consider it dead");
          close();
        });
        MessageImpl pingMessage = new MessageImpl<>(serverID, PING_ADDRESS, null, null, null, new PingMessageCodec(), true);
        Buffer data = pingMessage.encodeToWire();
        socket.write(data);
      });
    }

    // TODO optimise this (contention on monitor)
    synchronized void writeMessage(MessageImpl message) {
      if (connected) {
        Buffer data = message.encodeToWire();
        metrics.messageWritten(message.address(), data.length());
        socket.write(data);
      } else {
        pending.add(message);
      }
    }

    synchronized void connected(NetSocket socket) {
      this.socket = socket;
      connected = true;
      socket.exceptionHandler(t -> {
        close();
      });
      socket.closeHandler(v -> {
        close();
      });
      socket.handler(data -> {
        // Got a pong back
        vertx.cancelTimer(timeoutID);
        schedulePing();
      });
      // Start a pinger
      schedulePing();
      for (MessageImpl message : pending) {
        Buffer data = message.encodeToWire();
        metrics.messageWritten(message.address(), data.length());
        socket.write(data);
      }
      pending.clear();
    }

    void connect() {
      client.connect(theServerID.port, theServerID.host, res -> {
        if (res.succeeded()) {
          connected(res.result());
        } else {
          close();
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

  private class HandlerEntry<T> implements Closeable {
    final String address;
    final Handler<Message<T>> handler;

    private HandlerEntry(String address, Handler<Message<T>> handler) {
      this.address = address;
      this.handler = handler;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null) return false;
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
    public void close(Handler<AsyncResult<Void>> completionHandler) {
      unregisterHandler(this.address, this.handler, null);
      completionHandler.handle(Future.succeededFuture());
    }

  }

  @Override
  protected void finalize() throws Throwable {
    // Make sure this gets cleaned up if there are no more references to it
    // so as not to leave connections and resources dangling until the system is shutdown
    // which could make the JVM run out of file handles.
    close(ar -> {});
    super.finalize();
  }

  /*
   * This class is optimised for performance when used on the same event loop it was created on.
   * However it can be used safely from other threads.
   *
   * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
   * we benefit from biased locking which makes the overhead of synchronized near zero.
   */
  public class HandlerRegistration<T> implements MessageConsumer<T>, Handler<Message<T>> {

    private final String address;
    private final boolean replyHandler;
    private final boolean localOnly;
    private final long timeoutID;

    private boolean registered;
    private Handler<Message<T>> handler;
    private AsyncResult<Void> result;
    private Handler<AsyncResult<Void>> completionHandler;
    private Handler<Void> endHandler;
    private Handler<Message<T>> discardHandler;
    private int maxBufferedMessages;
    private final Queue<Message<T>> pending = new ArrayDeque<>(8);
    private boolean paused;
    private Object metric;

    public HandlerRegistration(String address, boolean replyHandler, boolean localOnly, long timeoutID) {
      this.address = address;
      this.replyHandler = replyHandler;
      this.localOnly = localOnly;
      this.timeoutID = timeoutID;
    }

    @Override
    public synchronized MessageConsumer<T> setMaxBufferedMessages(int maxBufferedMessages) {
      Arguments.require(maxBufferedMessages >= 0, "Max buffered messages cannot be negative");
      while (pending.size() > maxBufferedMessages) {
        pending.poll();
      }
      this.maxBufferedMessages = maxBufferedMessages;
      return this;
    }

    @Override
    public synchronized int getMaxBufferedMessages() {
      return maxBufferedMessages;
    }

    @Override
    public String address() {
      return address;
    }

    @Override
    public synchronized void completionHandler(Handler<AsyncResult<Void>> completionHandler) {
      Objects.requireNonNull(completionHandler);
      if (result != null) {
        AsyncResult<Void> value = result;
        vertx.runOnContext(v -> completionHandler.handle(value));
      } else {
        this.completionHandler = completionHandler;
      }
    }

    @Override
    public synchronized void unregister() {
      unregister(false);
    }

    @Override
    public synchronized void unregister(Handler<AsyncResult<Void>> completionHandler) {
      Objects.requireNonNull(completionHandler);
      doUnregister(completionHandler, false);
    }

    void unregister(boolean callEndHandler) {
      doUnregister(null, callEndHandler);
    }

    private void doUnregister(Handler<AsyncResult<Void>> completionHandler, boolean callEndHandler) {
      if (endHandler != null && callEndHandler) {
        Handler<Void> theEndHandler = endHandler;
        Handler<AsyncResult<Void>> handler = completionHandler;
        completionHandler = ar -> {
          theEndHandler.handle(null);
          if (handler != null) {
            handler.handle(ar);
          }
        };
      }
      if (registered) {
        registered = false;
        unregisterHandler(address, this, completionHandler);
      } else {
        callCompletionHandlerAsync(completionHandler);
      }
      registered = false;
    }

    private synchronized void setResult(AsyncResult<Void> result) {
      this.result = result;
      if (completionHandler != null) {
        if (result.succeeded()) {
          metric = metrics.handlerRegistered(address, replyHandler);
        }
        Handler<AsyncResult<Void>> callback = completionHandler;
        vertx.runOnContext(v -> callback.handle(result));
      } else if (result.failed()) {
        log.error("Failed to propagate registration for handler " + handler + " and address " + address);
      } else {
        metric = metrics.handlerRegistered(address, replyHandler);
      }
    }

    @Override
    public synchronized void handle(Message<T> event) {
      if (paused) {
        if (pending.size() < maxBufferedMessages) {
          pending.add(event);
        } else {
          if (discardHandler != null) {
            discardHandler.handle(event);
          }
        }
      } else {
        checkNextTick();
        MessageImpl abc = (MessageImpl) event;
        metrics.beginHandleMessage(metric, abc.getSocket() == null);
        try {
          handler.handle(event);
          metrics.endHandleMessage(metric, null);
        } catch (Exception e) {
          metrics.endHandleMessage(metric, e);
          throw e;
        }
      }
    }

    /*
     * Internal API for testing purposes.
     */
    public synchronized void discardHandler(Handler<Message<T>> handler) {
      this.discardHandler = handler;
    }

    @Override
    public synchronized MessageConsumer<T> handler(Handler<Message<T>> handler) {
      this.handler = handler;
      if (this.handler != null && !registered) {
        registered = true;
        registerHandler(address, this, replyHandler, localOnly, timeoutID);
      } else if (this.handler == null && registered) {
        // This will set registered to false
        this.unregister();
      }
      return this;
    }

    @Override
    public ReadStream<T> bodyStream() {
      return new BodyReadStream<>(this);
    }

    @Override
    public synchronized boolean isRegistered() {
      return registered;
    }

    @Override
    public synchronized MessageConsumer<T> pause() {
      if (!paused) {
        paused = true;
      }
      return this;
    }

    @Override
    public synchronized MessageConsumer<T> resume() {
      if (paused) {
        paused = false;
        checkNextTick();
      }
      return this;
    }

    @Override
    public synchronized MessageConsumer<T> endHandler(Handler<Void> endHandler) {
      if (endHandler != null) {
        // We should use the HandlerHolder context to properly do this (needs small refactoring)
        Context endCtx = vertx.getOrCreateContext();
        this.endHandler = v1 -> {
          endCtx.runOnContext(v2 -> {
            endHandler.handle(null);
          });
        };
      } else {
        this.endHandler = null;
      }
      return this;
    }

    @Override
    public synchronized MessageConsumer<T> exceptionHandler(Handler<Throwable> handler) {
      return this;
    }

    private void checkNextTick() {
      // Check if there are more pending messages in the queue that can be processed next time around
      if (!pending.isEmpty()) {
        vertx.runOnContext(v -> {
          if (!paused) {
            Message<T> message = pending.poll();
            if (message != null) {
              HandlerRegistration.this.handle(message);
            }
          }
        });
      }
    }
  }

  public static class EventBusNetServer {

    private final NetServer netServer;
    private Handler<NetSocket> handler;

    public EventBusNetServer(NetServer netServer) {
      this.netServer = netServer;
      netServer.connectHandler(conn -> {
        // The lock will almost always be obtained by the same thread so biased locking will mean there
        // is almost zero overhead to this synchronized block
        synchronized (EventBusNetServer.this) {
          handler.handle(conn);
        }
      });
    }

    public synchronized void setHandler(Handler<NetSocket> handler) {
      this.handler = handler;
    }
  }

}

