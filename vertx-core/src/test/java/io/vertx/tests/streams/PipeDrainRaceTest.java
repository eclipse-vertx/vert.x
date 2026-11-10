/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.streams;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;
import io.vertx.test.fakestream.FakeStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Reproduces the lost drain event behind OPENIG-11066 (large chunked HTTP/1.1 uploads stalling with the body
 * half-sent) using only Vert.x.
 * <p>
 * {@code PipeImpl#to} does, for every item:
 * <pre>
 *   ws.write(item);
 *   if (ws.writeQueueFull()) {   // (1) sample
 *     src.pause();
 *     ws.drainHandler(h);        // (2) register
 *   }
 * </pre>
 * The drain event is edge-triggered and is dropped when no handler is registered (see
 * {@code HttpClientRequestImpl#handleDrained}). When the source emits from a thread that is <b>not</b> the
 * request event-loop, the event-loop can flush the queue and fire the drain event between (1) and (2): the event
 * is lost, the source stays paused forever and the request is never ended.
 * <p>
 * The interleaving is made deterministic here by a destination that drains right after {@code writeQueueFull()}
 * sampled {@code true}.
 */
public class PipeDrainRaceTest {

  @Test(timeout = 10_000)
  public void testDrainBetweenQueueFullCheckAndHandlerRegistration() {
    List<Buffer> written = new ArrayList<>();
    FakeStream<Buffer> src = new FakeStream<>();
    src.pipeTo(new DrainingBeforeRegistrationWriteStream(written));

    // The queue is full after the 1st item, and drains before the pipe registered its drain handler
    src.emit(Buffer.buffer("1"));
    // The pipe paused the source and waits for a drain event that has already fired and been dropped: this item
    // stays buffered in the source and is never written, i.e. the upload stalls
    src.emit(Buffer.buffer("2"));

    assertEquals("the pipe must resume the source once the destination drained", 2, written.size());
  }

  /**
   * A write stream that is full after each write and which drains right after being sampled as full, i.e. before
   * the caller can register a drain handler. As in {@code HttpClientRequestImpl}, a drain event is dropped when no
   * drain handler is registered.
   */
  private static class DrainingBeforeRegistrationWriteStream implements WriteStream<Buffer> {

    private final List<Buffer> written;
    private Handler<Void> drainHandler;
    private boolean pendingWrite;

    DrainingBeforeRegistrationWriteStream(List<Buffer> written) {
      this.written = written;
    }

    @Override
    public synchronized Future<Void> write(Buffer data) {
      written.add(data);
      pendingWrite = true;
      return Future.succeededFuture();
    }

    @Override
    public synchronized boolean writeQueueFull() {
      if (!pendingWrite) {
        return false;
      }
      pendingWrite = false;
      // The event-loop flushes the queue here: the drain event fires, but no handler is registered yet
      Handler<Void> handler = drainHandler;
      if (handler != null) {
        handler.handle(null);
      }
      return true; // the sampled value is stale by the time the caller uses it
    }

    @Override
    public synchronized DrainingBeforeRegistrationWriteStream drainHandler(Handler<Void> handler) {
      drainHandler = handler;
      return this;
    }

    @Override
    public DrainingBeforeRegistrationWriteStream exceptionHandler(Handler<Throwable> handler) {
      return this;
    }

    @Override
    public DrainingBeforeRegistrationWriteStream setWriteQueueMaxSize(int maxSize) {
      return this;
    }

    @Override
    public Future<Void> end() {
      return Future.succeededFuture();
    }
  }
}
