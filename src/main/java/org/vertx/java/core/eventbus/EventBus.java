package org.vertx.java.core.eventbus;

import com.hazelcast.util.ConcurrentHashSet;
import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleFuture;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.spi.AsyncMultiMap;
import org.vertx.java.core.eventbus.spi.ClusterManager;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.ServerID;
import org.vertx.java.core.parsetools.RecordParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>This class represents a distributed lightweight event bus which can encompass multiple vert.x instances.
 * It is very useful for otherwise isolated vert.x application instances to communicate with each other.</p>
 *
 * <p>Messages sent over the event bus are represented by instances of the  {@link Message} class.</p>
 *
 * <p>The event bus implements a distributed publish / subscribe network.</p>
 *
 * <p>Messages are sent to an address which is simply an arbitrary String.
 * There can be multiple handlers can be registered against that address.
 * Any handlers with a matching name will receive the message irrespective of what vert.x application instance and
 * what vert.x instance they are located in.</p>
 *
 * <p>All messages sent over the bus are transient. On event of failure of all or part of the event bus messages
 * may be lost. Applications should be coded to cope with lost messages, e.g. by resending them, and making application
 * services idempotent.</p>
 *
 * <p>The order of messages received by any specific handler from a specific sender should match the order of messages
 * sent from that sender.</p>
 *
 * <p>When sending a message, a reply handler can be provided. If so, it will be called when the reply from the receiver
 * has been received.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBus {

  private static final Logger log = Logger.getLogger(EventBus.class);

  /**
   * The event bus instance. Use this to obtain an instance of the event bus from within application code.
   */
  public static EventBus instance;

  public static void initialize(EventBus bus) {
    if (instance != null) {
      throw new IllegalStateException("Cannot call initialize more than once");
    }
    instance = bus;
  }

  private final ServerID serverID;
  private final NetServer server;
  private final AsyncMultiMap<String, ServerID> subs;  // Multimap name -> Collection<node ids>
  private final ConcurrentMap<ServerID, ConnectionHolder> connections = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Set<HandlerHolder>> handlers = new ConcurrentHashMap<>();
  private final Map<String, ServerID> replyAddressCache = new ConcurrentHashMap<>();

  /*
  Non clustered event bus
   */
  protected EventBus() {
    // Just some dummy server ID
    this.serverID = new ServerID(2550, "localhost");
    this.server = null;
    this.subs = null;
  }

  /*
  Clustered event bus
   */
  protected EventBus(ServerID serverID, ClusterManager clusterManager) {
    this.serverID = serverID;
    subs = clusterManager.getMultiMap("subs");
    this.server = setServer();
  }

  private NetServer setServer() {
    return new NetServer().connectHandler(new Handler<NetSocket>() {
      public void handle(NetSocket socket) {
        final RecordParser parser = RecordParser.newFixed(4, null);
        Handler<Buffer> handler = new Handler<Buffer>() {
          int size = -1;
          public void handle(Buffer buff) {
            if (size == -1) {
              size = buff.getInt(0);
              parser.fixedSizeMode(size);
            } else {
              Sendable received = Sendable.read(buff);
              if (received.type() == Sendable.TYPE_MESSAGE) {
                Message msg = (Message)received;
                receiveMessage(msg);
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

  /**
   * Send a message on the event bus
   * @param message The message
   * @param replyHandler An optional reply handler. It will be called when the reply from a receiver is received.
   */
  public void send(final Message message, final Handler<Message> replyHandler) {
    Long contextID = Vertx.instance.getContextID();
    try {
      if (message.messageID == null) {
        message.messageID = UUID.randomUUID().toString();
      }
      message.sender = serverID;
      if (replyHandler != null) {
        message.replyAddress = UUID.randomUUID().toString();
        registerHandler(message.replyAddress, replyHandler, null, true);
      }

      // First check if sender is in response address cache - it will be if it's a response
      ServerID serverID = replyAddressCache.remove(message.address);
      if (serverID != null) {
        // Yes, it's a response to a particular server
        if (!serverID.equals(this.serverID)) {
          send(serverID, message);
        } else {
          receiveMessage(message.copy());
        }
      } else {
        if (subs != null) {
          subs.get(message.address, new CompletionHandler<Collection<ServerID>>() {
            public void handle(Future<Collection<ServerID>> event) {
              Collection<ServerID> serverIDs = event.result();
              if (event.succeeded()) {
                if (serverIDs != null) {
                  for (ServerID serverID : serverIDs) {
                    if (!serverID.equals(EventBus.this.serverID)) {  //We don't send to this node
                      send(serverID, message);
                    }
                  }
                }
              } else {
                log.error("Failed to send message", event.exception());
              }
            }
          });
        }
        //also send locally
        receiveMessage(message.copy());
      }
    } finally {
      // Reset the context id - send can cause messages to be delivered in different contexts so the context id
      // of the current thread can change
      if (contextID != null) {
        VertxInternal.instance.setContextID(contextID);
      }
    }
  }

  /**
   * Send a message on the event bus
   * @param message
   */
  public void send(final Message message) {
    send(message, null);
  }

  /**
   * Register a handler.
   * @param address The address to register for. Any messages sent to that address will be
   * received by the handler. A single handler can be registered against many addresses.
   * @param handler The handler
   * @param completionHandler  Optional completion handler. If specified, then when the subscription information has been
   * propagated to all nodes of the event bus, the handler will be called.
   */
  public void registerHandler(String address, Handler<Message> handler, CompletionHandler<Void> completionHandler) {
    registerHandler(address, handler, completionHandler, false);
  }

  /**
   * Registers handler
   *
   * The same as {@link #registerHandler(String, Handler, CompletionHandler)} with a null completionHandler
   */
  public void registerHandler(String address, Handler<Message> handler) {
    registerHandler(address, handler, null);
  }


  /**
   * Unregisters a handler
   * @param address The address the handler was registered to
   * @param handler The handler
   * @param completionHandler Optional completion handler. If specified, then when the subscription information has been
   * propagated to all nodes of the event bus, the handler will be called.
   */
  public void unregisterHandler(String address, Handler<Message> handler, CompletionHandler<Void> completionHandler) {
    Set<HandlerHolder> set = handlers.get(address);
    if (set != null) {
      set.remove(new HandlerHolder(handler, false));
      if (set.isEmpty()) {
        handlers.remove(address);
        if (subs != null) {
          removeSub(address, serverID, completionHandler);
        } else if (completionHandler != null) {
          callCompletionHandler(completionHandler);
        }
      } else if (completionHandler != null) {
        callCompletionHandler(completionHandler);
      }
    }
  }

  /**
   * Unregisters a handler
   * @param address The address the handler was registered to
   * @param handler The handler
   */
  public void unregisterHandler(String address, Handler<Message> handler) {
    unregisterHandler(address, handler, null);
  }

  protected void close(Handler<Void> doneHandler) {
    server.close(doneHandler);
  }

  private void registerHandler(String address, Handler<Message> handler, CompletionHandler<Void> completionHandler,
                               boolean replyHandler) {
    Set<HandlerHolder> set = handlers.get(address);
    if (set == null) {
      set = new ConcurrentHashSet<>();
      Set<HandlerHolder> prevSet = handlers.putIfAbsent(address, set);
      if (prevSet != null) {
        set = prevSet;
      }
      if (completionHandler == null) {
        completionHandler = new CompletionHandler<Void>() {
          public void handle(Future<Void> event) {
            if (event.failed()) {
              log.error("Failed to remove entry", event.exception());
            }
          }
        };
      }
      if (subs != null && !replyHandler) {
        // Propagate the information
        subs.put(address, serverID, completionHandler);
      } else {
        if (completionHandler != null) {
          callCompletionHandler(completionHandler);
        }
      }
      set.add(new HandlerHolder(handler, replyHandler));
    } else {
      set.add(new HandlerHolder(handler, replyHandler));
      if (completionHandler != null) {
        callCompletionHandler(completionHandler);
      }
    }
  }

  private void callCompletionHandler(CompletionHandler<Void> completionHandler) {
    SimpleFuture<Void> f = new SimpleFuture<>();
    f.setResult(null);
    completionHandler.handle(f);
  }

  private void send(final ServerID serverID, final Sendable sendable) {
    //We need to deal with the fact that connecting can take some time and is async, and we cannot
    //block to wait for it. So we add any sends to a pending list if not connected yet.
    //Once we connect we send them.
    ConnectionHolder holder = connections.get(serverID);
    if (holder == null) {
      NetClient client = new NetClient();
      holder = new ConnectionHolder(client);
      ConnectionHolder prevHolder = connections.putIfAbsent(serverID, holder);
      if (prevHolder != null) {
        holder = prevHolder;
      }
      holder.pending.add(sendable);
      final ConnectionHolder fholder = holder;
      client.connect(serverID.port, serverID.host, new Handler<NetSocket>() {
        public void handle(NetSocket socket) {
          fholder.socket = socket;
          for (Sendable sendable : fholder.pending) {
            sendable.write(socket);
          }
          fholder.connected = true;
        }
      });
      client.exceptionHandler(new Handler<Exception>() {
        public void handle(Exception e) {
          log.debug("Cluster connection failed. Removing it from map");
          connections.remove(serverID);
          if (subs != null && sendable.type() == Sendable.TYPE_MESSAGE) {
            removeSub(((Message)sendable).address, serverID, null);
          }
        }
      });
    } else {
      if (holder.connected) {
        sendable.write(holder.socket);
      } else {
        holder.pending.add(sendable);
      }
    }
  }

  private void removeSub(String subName, ServerID serverID, final CompletionHandler<Void> completionHandler) {
   subs.remove(subName, serverID, new CompletionHandler<Boolean>() {
      public void handle(Future<Boolean> event) {
        if (completionHandler != null) {
          SimpleFuture<Void> f = new SimpleFuture<>();
          if (event.failed()) {
            f.setException(event.exception());
          } else {
            f.setResult(null);
          }
          completionHandler.handle(f);
        } else {
          if (event.failed()) {
            log.error("Failed to remove subscription", event.exception());
          }
        }
      }
    });
  }

  // Called when a message is incoming
  private void receiveMessage(final Message msg) {
    if (msg.replyAddress != null) {
      replyAddressCache.put(msg.replyAddress, msg.sender);
    }
    msg.bus = this;
    final Set<HandlerHolder> set = handlers.get(msg.address);
    if (set != null) {
      boolean replyHandler = false;
      for (final HandlerHolder holder: set) {
        if (holder.replyHandler) {
          replyHandler = true;
        }
        VertxInternal.instance.executeOnContext(holder.contextID, new Runnable() {
          public void run() {
            try {
              // Need to check handler is still there - the handler might have been removed after the message were sent but
              // before it was received
              if (set.contains(holder)) {
                VertxInternal.instance.setContextID(holder.contextID);
                holder.handler.handle(msg);
              }
            } catch (Throwable t) {
              log.error("Unhandled exception in event bus handler", t);
            }
          }
        });
      }
      if (replyHandler) {
        handlers.remove(msg.address);
      }
    }
  }

  private class HandlerHolder {
    final long contextID;
    final Handler<Message> handler;
    final boolean replyHandler;

    private HandlerHolder(Handler<Message> handler, boolean replyHandler) {
      this.contextID = Vertx.instance.getContextID();
      this.handler = handler;
      this.replyHandler = replyHandler;
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

  private static class ConnectionHolder {
    final NetClient client;
    NetSocket socket;
    final List<Sendable> pending = new ArrayList<>();
    boolean connected;

    private ConnectionHolder(NetClient client) {
      this.client = client;
    }
  }
}

