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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.impl.MessageImpl;
import io.vertx.core.eventbus.impl.codecs.PingMessageCodec;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.spi.metrics.EventBusMetrics;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Connects the event-bus to another event-bus server, this connection write messages, the only data
 * it receives are pong replies.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
final class OutboundConnection implements Handler<Buffer> {

  private static final Logger log = LoggerFactory.getLogger(OutboundConnection.class);

  private static final String PING_ADDRESS = "__vertx_ping";

  private final ClusteredEventBus eventBus;
  private final String remoteNodeId;
  private final VertxInternal vertx;
  private final EventBusMetrics<?> metrics;

  private Queue<MessageWrite> pendingWrites;
  private NetSocket socket;
  private boolean connected;
  private long pingReplyTimeoutID = -1;
  private long pingTimeoutID = -1;

  OutboundConnection(ClusteredEventBus eventBus, String remoteNodeId) {
    this.eventBus = eventBus;
    this.remoteNodeId = remoteNodeId;
    this.vertx = eventBus.vertx();
    this.metrics = eventBus.getMetrics();
  }

  String remoteNodeId() {
    return remoteNodeId;
  }

  synchronized void writeMessage(MessageImpl<?, ?> message, Promise<Void> writePromise) {
    if (connected) {
      writeMessage(message)
        .onComplete(writePromise);
    } else {
      if (pendingWrites == null) {
        if (log.isDebugEnabled()) {
          log.debug("Not connected to server " + remoteNodeId + " - starting queuing");
        }
        pendingWrites = new ArrayDeque<>();
      }
      pendingWrites.add(new MessageWrite(message, writePromise));
    }
  }

  @Override
  public void handle(Buffer event) {
    // Got a pong back
    vertx.cancelTimer(pingReplyTimeoutID);
    schedulePing();
  }

  void handleClose(Throwable cause) {
    if (pingReplyTimeoutID != -1) {
      vertx.cancelTimer(pingReplyTimeoutID);
    }
    if (pingTimeoutID != -1) {
      vertx.cancelTimer(pingTimeoutID);
    }
    synchronized (this) {
      MessageWrite msg;
      if (pendingWrites != null) {
        while ((msg = pendingWrites.poll()) != null) {
          msg.writePromise.tryFail(cause);
        }
      }
    }
  }

  private void schedulePing() {
    EventBusOptions options = eventBus.options();
    pingTimeoutID = vertx.setTimer(options.getClusterPingInterval(), id1 -> {
      // If we don't get a pong back in time we close the connection
      pingReplyTimeoutID = vertx.setTimer(options.getClusterPingReplyInterval(), id2 -> {
        // Didn't get pong in time - consider connection dead
        log.warn("No pong from server " + remoteNodeId + " - will consider it dead");
        socket.close();
      });
      ClusteredMessage<?, ?> pingMessage = new ClusteredMessage<>(
        remoteNodeId,
        PING_ADDRESS,
        null,
        null,
        new PingMessageCodec(),
        true,
        eventBus);
      writeMessage(pingMessage);
    });
  }

  synchronized void connected(NetSocket socket) {
    this.socket = socket;
    this.connected = true;
    // Start a pinger
    schedulePing();
    if (pendingWrites != null) {
      if (log.isDebugEnabled()) {
        log.debug("Draining the queue for server " + remoteNodeId);
      }
      for (MessageWrite ctx : pendingWrites) {
        writeMessage(ctx.message)
          .onComplete(ctx.writePromise);
      }
    }
    pendingWrites = null;
  }

  private Future<Void> writeMessage(MessageImpl<?, ?> message) {
    Buffer data = ((ClusteredMessage<?, ?>)message).encodeToWire();
    if (metrics != null) {
      metrics.messageWritten(message.address(), data.length());
    }
    return socket.write(data);
  }

  private static class MessageWrite {
    final MessageImpl<?, ?> message;
    final Promise<Void> writePromise;
    MessageWrite(MessageImpl<?, ?> message, Promise<Void> writePromise) {
      this.message = message;
      this.writePromise = writePromise;
    }
  }
}
