package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.impl.codecs.PingMessageCodec;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetClientImpl;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.spi.metrics.EventBusMetrics;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ConnectionHolder {

  private static final Logger log = LoggerFactory.getLogger(ConnectionHolder.class);

  private static final String PING_ADDRESS = "__vertx_ping";

  private final ClusteredEventBus eventBus;
  private final NetClient client;
  private final ServerID serverID;
  private final Vertx vertx;
  private final EventBusMetrics metrics;

  private Queue<ClusteredMessage> pending;
  private NetSocket socket;
  private boolean connected;
  private long timeoutID = -1;
  private long pingTimeoutID = -1;

  ConnectionHolder(ClusteredEventBus eventBus, ServerID serverID) {
    this.eventBus = eventBus;
    this.serverID = serverID;
    this.vertx = eventBus.vertx();
    this.metrics = eventBus.getMetrics();
    client = new NetClientImpl(eventBus.vertx(), new NetClientOptions().setConnectTimeout(60 * 1000), false);
  }

  synchronized void connect() {
    if (connected) {
      throw new IllegalStateException("Already connected");
    }
    client.connect(serverID.port, serverID.host, res -> {
      if (res.succeeded()) {
        connected(res.result());
      } else {
        close();
      }
    });
  }

  // TODO optimise this (contention on monitor)
  synchronized void writeMessage(ClusteredMessage message) {
    if (connected) {
      Buffer data = message.encodeToWire();
      metrics.messageWritten(message.address(), data.length());
      socket.write(data);
    } else {
      if (pending == null) {
        pending = new ArrayDeque<>();
      }
      pending.add(message);
    }
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
    if (eventBus.connections().remove(serverID, this)) {
      log.debug("Cluster connection closed: " + serverID + " holder " + this);
    }
  }

  private void schedulePing() {
    VertxOptions options = eventBus.options();
    pingTimeoutID = vertx.setTimer(options.getClusterPingInterval(), id1 -> {
      // If we don't get a pong back in time we close the connection
      timeoutID = vertx.setTimer(options.getClusterPingReplyInterval(), id2 -> {
        // Didn't get pong in time - consider connection dead
        log.warn("No pong from server " + serverID + " - will consider it dead");
        close();
      });
      ClusteredMessage pingMessage =
        new ClusteredMessage<>(serverID, PING_ADDRESS, null, null, null, new PingMessageCodec(), true);
      Buffer data = pingMessage.encodeToWire();
      socket.write(data);
    });
  }

  private synchronized void connected(NetSocket socket) {
    this.socket = socket;
    connected = true;
    socket.exceptionHandler(t -> close());
    socket.closeHandler(v -> close());
    socket.handler(data -> {
      // Got a pong back
      vertx.cancelTimer(timeoutID);
      schedulePing();
    });
    // Start a pinger
    schedulePing();
    for (ClusteredMessage message : pending) {
      Buffer data = message.encodeToWire();
      metrics.messageWritten(message.address(), data.length());
      socket.write(data);
    }
    pending.clear();
  }

}
