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

package org.vertx.java.core.eventbus.impl;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.impl.Context;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
  private final ServerID serverID;
  private NetServer server;
  private SubsMap subs;
  private final ConcurrentMap<ServerID, ConnectionHolder> connections = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Handlers> handlerMap = new ConcurrentHashMap<>();
  private final AtomicInteger seq = new AtomicInteger(0);
  private final String prefix = UUID.randomUUID().toString();
  private final ClusterManager clusterMgr;
  
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
    this.vertx = vertx;
    this.serverID = new ServerID(port, hostname);
    this.clusterMgr = clusterManager;
    this.subs = clusterMgr.getSubsMap("subs");
    this.server = setServer();
    ManagementRegistry.registerEventBus(serverID);
  }
  
  public void send(String address, JsonObject message, final Handler<Message<JsonObject>> replyHandler) {
    sendOrPub(new JsonObjectMessage(true, address, message), replyHandler);
  }

  public void send(String address, JsonObject message) {
    send(address, message, null);
  }

  public void send(String address, JsonArray message, final Handler<Message<JsonArray>> replyHandler) {
    sendOrPub(new JsonArrayMessage(true, address, message), replyHandler);
  }

  public void send(String address, JsonArray message) {
    send(address, message, null);
  }

  public void send(String address, Buffer message, final Handler<Message<Buffer>> replyHandler) {
    sendOrPub(new BufferMessage(true, address, message), replyHandler);
  }

  public void send(String address, Buffer message) {
    send(address, message, null);
  }

  public void send(String address, byte[] message, final Handler<Message<byte[]>> replyHandler) {
    sendOrPub(new ByteArrayMessage(true, address, message), replyHandler);
  }

  public void send(String address, byte[] message) {
    send(address, message, null);
  }

  public void send(String address, String message, final Handler<Message<String>> replyHandler) {
    sendOrPub(new StringMessage(true, address, message), replyHandler);
  }

  public void send(String address, String message) {
    send(address, message, null);
  }

  public void send(String address, Integer message, final Handler<Message<Integer>> replyHandler) {
    sendOrPub(new IntMessage(true, address, message), replyHandler);
  }

  public void send(String address, Integer message) {
    send(address, message, null);
  }

  public void send(String address, Long message, final Handler<Message<Long>> replyHandler) {
    sendOrPub(new LongMessage(true, address, message), replyHandler);
  }

  public void send(String address, Long message) {
    send(address, message, null);
  }

  public void send(String address, Float message, final Handler<Message<Float>> replyHandler) {
    sendOrPub(new FloatMessage(true, address, message), replyHandler);
  }

  public void send(String address, Float message) {
    send(address, message, null);
  }

  public void send(String address, Double message, final Handler<Message<Double>> replyHandler) {
    sendOrPub(new DoubleMessage(true, address, message), replyHandler);
  }

  public void send(String address, Double message) {
    send(address, message, null);
  }

  public void send(String address, Boolean message, final Handler<Message<Boolean>> replyHandler) {
    sendOrPub(new BooleanMessage(true, address, message), replyHandler);
  }

  public void send(String address, Boolean message) {
    send(address, message, null);
  }

  public void send(String address, Short message, final Handler<Message<Short>> replyHandler) {
    sendOrPub(new ShortMessage(true, address, message), replyHandler);
  }

  public void send(String address, Short message) {
    send(address, message, null);
  }

  public void send(String address, Character message, final Handler<Message<Character>> replyHandler) {
    sendOrPub(new CharacterMessage(true, address, message), replyHandler);
  }

  public void send(String address, Character message) {
    send(address, message, null);
  }

  public void send(String address, Byte message, final Handler<Message<Byte>> replyHandler) {
    sendOrPub(new ByteMessage(true, address, message), replyHandler);
  }

  public void send(String address, Byte message) {
    send(address, message, null);
  }

  public void publish(String address, JsonObject message) {
    sendOrPub(new JsonObjectMessage(false, address, message), null);
  }

  public void publish(String address, JsonArray message) {
    sendOrPub(new JsonArrayMessage(false, address, message), null);
  }

  public void publish(String address, Buffer message) {
    sendOrPub(new BufferMessage(false, address, message), null);
  }

  public void publish(String address, byte[] message) {
    sendOrPub(new ByteArrayMessage(false, address, message), null);
  }

  public void publish(String address, String message) {
    sendOrPub(new StringMessage(false, address, message), null);
  }

  public void publish(String address, Integer message) {
    sendOrPub(new IntMessage(false, address, message), null);
  }

  public void publish(String address, Long message) {
    sendOrPub(new LongMessage(false, address, message), null);
  }

  public void publish(String address, Float message) {
    sendOrPub(new FloatMessage(false, address, message), null);
  }

  public void publish(String address, Double message) {
    sendOrPub(new DoubleMessage(false, address, message), null);
  }

  public void publish(String address, Boolean message) {
    sendOrPub(new BooleanMessage(false, address, message), null);
  }

  public void publish(String address, Short message) {
    sendOrPub(new ShortMessage(false, address, message), null);
  }

  public void publish(String address, Character message) {
    sendOrPub(new CharacterMessage(false, address, message), null);
  }

  public void publish(String address, Byte message) {
    sendOrPub(new ByteMessage(false, address, message), null);
  }

  public void registerHandler(String address, Handler<? extends Message> handler,
                              AsyncResultHandler<Void> completionHandler) {
    registerHandler(address, handler, completionHandler, false, false);
  }

  public void registerHandler(String address, Handler<? extends Message> handler) {
    registerHandler(address, handler, null);
  }

  public void registerLocalHandler(String address, Handler<? extends Message> handler) {
    registerHandler(address, handler, null, false, true);
  }

  public void unregisterHandler(String address, Handler<? extends Message> handler,
                                AsyncResultHandler<Void> completionHandler) {
    Context context = vertx.getOrAssignContext();
    Handlers handlers = handlerMap.get(address);
    if (handlers != null) {
      synchronized (handlers) {
        int size = handlers.list.size();
        // Requires a list traversal. This is tricky to optimise since we can't use a set since
        // we need fast ordered traversal for the round robin
        for (int i = 0; i < size; i++) {
          HandlerHolder holder = handlers.list.get(i);
          if (holder.handler == handler) {
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
            getHandlerCloseHook(context).entries.remove(new HandlerEntry(address, handler));
            return;
          }
        }
      }
    }
  }

  public void unregisterHandler(String address, Handler<? extends Message> handler) {
    unregisterHandler(address, handler, null);
  }

  @Override
  public void close(Handler<Void> doneHandler) {
		if (clusterMgr != null) {
			clusterMgr.close();
		}
		if (server != null) {
			server.close(doneHandler);
		}
  }

  void sendReply(final ServerID dest, final BaseMessage message, final Handler replyHandler) {
    sendOrPub(dest, message, replyHandler);
  }

  private NetServer setServer() {
    return vertx.createNetServer().connectHandler(new Handler<NetSocket>() {
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
                receiveMessage(received);
              }
              parser.fixedSizeMode(4);
              size = -1;
            }
          }
        };
        parser.setOutput(handler);
        socket.dataHandler(parser);
      }
    }).listen(serverID.port, serverID.host);
  }

  private void sendToSubs(ServerIDs subs, BaseMessage message) {
    if (message.send) {
      // Choose one
      ServerID sid = subs.choose();
      if (!sid.equals(serverID)) {  //We don't send to this node
        sendRemote(sid, message);
      } else {
        receiveMessage(message);
      }
    } else {
      // Publish
      for (ServerID sid : subs) {
        if (!sid.equals(serverID)) {  //We don't send to this node
          sendRemote(sid, message);
        } else {
          receiveMessage(message);
        }
      }
    }
  }

  private void sendOrPub(final BaseMessage message, final Handler replyHandler) {
    sendOrPub(null, message, replyHandler);
  }

  private void sendOrPub(ServerID replyDest, final BaseMessage message, final Handler replyHandler) {
    Context context = vertx.getOrAssignContext();
    try {
      message.sender = serverID;
      if (replyHandler != null) {
        message.replyAddress = prefix + String.valueOf(seq.incrementAndGet());
        registerHandler(message.replyAddress, replyHandler, null, true, true);
      }
      if (replyDest != null) {
        if (!replyDest.equals(this.serverID)) {
          sendRemote(replyDest, message);
        } else {
          receiveMessage(message);
        }
      } else {
        if (subs != null) {
          subs.get(message.address, new AsyncResultHandler<ServerIDs>() {
            public void handle(AsyncResult<ServerIDs> event) {
              if (event.succeeded()) {
                ServerIDs serverIDs = event.result;
                if (!serverIDs.isEmpty()) {
                  sendToSubs(serverIDs, message);
                } else {
                  receiveMessage(message);
                }
              } else {
                log.error("Failed to send message", event.exception);
              }
            }
          });
        } else {
          // Not clustered
          receiveMessage(message);
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

  private void registerHandler(String address, Handler<? extends Message> handler,
                               AsyncResultHandler<Void> completionHandler,
                               boolean replyHandler, boolean localOnly) {
    if (address == null) {
      throw new NullPointerException("address");
    }
    Context context = vertx.getOrAssignContext();
    Handlers handlers = handlerMap.get(address);
    if (handlers == null) {
      handlers = new Handlers();
      Handlers prevHandlers = handlerMap.putIfAbsent(address, handlers);
      if (prevHandlers != null) {
        handlers = prevHandlers;
      }
      if (completionHandler == null) {
        completionHandler = new AsyncResultHandler<Void>() {
          public void handle(AsyncResult<Void> event) {
            if (event.failed()) {
              log.error("Failed to remove entry", event.exception);
            }
          }
        };
      }
      handlers.list.add(new HandlerHolder(handler, replyHandler, localOnly, context));
      if (subs != null && !replyHandler && !localOnly) {
        // Propagate the information
        subs.put(address, serverID, completionHandler);
      } else {
        callCompletionHandler(completionHandler);
      }
    } else {
      handlers.list.add(new HandlerHolder(handler, replyHandler, localOnly, context));
      if (completionHandler != null) {
        callCompletionHandler(completionHandler);
      }
    }
    getHandlerCloseHook(context).entries.add(new HandlerEntry(address, handler));
  }

  private HandlerCloseHook getHandlerCloseHook(Context context) {
    HandlerCloseHook hcl = (HandlerCloseHook)context.getCloseHook(this);
    if (hcl == null) {
      hcl = new HandlerCloseHook();
      context.putCloseHook(this, hcl);
    }
    return hcl;
  }

  private void callCompletionHandler(AsyncResultHandler<Void> completionHandler) {
    AsyncResult<Void> f = new AsyncResult<Void>().setHandler(completionHandler).setResult(null);
  }

  private void cleanSubsForServerID(ServerID theServerID) {
    if (subs != null) {
      subs.removeAllForServerID(theServerID, new AsyncResultHandler<Void>() {
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

  private void removeSub(String subName, ServerID theServerID, final AsyncResultHandler<Void> completionHandler) {
    subs.remove(subName, theServerID, new AsyncResultHandler<Boolean>() {
      public void handle(AsyncResult<Boolean> event) {
        if (completionHandler != null) {
          AsyncResult<Void> ar = new AsyncResult<>();
          if (event.failed()) {
            ar.setFailure(event.exception);
          } else {
            ar.setResult(null);
          }
          ar.setHandler(completionHandler);
        } else {
          if (event.failed()) {
            log.error("Failed to remove subscription", event.exception);
          }
        }
      }
    });
  }

  // Called when a message is incoming
  private void receiveMessage(final BaseMessage msg) {
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
        for (final HandlerHolder holder: handlers.list) {
          doReceive(msg, holder);
        }
      }
    }
  }

  private void doReceive(final BaseMessage msg, final HandlerHolder holder) {
    // Each handler gets a fresh copy
    final Message copied = msg.copy();

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
	
  private static class HandlerHolder {
    final Context context;
    final Handler handler;
    final boolean replyHandler;
    final boolean localOnly;
    boolean removed;

    HandlerHolder(Handler handler, boolean replyHandler, boolean localOnly, Context context) {
      this.context = context;
      this.handler = handler;
      this.replyHandler = replyHandler;
      this.localOnly = localOnly;
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
      socket.exceptionHandler(new Handler<Exception>() {
        public void handle(Exception e) {
          cleanupConnection(theServerID, ConnectionHolder.this, true);
        }
      });
      socket.closedHandler(new SimpleHandler() {
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
      client.connect(theServerID.port, theServerID.host, new Handler<NetSocket>() {
        public void handle(final NetSocket socket) {
          connected(theServerID, socket);
        }
      });
      client.exceptionHandler(new Handler<Exception>() {
        public void handle(Exception e) {
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

  private class HandlerEntry {
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
  }

  private class HandlerCloseHook implements Runnable {

    final Set<HandlerEntry> entries = new HashSet<>();

    public void run() {
      for (HandlerEntry entry: new HashSet<>(entries)) {
        unregisterHandler(entry.address, entry.handler);
      }
    }
  }
}

