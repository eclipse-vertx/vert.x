package org.vertx.java.core.cluster;

import org.vertx.java.core.CompletionHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleFuture;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.cluster.spi.AsyncMultiMap;
import org.vertx.java.core.cluster.spi.ClusterManager;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.ServerID;
import org.vertx.java.core.parsetools.RecordParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
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
 * <p>When sending a message, a receipt can be request. If so, when the message has been received by all registered
 * matching handlers and the {@link Message#acknowledge} method has been called on each received message the receipt
 * handler will be called.</p>
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

  private ServerID serverID;
  private NetServer server;
  private AsyncMultiMap<String, ServerID> subs;  // Multimap name -> Collection<node ids>
  private ConcurrentMap<ServerID, ConnectionHolder> connections = new ConcurrentHashMap<>();
  private ConcurrentMap<String, Set<HandlerHolder>> handlers = new ConcurrentHashMap<>();
  private Map<String, ReceiptHandlerHolder> receiptHandlerHolders = new ConcurrentHashMap<>();

  protected EventBus(ServerID serverID, ClusterManager clusterManager) {
    this.serverID = serverID;
    subs = clusterManager.getMultiMap("subs");
    server = new NetServer().connectHandler(new Handler<NetSocket>() {
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
              } else {
                Ack ack = (Ack)received;
                handleAck(ack);
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
   * @param receiptHandler An optional receipt handler. If specified then when the message has reached all registered
   * handlers and each one has called {@link Message#acknowledge} then the handler will be called
   */
  public void send(final Message message, final Handler<Void> receiptHandler) {
    message.messageID = UUID.randomUUID().toString();
    message.sender = serverID;
    if (receiptHandler != null) {
      message.requiresAck = true;
    }

    subs.get(message.address, new CompletionHandler<Collection<ServerID>>() {
      public void handle(Future<Collection<ServerID>> event) {
        Collection<ServerID> serverIDs = event.result();
        if (event.succeeded()) {
          if (serverIDs != null) {
            if (receiptHandler != null) {
              receiptHandlerHolders.put(message.messageID, new ReceiptHandlerHolder(receiptHandler, serverIDs.size()));
            }
            for (ServerID serverID : serverIDs) {
              if (!serverID.equals(EventBus.this.serverID)) {  //We don't send to this node
                send(serverID, message);
              }
            }
          }
          //also send locally
          receiveMessage(message.copy());
        } else {
          log.error("Failed to send message", event.exception());
        }
      }
    });
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
    Set<HandlerHolder> set = handlers.get(address);
    if (set == null) {
      set = new HashSet<>();
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
      subs.put(address, serverID, completionHandler);
      set.add(new HandlerHolder(handler));
    } else {
      set.add(new HandlerHolder(handler));
      if (completionHandler != null) {
        SimpleFuture<Void> f = new SimpleFuture<>();
        f.setResult(null);
        completionHandler.handle(f);
      }
    }
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
   */
  public void unregisterHandler(String address, Handler<Message> handler) {
    Set<HandlerHolder> set = handlers.get(address);
    if (set != null) {
      set.remove(new HandlerHolder(handler));
      if (set.isEmpty()) {
        handlers.remove(address);
        removeSub(address, serverID);
      }
    }
  }

  protected void close(Handler<Void> doneHandler) {
    server.close(doneHandler);
  }

  void acknowledge(ServerID sender, String messageID) {
    Ack ack = new Ack(messageID);
    if (sender.equals(this.serverID)) {
      handleAck(ack);
    } else {
      send(sender, ack);
    }
  }

  private void handleAck(Ack ack) {
    ReceiptHandlerHolder receiptHandlerHolder = receiptHandlerHolders.get(ack.messageID);
    if (receiptHandlerHolder != null) {
      if (--receiptHandlerHolder.count == 0) {
        receiptHandlerHolder.receiptHandler.handle(null);
        receiptHandlerHolders.remove(ack.messageID);
      }
    }
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
          if (sendable.type() == Sendable.TYPE_MESSAGE) {
            removeSub(((Message)sendable).address, serverID);
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

  private void removeSub(String subName, ServerID serverID) {
   subs.remove(subName, serverID, new CompletionHandler<Boolean>() {
      public void handle(Future<Boolean> event) {
        if (event.failed()) {
          log.error("Failed to remove entry", event.exception());
        }
      }
    });
  }

  // Called when a message is incoming
  private void receiveMessage(final Message msg) {
    msg.bus = this;
    Set<HandlerHolder> set = handlers.get(msg.address);
    if (set != null) {
      for (final HandlerHolder holder: set) {
        VertxInternal.instance.executeOnContext(holder.contextID, new Runnable() {
          public void run() {
            VertxInternal.instance.setContextID(holder.contextID);
            holder.handler.handle(msg);
          }
        });
      }
    }
  }

  private class HandlerHolder {
    final long contextID;
    final Handler<Message> handler;

    private HandlerHolder(Handler<Message> handler) {
      this.contextID = Vertx.instance.getContextID();
      this.handler = handler;
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

  private class ReceiptHandlerHolder {
    int count;
    final Handler<Void> receiptHandler;

    private ReceiptHandlerHolder(Handler<Void> receiptHandler, int count) {
      this.receiptHandler = receiptHandler;
      this.count = count;
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

