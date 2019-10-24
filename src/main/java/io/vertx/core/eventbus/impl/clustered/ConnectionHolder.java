/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.impl.OutboundDeliveryContext;
import io.vertx.core.eventbus.impl.codecs.PingMessageCodec;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
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

  private Queue<OutboundDeliveryContext<?>> pending;
  private NetSocket socket;
  private boolean connected;
  private long timeoutID = -1;
  private long pingTimeoutID = -1;

  ConnectionHolder(ClusteredEventBus eventBus, ServerID serverID, EventBusOptions options) {
    this.eventBus = eventBus;
    this.serverID = serverID;
    this.vertx = eventBus.vertx();
    this.metrics = eventBus.getMetrics();
    NetClientOptions clientOptions = new NetClientOptions(options.toJson());
    ClusteredEventBus.setCertOptions(clientOptions, options.getKeyCertOptions());
    ClusteredEventBus.setTrustOptions(clientOptions, options.getTrustOptions());
    client = new NetClientImpl(eventBus.vertx(), clientOptions, false);
  }

  synchronized void connect() {
    if (connected) {
      throw new IllegalStateException("Already connected");
    }
    client.connect(serverID.port, serverID.host, res -> {
      if (res.succeeded()) {
        connected(res.result());
      } else {
        log.warn("Connecting to server " + serverID + " failed", res.cause());
        close(res.cause());
      }
    });
  }

  // TODO optimise this (contention on monitor)
  synchronized void writeMessage(OutboundDeliveryContext<?> ctx) {
    if (connected) {
      Buffer data = ((ClusteredMessage)ctx.message).encodeToWire();
      if (metrics != null) {
        metrics.messageWritten(ctx.message.address(), data.length());
      }
      socket.write(data, ctx);
    } else {
      if (pending == null) {
        if (log.isDebugEnabled()) {
          log.debug("Not connected to server " + serverID + " - starting queuing");
        }
        pending = new ArrayDeque<>();
      }
      pending.add(ctx);
    }
  }

  void close() {
    close(ConnectionBase.CLOSED_EXCEPTION);
  }

  private void close(Throwable cause) {
    if (timeoutID != -1) {
      vertx.cancelTimer(timeoutID);
    }
    if (pingTimeoutID != -1) {
      vertx.cancelTimer(pingTimeoutID);
    }
    synchronized (this) {
      OutboundDeliveryContext<?> msg;
      if (pending != null) {
        while ((msg = pending.poll()) != null) {
          msg.written(cause);
        }
      }
    }
    try {
      client.close();
    } catch (Exception ignore) {
    }
    // The holder can be null or different if the target server is restarted with same serverid
    // before the cleanup for the previous one has been processed
    if (eventBus.connections().remove(serverID, this)) {
      if (log.isDebugEnabled()) {
        log.debug("Cluster connection closed for server " + serverID);
      }
    }
  }

  private void schedulePing() {
    EventBusOptions options = eventBus.options();
    pingTimeoutID = vertx.setTimer(options.getClusterPingInterval(), id1 -> {
      // If we don't get a pong back in time we close the connection
      timeoutID = vertx.setTimer(options.getClusterPingReplyInterval(), id2 -> {
        // Didn't get pong in time - consider connection dead
        log.warn("No pong from server " + serverID + " - will consider it dead");
        close();
      });
      ClusteredMessage pingMessage =
        new ClusteredMessage<>(serverID, PING_ADDRESS, null, null, new PingMessageCodec(), true, eventBus);
      Buffer data = pingMessage.encodeToWire();
      socket.write(data);
    });
  }

  private synchronized void connected(NetSocket socket) {
    this.socket = socket;
    connected = true;
    socket.exceptionHandler(err -> {
      close(err);
    });
    socket.closeHandler(v -> close());
    socket.handler(data -> {
      // Got a pong back
      vertx.cancelTimer(timeoutID);
      schedulePing();
    });
    // Start a pinger
    schedulePing();
    if (pending != null) {
      if (log.isDebugEnabled()) {
        log.debug("Draining the queue for server " + serverID);
      }
      for (OutboundDeliveryContext<?> ctx : pending) {
        Buffer data = ((ClusteredMessage<?, ?>)ctx.message).encodeToWire();
        if (metrics != null) {
          metrics.messageWritten(ctx.message.address(), data.length());
        }
        socket.write(data, ctx);
      }
    }
    pending = null;
  }

}
