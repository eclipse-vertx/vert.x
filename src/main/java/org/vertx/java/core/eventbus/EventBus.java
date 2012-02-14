package org.vertx.java.core.eventbus;

import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleFuture;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.app.VerticleManager;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.spi.AsyncMultiMap;
import org.vertx.java.core.eventbus.spi.ClusterManager;
import org.vertx.java.core.json.JsonObject;
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
  private final ConcurrentMap<String, Map<HandlerHolder, String>> handlers = new ConcurrentHashMap<>();
  private final Map<String, ServerID> replyAddressCache = new ConcurrentHashMap<>();
  private final Map<String, HandlerInfo> handlersByID = new ConcurrentHashMap<>();

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
              Message received = Message.read(buff);
              receiveMessage(received);
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
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, JsonObject message, final Handler<Message<JsonObject>> replyHandler) {
    send(new JsonMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, JsonObject message) {
    send(address, message, null);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, Buffer message, final Handler<Message<Buffer>> replyHandler) {
    send(new BufferMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, Buffer message) {
    send(address, message, null);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, byte[] message, final Handler<Message<byte[]>> replyHandler) {
    send(new ByteArrayMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, byte[] message) {
    send(address, message, null);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, String message, final Handler<Message<String>> replyHandler) {
    send(new StringMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, String message) {
    send(address, message, null);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, Integer message, final Handler<Message<Integer>> replyHandler) {
    send(new IntMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, Integer message) {
    send(address, message, null);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, Long message, final Handler<Message<Long>> replyHandler) {
    send(new LongMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, Long message) {
    send(address, message, null);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, Float message, final Handler<Message<Float>> replyHandler) {
    send(new FloatMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, Float message) {
    send(address, message, null);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, Double message, final Handler<Message<Double>> replyHandler) {
    send(new DoubleMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, Double message) {
    send(address, message, null);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, Boolean message, final Handler<Message<Boolean>> replyHandler) {
    send(new BooleanMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, Boolean message) {
    send(address, message, null);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, Short message, final Handler<Message<Short>> replyHandler) {
    send(new ShortMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, Short message) {
    send(address, message, null);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, Character message, final Handler<Message<Character>> replyHandler) {
    send(new CharacterMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, Character message) {
    send(address, message, null);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   * @param replyHandler Reply handler will be called when any reply from the recipient is received
   */
  public void send(String address, Byte message, final Handler<Message<Byte>> replyHandler) {
    send(new ByteMessage(address, message), replyHandler);
  }

  /**
   * Send a message
   * @param address The address to send it to
   * @param message The message
   */
  public void send(String address, Byte message) {
    send(address, message, null);
  }

  /**
   * Unregisters a handler
   * @param address The address the handler was registered to
   * @param handler The handler
   * @param completionHandler Optional completion handler. If specified, then when the subscription information has been
   * propagated to all nodes of the event bus, the handler will be called.
   */
  public void unregisterHandler(String address, Handler<? extends Message> handler,
                                CompletionHandler<Void> completionHandler) {
    Map<HandlerHolder, String> map = handlers.get(address);
    if (map != null) {
      String handlerID = map.remove(new HandlerHolder(handler, false));
      if (handlerID != null) {
        handlersByID.remove(handlerID);
      }
      if (map.isEmpty()) {
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
   * Unregister a handler
   * @param address The address the handler was registered aty
   * @param handler The handler
   */
  public void unregisterHandler(String address, Handler<? extends Message> handler) {
    unregisterHandler(address, handler, null);
  }

  /**
   * Unregister a handler given a handler id
   * @param id The handler id
   */
  public void unregisterHandler(String id) {
    unregisterHandler(id, (CompletionHandler<Void>) null);
  }

  /**
   * Unregister a handler given a handler id
   * @param id The handler id
   * @param completionHandler Optional completion handler. If specified, then when the subscription information has been
   * propagated to all nodes of the event bus, the handler will be called.
   */
  public void unregisterHandler(String id, CompletionHandler<Void> completionHandler) {
    HandlerInfo info = handlersByID.get(id);
    if (info != null) {
      unregisterHandler(info.address, info.handler, completionHandler);
    }
  }

  /**
   * Registers a handler against a uniquely generated address, the address is returned as the id
   * @param handler
   * @return The handler id which is the same as the address
   */
  public String registerHandler(Handler<? extends Message> handler) {
    return registerHandler(handler, null);
  }

  /**
   * Registers a handler against a uniquely generated address, the address is returned as the id
   * @param handler
   * @param completionHandler Optional completion handler. If specified, then when the subscription information has been
   * propagated to all nodes of the event bus, the handler will be called.
   * @return The handler id which is the same as the address
   */
  public String registerHandler(Handler<? extends Message> handler,
                               CompletionHandler<Void> completionHandler) {
    return registerHandler(null, handler, completionHandler, false);
  }

  /**
   * Registers a handler against the specified address
   * @param address The address top register it at
   * @param handler The handler
   * @param completionHandler Optional completion handler. If specified, then when the subscription information has been
   * propagated to all nodes of the event bus, the handler will be called.
   * @return The handler id which is the same as the address
   */
  public String registerHandler(String address, Handler<? extends Message> handler,
                               CompletionHandler<Void> completionHandler) {
    return registerHandler(address, handler, completionHandler, false);
  }

  /**
   * Registers a handler against the specified address
   * @param address The address top register it at
   * @param handler The handler
   * @return The handler id which is the same as the address
   */
  public String registerHandler(String address, Handler<? extends Message> handler) {
    return registerHandler(address, handler, null);
  }

  protected void close(Handler<Void> doneHandler) {
    server.close(doneHandler);
  }

  private void send(final Message message, final Handler replyHandler) {

    Long contextID = Vertx.instance.getContextID();
    try {
      message.sender = serverID;
      if (replyHandler != null) {
        message.replyAddress = UUID.randomUUID().toString();
        registerHandler(message.replyAddress, replyHandler, null, true);
      }

      // First check if sender is in response address cache - it will be if it's a reply
      ServerID theServerID = replyAddressCache.remove(message.address);

      if (theServerID != null) {
        // Yes, it's a response to a particular server
        if (!theServerID.equals(this.serverID)) {
          sendRemote(theServerID, message);
        } else {
          receiveMessage(message);
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
                      sendRemote(serverID, message);
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
        receiveMessage(message);
      }
    } finally {
      // Reset the context id - send can cause messages to be delivered in different contexts so the context id
      // of the current thread can change
      if (contextID != null) {
        VertxInternal.instance.setContextID(contextID);
      }
    }
  }

  private String registerHandler(String address, Handler<? extends Message> handler,
                               CompletionHandler<Void> completionHandler,
                               boolean replyHandler) {
    String id = UUID.randomUUID().toString();
    if (address == null) {
      address = id;
    }
    handlersByID.put(id, new HandlerInfo(address, handler));
    Map<HandlerHolder, String> map = handlers.get(address);
    if (map == null) {
      map = new ConcurrentHashMap<>();
      Map<HandlerHolder, String> prevMap = handlers.putIfAbsent(address, map);
      if (prevMap != null) {
        map = prevMap;
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
      map.put(new HandlerHolder(handler, replyHandler), id);
      if (subs != null && !replyHandler) {
        // Propagate the information
        subs.put(address, serverID, completionHandler);
      } else {
        if (completionHandler != null) {
          callCompletionHandler(completionHandler);
        }
      }
    } else {
      map.put(new HandlerHolder(handler, replyHandler), id);
      if (completionHandler != null) {
        callCompletionHandler(completionHandler);
      }
    }
    return id;
  }

  private void callCompletionHandler(CompletionHandler<Void> completionHandler) {
    SimpleFuture<Void> f = new SimpleFuture<>();
    f.setResult(null);
    completionHandler.handle(f);
  }

  private void sendRemote(final ServerID serverID, final Message message) {
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
      holder.pending.add(message);
      final ConnectionHolder fholder = holder;
      client.connect(serverID.port, serverID.host, new Handler<NetSocket>() {
        public void handle(NetSocket socket) {
          fholder.socket = socket;
          for (Message Message : fholder.pending) {
            Message.write(socket);
          }
          fholder.connected = true;
        }
      });
      client.exceptionHandler(new Handler<Exception>() {
        public void handle(Exception e) {
          log.debug("Cluster connection failed. Removing it from map");
          connections.remove(serverID);
          if (subs != null) {
            removeSub(message.address, serverID, null);
          }
        }
      });
    } else {
      if (holder.connected) {
        message.write(holder.socket);
      } else {
        holder.pending.add(message);
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
  private void receiveMessage(Message msg) {
    if (msg.replyAddress != null) {
      replyAddressCache.put(msg.replyAddress, msg.sender);
    }
    msg.bus = this;
    final Map<HandlerHolder, String> map = handlers.get(msg.address);
    if (map != null) {
      boolean replyHandler = false;
      for (final HandlerHolder holder: map.keySet()) {
        if (holder.replyHandler) {
          replyHandler = true;
        }
        // Each handler gets a fresh copy
        final Message copied = msg.copy();

        VertxInternal.instance.executeOnContext(holder.contextID, new Runnable() {
          public void run() {
            try {
              // Need to check handler is still there - the handler might have been removed after the message were sent but
              // before it was received
              if (map.containsKey(holder)) {
                VertxInternal.instance.setContextID(holder.contextID);
                holder.handler.handle(copied);
              }
            } catch (Throwable t) {
              VerticleManager.instance.reportException(t);
            }
          }
        });
      }
      if (replyHandler) {
        handlers.remove(msg.address);
      }
    }
  }

  private static class HandlerHolder {
    final long contextID;
    final Handler handler;
    final boolean replyHandler;

    HandlerHolder(Handler handler, boolean replyHandler) {
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
    final List<Message> pending = new ArrayList<>();
    boolean connected;

    private ConnectionHolder(NetClient client) {
      this.client = client;
    }
  }

  private static class HandlerInfo {
    final String address;
    final Handler<? extends Message> handler;

    private HandlerInfo(String address, Handler<? extends Message> handler) {
      this.address = address;
      this.handler = handler;
    }
  }

}

