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
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Confirms, with a real {@link HttpClientRequest} and a real server, the lost drain event behind OPENIG-11066
 * that {@link PipeDrainRaceTest} models with a fake destination.
 * <p>
 * The producer emits from its own thread, as a body read on another event-loop (a proxy) or a plain thread does.
 * {@code PipeImpl} samples {@code writeQueueFull()}, then pauses the source and registers the drain handler.
 * The request lets the connection event-loop drain in between, which is what a producer thread descheduled
 * between those two calls would observe. The only artificial part is {@link GapBeforeRegistration}, which widens
 * that window deterministically: it returns the real sample, but only after the real request has drained and the
 * drain callback has run (and found no handler). The queue, the connection, the drain dispatch and the server are
 * all real.
 */
public class PipeDrainRaceHttpClientTest {

  private static final int CHUNK_SIZE = 8 * 1024;
  private static final int CHUNKS = 64;
  private static final long UPLOAD_TIMEOUT_SECONDS = 5;

  private Vertx vertx;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown() throws Exception {
    vertx.close().await(10, TimeUnit.SECONDS);
  }

  @Test(timeout = 60_000)
  public void testChunkedUploadCompletesWhenRequestDrainsBeforeHandlerRegistration() throws Exception {
    AtomicLong received = new AtomicLong();
    HttpServer server = vertx
      .createHttpServer(new HttpServerOptions().setPort(0).setHost("localhost"))
      .requestHandler(req -> {
        req.handler(buff -> received.addAndGet(buff.length()));
        req.endHandler(v -> req.response().end("ok"));
      })
      .listen()
      .await(10, TimeUnit.SECONDS);
    // No explicit close of the client and the server: a stalled upload would block it, tearDown closes everything
    HttpClient client = vertx.createHttpClient();
    ForeignThreadSource src = new ForeignThreadSource(CHUNKS);
    GapBeforeRegistration[] dst = new GapBeforeRegistration[1];
    Future<HttpClientResponse> response = client
      .request(HttpMethod.POST, server.actualPort(), "localhost", "/upload")
      .compose(req -> {
        req.setChunked(true);
        // An 8 KiB chunk makes the queue "full" (high water mark), and its flush makes it writable again (the
        // low water mark is size / 2: a size of 1 would make it 0 and the queue would never become writable)
        req.setWriteQueueMaxSize(4 * 1024);
        dst[0] = new GapBeforeRegistration(req);
        src.pipeTo(dst[0]);
        return req.response();
      });
    try {
      assertEquals(200, response.await(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS).statusCode());
    } catch (TimeoutException e) {
      fail("upload stalled: source emitted " + src.emitted.get() + "/" + CHUNKS + " chunks and is paused="
        + src.isPaused() + ", server received " + received.get() + " bytes, no end of body"
        + " (stale writeQueueFull samples: " + dst[0].staleSamples.get() + ")");
    }
    assertTrue(received.get() >= (long) CHUNKS * CHUNK_SIZE);
  }

  /**
   * Delegates to a real {@link HttpClientRequest}. {@link #writeQueueFull()} returns the real sample; when it is
   * {@code true} it first waits for the real request to drain and for the drain callback to run, so that the
   * value the pipe acts on is stale, and no drain handler is registered yet when the drain event fires.
   */
  private static class GapBeforeRegistration implements WriteStream<Buffer> {

    final AtomicInteger staleSamples = new AtomicInteger();
    private final HttpClientRequest request;

    GapBeforeRegistration(HttpClientRequest request) {
      this.request = request;
    }

    @Override
    public boolean writeQueueFull() {
      boolean sample = request.writeQueueFull();
      if (sample) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (request.writeQueueFull() && System.nanoTime() < deadline) {
          Thread.onSpinWait();
        }
        if (!request.writeQueueFull()) {
          staleSamples.incrementAndGet();
          try {
            Thread.sleep(100); // let the event-loop run the drain callback while no handler is registered
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }
      return sample;
    }

    @Override
    public Future<Void> write(Buffer data) {
      return request.write(data);
    }

    @Override
    public Future<Void> end() {
      return request.end();
    }

    @Override
    public GapBeforeRegistration drainHandler(Handler<Void> handler) {
      request.drainHandler(handler);
      return this;
    }

    @Override
    public GapBeforeRegistration exceptionHandler(Handler<Throwable> handler) {
      request.exceptionHandler(handler);
      return this;
    }

    @Override
    public GapBeforeRegistration setWriteQueueMaxSize(int maxSize) {
      request.setWriteQueueMaxSize(maxSize);
      return this;
    }
  }

  /** Honors pause/resume but emits from a plain thread, never from an event-loop. */
  private static class ForeignThreadSource implements ReadStream<Buffer> {

    final AtomicInteger emitted = new AtomicInteger();
    private final int chunks;
    private final Object lock = new Object();
    private boolean paused = true;
    private boolean started;
    private Handler<Buffer> handler;
    private Handler<Void> endHandler;

    ForeignThreadSource(int chunks) {
      this.chunks = chunks;
    }

    boolean isPaused() {
      synchronized (lock) {
        return paused;
      }
    }

    @Override
    public ForeignThreadSource handler(Handler<Buffer> h) {
      synchronized (lock) {
        handler = h;
      }
      return this;
    }

    @Override
    public ForeignThreadSource endHandler(Handler<Void> h) {
      synchronized (lock) {
        endHandler = h;
      }
      return this;
    }

    @Override
    public ForeignThreadSource exceptionHandler(Handler<Throwable> h) {
      return this;
    }

    @Override
    public ForeignThreadSource pause() {
      synchronized (lock) {
        paused = true;
      }
      return this;
    }

    @Override
    public ForeignThreadSource resume() {
      synchronized (lock) {
        paused = false;
        lock.notifyAll();
        if (!started) {
          started = true;
          Thread t = new Thread(this::run, "foreign-producer");
          t.setDaemon(true);
          t.start();
        }
      }
      return this;
    }

    @Override
    public ForeignThreadSource fetch(long amount) {
      return resume();
    }

    private void run() {
      Buffer chunk = Buffer.buffer(new byte[CHUNK_SIZE]);
      try {
        for (int i = 0; i < chunks; i++) {
          Handler<Buffer> h;
          synchronized (lock) {
            while (paused) {
              lock.wait();
            }
            h = handler;
          }
          if (h == null) {
            return; // the pipe was closed
          }
          emitted.incrementAndGet();
          h.handle(chunk);
        }
        Handler<Void> end;
        synchronized (lock) {
          end = endHandler;
        }
        if (end != null) {
          end.handle(null);
        }
      } catch (InterruptedException ignore) {
      }
    }
  }
}
