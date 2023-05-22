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

import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.impl.MessageImpl;
import io.vertx.core.eventbus.impl.codecs.PingMessageCodec;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.cluster.NodeInfo;
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
  private final String remoteNodeId;
  private final VertxInternal vertx;
  private final EventBusMetrics metrics;

  private Queue<SomeTask> pending;
  private NetSocket socket;
  private boolean connected;
  private long timeoutID = -1;
  private long pingTimeoutID = -1;

  private static class SomeTask {
    final MessageImpl<?, ?> message;
    final Promise<Void> writePromise;
    SomeTask(MessageImpl<?, ?> message, Promise<Void> writePromise) {
      this.message = message;
      this.writePromise = writePromise;
    }
  }

  ConnectionHolder(ClusteredEventBus eventBus, String remoteNodeId) {
    this.eventBus = eventBus;
    this.remoteNodeId = remoteNodeId;
    this.vertx = eventBus.vertx();
    this.metrics = eventBus.getMetrics();
  }

  void connect() {
    Promise<NodeInfo> promise = Promise.promise();
    eventBus.vertx().getClusterManager().getNodeInfo(remoteNodeId, promise);
    promise.future()
      .flatMap(info -> eventBus.client().connect(info.port(), info.host()))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          connected(ar.result());
        } else {
          log.warn("Connecting to server " + remoteNodeId + " failed", ar.cause());
          close(ar.cause());
        }
      });
  }

  // TODO optimise this (contention on monitor)
  synchronized void writeMessage(MessageImpl<?, ?> message, Promise<Void> writePromise) {
    if (connected) {
      Buffer data = ((ClusteredMessage) message).encodeToWire();
      if (metrics != null) {
        metrics.messageWritten(message.address(), data.length());
      }
      socket.write(data).onComplete(writePromise);
    } else {
      if (pending == null) {
        if (log.isDebugEnabled()) {
          log.debug("Not connected to server " + remoteNodeId + " - starting queuing");
        }
        pending = new ArrayDeque<>();
      }
      pending.add(new SomeTask(message, writePromise));
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
      SomeTask msg;
      if (pending != null) {
        while ((msg = pending.poll()) != null) {
          msg.writePromise.tryFail(cause);
        }
      }
    }
    // The holder can be null or different if the target server is restarted with same nodeInfo
    // before the cleanup for the previous one has been processed
    if (eventBus.connections().remove(remoteNodeId, this)) {
      if (log.isDebugEnabled()) {
        log.debug("Cluster connection closed for server " + remoteNodeId);
      }
    }
  }

  private void schedulePing() {
    EventBusOptions options = eventBus.options();
    pingTimeoutID = vertx.setTimer(options.getClusterPingInterval(), id1 -> {
      // If we don't get a pong back in time we close the connection
      timeoutID = vertx.setTimer(options.getClusterPingReplyInterval(), id2 -> {
        // Didn't get pong in time - consider connection dead
        log.warn("No pong from server " + remoteNodeId + " - will consider it dead");
        close();
      });
      ClusteredMessage pingMessage =
        new ClusteredMessage<>(remoteNodeId, PING_ADDRESS, null, null, new PingMessageCodec(), true, eventBus);
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
        log.debug("Draining the queue for server " + remoteNodeId);
      }
      for (SomeTask ctx : pending) {
        Buffer data = ((ClusteredMessage<?, ?>)ctx.message).encodeToWire();
        if (metrics != null) {
          metrics.messageWritten(ctx.message.address(), data.length());
        }
        socket.write(data).onComplete(ctx.writePromise);
      }
    }
    pending = null;
  }
}
