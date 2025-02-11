/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.impl.CodecManager;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.spi.metrics.EventBusMetrics;

/**
 * Process event-bus server connections, this connection reads messages, the only data
 * it writes are pong replies.
 */
final class InboundConnection implements Handler<Buffer> {

  private static final Buffer PONG = Buffer.buffer(new byte[]{(byte) 1});

  private final ClusteredEventBus clusteredEventBus;
  private final NetSocket socket;
  private final RecordParser parser;
  private int size = -1;
  private Handler<ClusteredMessage<?, ?>> handler;

  public InboundConnection(ClusteredEventBus clusteredEventBus, NetSocket socket) {
    this.clusteredEventBus = clusteredEventBus;

    RecordParser parser = RecordParser.newFixed(4);
    parser.setOutput(this::decodeMessage);

    this.socket = socket;
    this.parser = parser;
  }

  @Override
  public void handle(Buffer data) {
    parser.handle(data);
  }

  InboundConnection handler(Handler<ClusteredMessage<?, ?>> messageHandler) {
    handler = messageHandler;
    return this;
  }

  private void decodeMessage(Buffer buff) {
    if (size == -1) {
      size = buff.getInt(0);
      parser.fixedSizeMode(size);
    } else {
      ClusteredMessage<?, ?> received = new ClusteredMessage<>(clusteredEventBus);
      received.readFromWire(buff, clusteredEventBus.codecManager());
      parser.fixedSizeMode(4);
      size = -1;
      if (received.hasFailure()) {
        received.internalError();
      } else if (received.codec() == CodecManager.PING_MESSAGE_CODEC) {
        // Just send back pong directly on connection
        socket.write(PONG);
      } else {
        EventBusMetrics<?> metrics = clusteredEventBus.metrics();
        if (metrics != null) {
          metrics.messageRead(received.address(), buff.length());
        }
        handler.handle(received);
      }
    }
  }
}
