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
  //private final Map<Handler<Message<JsonObject>>, Handler<Message<Buffer>>> jsonHandlerMap = new ConcurrentHashMap<>();

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

//  public void send(String address, final JsonObject jsonObject, final Handler<Message<JsonObject>> replyHandler) {
//    Handler<Message<Buffer>> rHandler = replyHandler == null ? null : new Handler<Message<Buffer>>() {
//      public void handle(Message<Buffer> message) {
//        replyHandler.handle(new JsonMessage(new JsonObject(message.body.toString()), EventBus.this, message.replyAddress));
//      }
//    };
//    send(address, Buffer.create(jsonObject.encode()), rHandler);
//  }

//  /**
//   * Send a Json message on the event bus
//   * @param address The address to be send it to
//   * @param jsonObject The message
//   */
//  public void send(String address, JsonObject jsonObject) {
//    send(address, jsonObject, null);
//  }

  public void send(String address, JsonObject message, final Handler<Message<JsonObject>> replyHandler) {
    send(new JsonMessage(address, message), replyHandler);
  }
  
  public void send(String address, JsonObject message) {
    send(address, message, null);
  }
  
  public void send(String address, Buffer message, final Handler<Message<Buffer>> replyHandler) {
    send(new BufferMessage(address, message), replyHandler);
  }
  
  public void send(String address, Buffer message) {
    send(address, message, null);
  }
  
  public void send(String address, byte[] message, final Handler<Message<byte[]>> replyHandler) {
    send(new ByteArrayMessage(address, message), replyHandler);
  }
  
  public void send(String address, byte[] message) {
    send(address, message, null);
  }
  
  public void send(String address, String message, final Handler<Message<String>> replyHandler) {
    send(new StringMessage(address, message), replyHandler);
  }
  
  public void send(String address, String message) {
    send(address, message, null);
  }
  
  public void send(String address, Integer message, final Handler<Message<Integer>> replyHandler) {
    send(new IntMessage(address, message), replyHandler);
  }
  
  public void send(String address, Integer message) {
    send(address, message, null);
  }
  
  public void send(String address, Long message, final Handler<Message<Long>> replyHandler) {
    send(new LongMessage(address, message), replyHandler);
  }
  
  public void send(String address, Long message) {
    send(address, message, null);
  }
  
  public void send(String address, Float message, final Handler<Message<Float>> replyHandler) {
    send(new FloatMessage(address, message), replyHandler);
  }
  
  public void send(String address, Float message) {
    send(address, message, null);
  }
  
  public void send(String address, Double message, final Handler<Message<Double>> replyHandler) {
    send(new DoubleMessage(address, message), replyHandler);
  }
  
  public void send(String address, Double message) {
    send(address, message, null);
  }
  
  public void send(String address, Boolean message, final Handler<Message<Boolean>> replyHandler) {
    send(new BooleanMessage(address, message), replyHandler);
  }
  
  public void send(String address, Boolean message) {
    send(address, message, null);
  }
  
  public void send(String address, Short message, final Handler<Message<Short>> replyHandler) {
    send(new ShortMessage(address, message), replyHandler);
  }
  
  public void send(String address, Short message) {
    send(address, message, null);
  }
  
  public void send(String address, Character message, final Handler<Message<Character>> replyHandler) {
    send(new CharacterMessage(address, message), replyHandler);
  }
  
  public void send(String address, Character message) {
    send(address, message, null);
  }
  
  public void send(String address, Byte message, final Handler<Message<Byte>> replyHandler) {
    send(new ByteMessage(address, message), replyHandler);
  }
  
  public void send(String address, Byte message) {
    send(address, message, null);
  }

  private void send(final Message message, final Handler replyHandler) {
    Long contextID = Vertx.instance.getContextID();
    try {
      message.sender = serverID;
      if (replyHandler != null) {
        message.replyAddress = UUID.randomUUID().toString();
        registerHandler(message.replyAddress, replyHandler, null, true);
      }

      // First check if sender is in response address cache - it will be if it's a response
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

//  /**
//   * Send a message on the event bus
//   * @param body
//   */
//  public <T> void send(String address, T body) {
//    send(address, body, null);
//  }

//  /**
//   * Register a handler.
//   * @param address The address to register for. Any messages sent to that address will be
//   * received by the handler. A single handler can be registered against many addresses.
//   * @param handler The handler
//   * @param completionHandler  Optional completion handler. If specified, then when the subscription information has been
//   * propagated to all nodes of the event bus, the handler will be called.
//   */
//  public <T> void registerHandler(String address, Handler<Message<T>> handler, CompletionHandler<Void> completionHandler) {
//    registerHandler(address, handler, completionHandler, false);
//  }
//
//  /**
//   * Registers handler
//   *
//   * The same as {@link #registerHandler(String, Handler, CompletionHandler)} with a null completionHandler
//   */
//  public <T> void registerHandler(String address, Handler<Message<T>> handler) {
//    registerHandler(address, handler, null);
//  }
//
//  /**
//   * Register a handler for Json messages.
//   * @param address The address to register for. Any messages sent to that address will be
//   * received by the handler. A single handler can be registered against many addresses.
//   * @param handler The handler
//   * @param completionHandler  Optional completion handler. If specified, then when the subscription information has been
//   * propagated to all nodes of the event bus, the handler will be called.
//   */
//  public void registerHandler(String address, final Handler<Message<JsonObject>> handler, CompletionHandler<Void> completionHandler) {
//    Handler<Message<Buffer>> mHandler = new Handler<Message<Buffer>>() {
//      public void handle(Message<Buffer> message) {
//        handler.handle(new JsonMessage(new JsonObject(message.body.toString()), EventBus.this, message.replyAddress));
//      }
//    };
//    registerHandler(address, mHandler, completionHandler, false);
//    jsonHandlerMap.put(handler, mHandler);
//  }
//
//  /**
//   * Registers handler
//   *
//   * The same as {@link #registerHandler(String, Handler, CompletionHandler)} with a null completionHandler
//   */
//  public void registerHandler(String address, final Handler<Message<JsonObject>> handler) {
//    registerHandler(address, handler, null);
//  }

  /**
   * Unregisters a handler
   * @param address The address the handler was registered to
   * @param handler The handler
   * @param completionHandler Optional completion handler. If specified, then when the subscription information has been
   * propagated to all nodes of the event bus, the handler will be called.
   */
  public <T> void unregisterHandler(String address, Handler<Message<T>> handler,
                                CompletionHandler<Void> completionHandler) {
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

  public <T> void unregisterHandler(String address, Handler<Message<T>> handler) {
    unregisterHandler(address, handler, null);
  }

//  /**
//   * Unregisters a handler
//   * @param address The address the handler was registered to
//   * @param handler The handler
//   */
//  public <T> void unregisterHandler(String address, Handler<Message<T>> handler) {
//    unregisterHandler(address, handler, null);
//  }
//
//  /**
//   * Unregisters a handler
//   * @param address The address the handler was registered to
//   * @param handler The handler
//   * @param completionHandler Optional completion handler. If specified, then when the subscription information has been
//   * propagated to all nodes of the event bus, the handler will be called.
//   */
//  public void unregisterHandler(String address, Handler<Message<JsonObject>> handler, CompletionHandler<Void> completionHandler) {
//    Handler<Message<Buffer>> mHandler = jsonHandlerMap.remove(handler);
//    if (mHandler != null) {
//      unregisterHandler(address, mHandler, completionHandler);
//    }
//  }

//  /**
//   * Unregisters a handler
//   * @param address The address the handler was registered to
//   * @param handler The handler
//   */
//  public void unregisterHandler(String address, Handler<Message<JsonObject>> handler) {
//    unregisterHandler(address, handler, null);
//  }

  protected void close(Handler<Void> doneHandler) {
    server.close(doneHandler);
  }

  public <T> void registerHandler(String address, Handler<Message<T>> handler,
                               CompletionHandler<Void> completionHandler) {
    registerHandler(address, handler, completionHandler, false);
  }

  public <T> void registerHandler(String address, Handler<Message<T>> handler) {
    registerHandler(address, handler, null, false);
  }

  private <T> void registerHandler(String address, Handler<Message<T>> handler,
                                 CompletionHandler<Void> completionHandler,
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
    final Set<HandlerHolder> set = handlers.get(msg.address);
    if (set != null) {
      boolean replyHandler = false;
      for (final HandlerHolder holder: set) {
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
              if (set.contains(holder)) {
                VertxInternal.instance.setContextID(holder.contextID);
                holder.handler.handle(copied);
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

//  private <T> Message createMessage(String address, T payload) {
//    Message msg;
//    if (payload instanceof String) {
//      msg = new StringMessage(address, (String)payload);
//    } else if (payload instanceof Buffer) {
//      msg =  new BufferMessage(address, (Buffer)payload);
//    } else if (payload instanceof Boolean) {
//      msg =  new BooleanMessage(address, (Boolean)payload);
//    } else if (payload instanceof byte[]) {
//      msg =  new ByteArrayMessage(address, (byte[])payload);
//    } else if (payload instanceof Character) {
//      msg = new CharacterMessage(address, (Character)payload);
//    } else if (payload instanceof Double) {
//      msg = new DoubleMessage(address, (Double)payload);
//    } else if (payload instanceof Float) {
//      msg = new FloatMessage(address, (Float)payload);
//    } else if (payload instanceof Integer) {
//      msg = new IntMessage(address, (Integer)payload);
//    } else if (payload instanceof Long) {
//      msg = new LongMessage(address, (Long)payload);
//    } else if (payload instanceof Short) {
//      msg = new ShortMessage(address, (Short)payload);
//    } else {
//      throw new IllegalArgumentException("Invalid type to send as message: " + payload.getClass());
//    }
//    return msg;
//  }

  private class HandlerHolder {
    final long contextID;
    final Handler handler;
    final boolean replyHandler;

    private HandlerHolder(Handler handler, boolean replyHandler) {
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
}

