/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Headers;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.Registration;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.eventbus.impl.codecs.BooleanMessageCodec;
import io.vertx.core.eventbus.impl.codecs.BufferMessageCodec;
import io.vertx.core.eventbus.impl.codecs.ByteArrayMessageCodec;
import io.vertx.core.eventbus.impl.codecs.ByteMessageCodec;
import io.vertx.core.eventbus.impl.codecs.CharMessageCodec;
import io.vertx.core.eventbus.impl.codecs.DoubleMessageCodec;
import io.vertx.core.eventbus.impl.codecs.FloatMessageCodec;
import io.vertx.core.eventbus.impl.codecs.IntMessageCodec;
import io.vertx.core.eventbus.impl.codecs.JsonArrayMessageCodec;
import io.vertx.core.eventbus.impl.codecs.JsonObjectMessageCodec;
import io.vertx.core.eventbus.impl.codecs.LongMessageCodec;
import io.vertx.core.eventbus.impl.codecs.NullMessageCodec;
import io.vertx.core.eventbus.impl.codecs.ReplyExceptionMessageCodec;
import io.vertx.core.eventbus.impl.codecs.ShortMessageCodec;
import io.vertx.core.eventbus.impl.codecs.StringMessageCodec;
import io.vertx.core.impl.Closeable;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;
import io.vertx.core.spi.cluster.ClusterManager;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>                                                                                        T
 */
public class EventBusImpl implements EventBus {

  private static final Logger log = LoggerFactory.getLogger(EventBusImpl.class);

  // The standard message codecs
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
  private static final MessageCodec<String, String> NULL_MESSAGE_CODEC = new NullMessageCodec();

  private static final String PING_ADDRESS = "__vertx.ping";
  private static final long PING_INTERVAL = 20000;
  private static final long PING_REPLY_INTERVAL = 20000;

  private final VertxInternal vertx;
  private ServerID serverID;
  private NetServer server;
  private AsyncMultiMap<String, ServerID> subs;
  private final ConcurrentMap<ServerID, ConnectionHolder> connections = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Handlers> handlerMap = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, MessageCodec> userCodecMap = new ConcurrentHashMap<>();
  private final ConcurrentMap<Class, MessageCodec> defaultCodecMap = new ConcurrentHashMap<>();
  private final ClusterManager clusterMgr;
  private final AtomicLong replySequence = new AtomicLong(0);
  private final ProxyFactory proxyFactory;
  private Registration pingRegistration;
  private MessageCodec[] systemCodecs;

  public EventBusImpl(VertxInternal vertx, long proxyOperationTimeout) {
    // Just some dummy server ID
    this.vertx = vertx;
    this.serverID = new ServerID(-1, "localhost");
    this.server = null;
    this.subs = null;
    this.clusterMgr = null;
    this.proxyFactory = new ProxyFactory(this, proxyOperationTimeout);
    setPingHandler();
    putStandardCodecs();
  }

  public EventBusImpl(VertxInternal vertx, long proxyOperationTimeout, int port, String hostname, ClusterManager clusterManager,
                      Handler<AsyncResult<Void>> listenHandler) {
    this.vertx = vertx;
    this.clusterMgr = clusterManager;
    this.proxyFactory = new ProxyFactory(this, proxyOperationTimeout);
    clusterMgr.<String, ServerID>getAsyncMultiMap("subs", null, ar -> {
      if (ar.succeeded()) {
        subs = ar.result();
        this.server = setServer(port, hostname, listenHandler);
      } else {
        if (listenHandler != null) {
          listenHandler.handle(Future.completedFuture(ar.cause()));
        } else {
          log.error(ar.cause());
        }
      }
    });
    putStandardCodecs();
  }

  @Override
  public EventBus send(String address, Object message) {
    return send(address, message, DeliveryOptions.options(), null);
  }

