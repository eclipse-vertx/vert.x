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
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBus {

  private static final Logger log = Logger.getLogger(EventBus.class);

  public static EventBus instance;

  public static void initialize(ServerID serverID, ClusterManager clusterManager) {
    if (instance != null) {
      throw new IllegalStateException("Cannot call initialize more than once");
    }
    instance = new EventBus(serverID, clusterManager);
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

  public void close(Handler<Void> doneHandler) {
    server.close(doneHandler);
  }

  public void send(final Message message, final Handler<Void> receiptHandler) {
    message.messageID = UUID.randomUUID().toString();
    message.sender = serverID;
    if (receiptHandler != null) {
      message.requiresAck = true;
    }

    subs.get(message.subName, new CompletionHandler<Collection<ServerID>>() {
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

  public void send(final Message message) {
    send(message, null);
  }

  public void registerHandler(String subName, Handler<Message> handler, CompletionHandler<Void> doneHandler) {
    Set<HandlerHolder> set = handlers.get(subName);
    if (set == null) {
      set = new HashSet<>();
      Set<HandlerHolder> prevSet = handlers.putIfAbsent(subName, set);
      if (prevSet != null) {
        set = prevSet;
      }
      if (doneHandler == null) {
        doneHandler = new CompletionHandler<Void>() {
          public void handle(Future<Void> event) {
            if (event.failed()) {
              log.error("Failed to remove entry", event.exception());
            }
          }
        };
      }
      subs.put(subName, serverID, doneHandler);
      set.add(new HandlerHolder(handler));
    } else {
      set.add(new HandlerHolder(handler));
      if (doneHandler != null) {
        SimpleFuture<Void> f = new SimpleFuture<>();
        f.setResult(null);
        doneHandler.handle(f);
      }
    }
  }

  public void registerHandler(String subName, Handler<Message> handler) {
    registerHandler(subName, handler, null);
  }

  public void unregisterHandler(String subName, Handler<Message> handler) {
    Set<HandlerHolder> set = handlers.get(subName);
    if (set != null) {
      set.remove(new HandlerHolder(handler));
      if (set.isEmpty()) {
        handlers.remove(subName);
        removeSub(subName, serverID);
      }
    }
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
          log.info("Cluster connection failed. Removing it from map");
          connections.remove(serverID);
          if (sendable.type() == Sendable.TYPE_MESSAGE) {
            removeSub(((Message)sendable).subName, serverID);
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
    Set<HandlerHolder> set = handlers.get(msg.subName);
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