  @Override
  public <T> EventBus send(String address, Object message, Handler<AsyncResult<Message<T>>> replyHandler) {
    return send(address, message, DeliveryOptions.options(), replyHandler);
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
  public EventBus publish(String address, Object message) {
    return publish(address, message, DeliveryOptions.options());
  }

  @Override
  public EventBus publish(String address, Object message, DeliveryOptions options) {
    sendOrPub(null, createMessage(false, address, options.getHeaders(), message, options.getCodecName()), options, null);
    return this;
  }


  @Override
  public EventBus forward(String address, Object message) {
	  forward(address, message, null);
	  return null;
  }

  @Override
  public EventBus forward(String address, Object message, DeliveryOptions options) {
	  sendOrPub(null, (MessageImpl)message, options, null);
	  return this;
  }

  @Override
  public <T> Registration registerHandler(String address, Handler<Message<T>> handler) {
    return registerHandler(address, handler, false, false, -1);
  }

  @Override
  public <T> Registration registerLocalHandler(String address, Handler<Message<T>> handler) {
    return registerHandler(address, handler, false, true, -1);
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
  public <T> T createProxy(Class<T> clazz, String address) {
    return proxyFactory.createProxy(clazz, address);
  }

  @Override
  public <T> Registration registerService(T service, String address) {
    return proxyFactory.registerService(service, address);
  }

  @Override
  public void close(Handler<AsyncResult<Void>> completionHandler) {
    if (server != null) {
      server.close(ar -> {
        if (ar.failed()) {
          log.error("Failed to close server", ar.cause());
        }
        closeClusterManager(completionHandler);
        pingRegistration.unregister();
      });
    } else {
      closeClusterManager(completionHandler);
      pingRegistration.unregister();
    }
  }

  <T> void sendReply(ServerID dest, MessageImpl message, DeliveryOptions options, Handler<AsyncResult<Message<T>>> replyHandler) {
    if (message.address() == null) {
      sendNoHandlersFailure(replyHandler);
    } else {
      sendOrPub(dest, message, options, replyHandler);
    }
  }

  MessageImpl createMessage(boolean send, String address, Headers headers, Object body, String codecName) {
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
          vertx.runOnContext(v -> completionHandler.handle(Future.completedFuture()));
        }
      });
    } else if (completionHandler != null) {
      vertx.runOnContext(v -> completionHandler.handle(Future.completedFuture()));
    }
  }

  private void setPingHandler() {
    pingRegistration = registerHandler(PING_ADDRESS, msg -> {
      msg.reply(null);
    });
  }

  private NetServer setServer(int port, String hostName, Handler<AsyncResult<Void>> listenHandler) {
    NetServer server = vertx.createNetServer(NetServerOptions.options().setPort(port).setHost(hostName)).connectHandler(socket -> {
      RecordParser parser = RecordParser.newFixed(4, null);
      Handler<Buffer> handler = new Handler<Buffer>() {
        int size = -1;

        public void handle(Buffer buff) {
          if (size == -1) {
            size = buff.getInt(0);
            parser.fixedSizeMode(size);
          } else {
            MessageImpl received = new MessageImpl();
            received.readFromWire(buff, userCodecMap, systemCodecs);
            receiveMessage(received, -1, null, null);
            parser.fixedSizeMode(4);
            size = -1;
          }
        }
      };
      parser.setOutput(handler);
      socket.dataHandler(parser);
    });

    server.listen(asyncResult -> {
      if (asyncResult.succeeded()) {
        // Obtain system configured public host/port
        int publicPort = Integer.getInteger("vertx.cluster.public.port", -1);
        String publicHost = System.getProperty("vertx.cluster.public.host", null);

        // If using a wilcard port (0) then we ask the server for the actual port:
        int serverPort = (publicPort == -1) ? server.actualPort() : publicPort;
        String serverHost = (publicHost == null) ? hostName : publicHost;
        EventBusImpl.this.serverID = new ServerID(serverPort, serverHost);
        setPingHandler();
      }
      if (listenHandler != null) {
        if (asyncResult.succeeded()) {
          listenHandler.handle(Future.completedFuture());
        } else {
          listenHandler.handle(Future.completedFuture(asyncResult.cause()));
        }
      } else if (asyncResult.failed()) {
        log.error("Failed to listen", asyncResult.cause());
      }
    });
    return server;
  }

  private <T> void sendToSubs(ChoosableIterable<ServerID> subs, MessageImpl message,
                              long timeoutID,
                              Handler<AsyncResult<Message<T>>> asyncResultHandler,
                              Handler<Message<T>> replyHandler) {
    if (message.send()) {
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

  private void putStandardCodecs() {
    putCodecs(STRING_MESSAGE_CODEC, BUFFER_MESSAGE_CODEC, JSON_OBJECT_MESSAGE_CODEC, JSON_ARRAY_MESSAGE_CODEC,
      BYTE_ARRAY_MESSAGE_CODEC, INT_MESSAGE_CODEC, LONG_MESSAGE_CODEC, FLOAT_MESSAGE_CODEC, DOUBLE_MESSAGE_CODEC,
      BOOLEAN_MESSAGE_CODEC, SHORT_MESSAGE_CODEC, CHAR_MESSAGE_CODEC, BYTE_MESSAGE_CODEC, REPLY_EXCEPTION_MESSAGE_CODEC,
      NULL_MESSAGE_CODEC);
  }

  private void putCodecs(MessageCodec... codecs) {
    systemCodecs = new MessageCodec[codecs.length];
    for (MessageCodec codec: codecs) {
      systemCodecs[codec.systemCodecID()] = codec;
    }
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
    ContextImpl context = vertx.getOrCreateContext();
    Handler<Message<T>> simpleReplyHandler = null;
    try {
      long timeoutID = -1;
      if (replyHandler != null) {
        message.setReplyAddress(generateReplyAddress());
        AtomicReference<Registration> refReg = new AtomicReference<>();
        // Add a timeout to remove the reply handler to prevent leaks in case a reply never comes
        timeoutID = vertx.setTimer(options.getSendTimeout(), timerID -> {
          log.warn("Message reply handler timed out as no reply was received - it will be removed");
          refReg.get().unregister();
          replyHandler.handle(Future.completedFuture(new ReplyException(ReplyFailure.TIMEOUT, "Timed out waiting for reply")));
        });
        simpleReplyHandler = convertHandler(replyHandler);
        Registration registration = registerHandler(message.replyAddress(), simpleReplyHandler, true, true, timeoutID);
        refReg.set(registration);
      }
      if (replyDest != null) {
        if (!replyDest.equals(this.serverID)) {
          sendRemote(replyDest, message);
        } else {
          receiveMessage(message, timeoutID, replyHandler, simpleReplyHandler);
        }
      } else {
        if (subs != null) {
          long fTimeoutID = timeoutID;
          Handler<Message<T>> fSimpleReplyHandler = simpleReplyHandler;
          subs.get(message.address(), asyncResult -> {
            if (asyncResult.succeeded()) {
              ChoosableIterable<ServerID> serverIDs = asyncResult.result();
              if (serverIDs != null && !serverIDs.isEmpty()) {
                sendToSubs(serverIDs, message, fTimeoutID, replyHandler, fSimpleReplyHandler);
              } else {
                receiveMessage(message, fTimeoutID, replyHandler, fSimpleReplyHandler);
              }
            } else {
              log.error("Failed to send message", asyncResult.cause());
            }
          });
        } else {
          // Not clustered
          receiveMessage(message, timeoutID, replyHandler, simpleReplyHandler);
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

  private <T> Handler<Message<T>> convertHandler(Handler<AsyncResult<Message<T>>> handler) {
    return reply -> {
      Future<Message<T>> result;
      if (reply.body() instanceof ReplyException) {
        // This is kind of clunky - but hey-ho
        result = Future.completedFuture((ReplyException) reply.body());
      } else {
        result = Future.completedFuture(reply);
      }
      handler.handle(result);
    };
  }

  private <T> Registration registerHandler(String address, Handler<Message<T>> handler,
                                       boolean replyHandler, boolean localOnly, long timeoutID) {
    checkStarted();
    if (address == null) {
      throw new NullPointerException("address");
    }
    if (handler == null) {
      throw new NullPointerException("handler");
    }
    ContextImpl context = vertx.getContext();
    boolean hasContext = context != null;
    if (!hasContext) {
      // Embedded
      context = vertx.createEventLoopContext(null, new JsonObject());
    }
    HandlerHolder holder = new HandlerHolder<T>(handler, replyHandler, localOnly, context, timeoutID);
    HandlerRegistration registration = new HandlerRegistration<T>(address, handler);

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
        registration.result = Future.completedFuture();
      }
    } else {
      registration.result = Future.completedFuture();
    }

    handlers.list.add(holder);

    if (hasContext) {
      HandlerEntry entry = new HandlerEntry<T>(address, handler);
      context.addCloseHook(entry);
    }

    return registration;
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
            holder.context.removeCloseHook(new HandlerEntry<T>(address, handler));
            break;
          }
        }
      }
    }
  }

  private <T> void unregisterHandler(String address, Handler<Message<T>> handler) {
    unregisterHandler(address, handler, emptyHandler());
  }

  private void callCompletionHandler(Handler<AsyncResult<Void>> completionHandler) {
    completionHandler.handle(Future.completedFuture());
  }

  private void cleanSubsForServerID(ServerID theServerID) {
    if (subs != null) {
      subs.removeAllForValue(theServerID, ar -> {
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

  private void sendRemote(ServerID theServerID, MessageImpl message) {
    // We need to deal with the fact that connecting can take some time and is async, and we cannot
    // block to wait for it. So we add any sends to a pending list if not connected yet.
    // Once we connect we send them.
    // This can also be invoked concurrently from different threads, so it gets a little
    // tricky
    ConnectionHolder holder = connections.get(theServerID);
    if (holder == null) {
      NetClient client = vertx.createNetClient(NetClientOptions.options().setConnectTimeout(60 * 1000));
      // When process is creating a lot of connections this can take some time
      // so increase the timeout
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

  private void schedulePing(ConnectionHolder holder) {
    holder.pingTimeoutID = vertx.setTimer(PING_INTERVAL, id1 -> {
      // If we don't get a pong back in time we close the connection
      holder.timeoutID = vertx.setTimer(PING_REPLY_INTERVAL, id2 -> {
        // Didn't get pong in time - consider connection dead
        log.warn("No pong from server " + serverID + " - will consider it dead, timerID: " + id2 + " holder " + holder);
        cleanupConnection(holder.theServerID, holder, true);
      });
      MessageImpl pingMessage = new MessageImpl<>(serverID, PING_ADDRESS, null, null, null, new NullMessageCodec(), true);
      holder.socket.write(pingMessage.encodeToWire());
    });
  }

  private void removeSub(String subName, ServerID theServerID, Handler<AsyncResult<Void>> completionHandler) {
    subs.remove(subName, theServerID, ar -> {
      if (!ar.succeeded()) {
        log.error("Couldn't find sub to remove");
      } else {
        completionHandler.handle(Future.completedFuture());
      }
    });
  }

  // Called when a message is incoming
  private <T> void receiveMessage(MessageImpl msg, long timeoutID, Handler<AsyncResult<Message<T>>> replyHandler,
                                  Handler<Message<T>> simpleReplyHandler) {
    msg.setBus(this);
    Handlers handlers = handlerMap.get(msg.address());
    if (handlers != null) {
      if (msg.send()) {
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
      if (replyHandler != null) {
        sendNoHandlersFailure(replyHandler);
        if (timeoutID != -1) {
          vertx.cancelTimer(timeoutID);
        }
        if (simpleReplyHandler != null) {
          unregisterHandler(msg.replyAddress(), simpleReplyHandler);
        }
      }
    }
  }

  private <T> void sendNoHandlersFailure(Handler<AsyncResult<Message<T>>> handler) {
    vertx.runOnContext(new Handler<Void>() {
      @Override
      public void handle(Void v) {
        handler.handle(Future.completedFuture(new ReplyException(ReplyFailure.NO_HANDLERS)));
      }
    });
  }


  private <T> void doReceive(MessageImpl msg, HandlerHolder<T> holder) {
    // Each handler gets a fresh copy
    @SuppressWarnings("unchecked")
    Message<T> copied = msg.copyBeforeReceive();

    holder.context.execute(() -> {
      // Need to check handler is still there - the handler might have been removed after the message were sent but
      // before it was received
      try {
        if (!holder.removed) {
          holder.handler.handle(copied);
        }
      } finally {
        if (holder.replyHandler) {
          unregisterHandler(msg.address(), holder.handler);
        }
      }
    }, false);
  }

  private void checkStarted() {
    if (serverID == null) {
      throw new IllegalStateException("Event Bus is not started");
    }
  }

  private static final Handler<?> _emptyHandler = e -> {};

  @SuppressWarnings("unchecked")
  private static <T> Handler<T> emptyHandler() {
    return (Handler<T>) _emptyHandler;
  }

  private static class HandlerHolder<T> {
    final ContextImpl context;
    final Handler<Message<T>> handler;
    final boolean replyHandler;
    final boolean localOnly;
    final long timeoutID;
    boolean removed;

    HandlerHolder(Handler<Message<T>> handler, boolean replyHandler, boolean localOnly, ContextImpl context, long timeoutID) {
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
    volatile NetSocket socket;
    final Queue<MessageImpl> pending = new ConcurrentLinkedQueue<>();
    volatile boolean connected;
    long timeoutID = -1;
    long pingTimeoutID = -1;
    ServerID theServerID;

    private ConnectionHolder(NetClient client) {
      this.client = client;
    }

    void writeMessage(MessageImpl message) {
      if (connected) {
        socket.write(message.encodeToWire());
      } else {
        synchronized (this) {
          if (connected) {
            socket.write(message.encodeToWire());
          } else {
            pending.add(message);
          }
        }
      }
    }

    synchronized void connected(ServerID theServerID, NetSocket socket) {
      this.socket = socket;
      this.theServerID = theServerID;
      connected = true;
      socket.exceptionHandler(t -> cleanupConnection(theServerID, ConnectionHolder.this, true));
      socket.closeHandler(v -> cleanupConnection(theServerID, ConnectionHolder.this, false));
      socket.dataHandler(data -> {
        // Got a pong back
        vertx.cancelTimer(timeoutID);
        schedulePing(ConnectionHolder.this);
      });
      // Start a pinger
      schedulePing(ConnectionHolder.this);
      for (MessageImpl message : pending) {
        socket.write(message.encodeToWire());
      }
      pending.clear();
    }

    void connect(NetClient client, ServerID theServerID) {
      client.connect(theServerID.port, theServerID.host, res -> {
        if (res.succeeded()) {
          connected(theServerID, res.result());
        } else {
          cleanupConnection(theServerID, ConnectionHolder.this, true);
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
      unregisterHandler(this.address, this.handler, emptyHandler());
      completionHandler.handle(Future.completedFuture());
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

  private class HandlerRegistration<T> implements Registration {
    private final String address;
    private final Handler<Message<T>> handler;
    private AsyncResult<Void> result;
    private Handler<AsyncResult<Void>> completionHandler;

    public HandlerRegistration(String address, Handler<Message<T>> handler) {
      this.address = address;
      this.handler = handler;
    }

    @Override
    public String address() {
      return address;
    }

    @Override
    public synchronized void completionHandler(Handler<AsyncResult<Void>> completionHandler) {
      Objects.requireNonNull(completionHandler);
      if (result != null) {
        completionHandler.handle(result);
      } else {
        this.completionHandler = completionHandler;
      }
    }

    @Override
    public void unregister() {
      unregister(emptyHandler());
    }

    @Override
    public void unregister(Handler<AsyncResult<Void>> completionHandler) {
      Objects.requireNonNull(completionHandler);
      unregisterHandler(address, handler, completionHandler);
    }

    private synchronized void setResult(AsyncResult<Void> result) {
      this.result = result;
      if (completionHandler != null) {
        completionHandler.handle(result);
      } else if (result.failed()) {
        log.error("Failed to propagate registration for handler " + handler + " and address " + address);
      }
    }
  }


}

