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
package io.vertx.tests.http;

import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests of the HTTP/2 server strict thread mode, in which streams skip the outbound message queue.
 *
 * @see io.vertx.core.http.HttpServerOptions#setStrictThreadMode(boolean)
 */
public class Http2StrictThreadModeTest extends Http2TestBase {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return super.createBaseServerOptions().setStrictThreadMode(true);
  }

  /**
   * The direct queue is selected, the first write of a response is therefore rejected when it does not
   * happen on the event-loop the stream is bound to.
   */
  @Test
  public void testForeignThreadHeadersWriteRejected() throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    server.requestHandler(req -> {
      Context ctx = vertx.getOrCreateContext();
      HttpServerResponse response = req.response();
      new Thread(() -> {
        try {
          response.end();
        } catch (Throwable e) {
          failure.set(e);
          ctx.runOnContext(v -> response.end());
        }
      }).start();
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req.send().compose(HttpClientResponse::end))
      .await();
    assertTrue("Expected the foreign thread write to be rejected", failure.get() instanceof IllegalStateException);
  }

  /**
   * Same as {@link #testForeignThreadHeadersWriteRejected()} for a write that goes through the queue
   * instead of the first-headers fast path.
   */
  @Test
  public void testForeignThreadDataWriteRejected() throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    server.requestHandler(req -> {
      Context ctx = vertx.getOrCreateContext();
      HttpServerResponse response = req.response().setChunked(true);
      response.write("head").onComplete(onSuccess(v -> {
        new Thread(() -> {
          try {
            response.write("foreign");
          } catch (Throwable e) {
            failure.set(e);
            ctx.runOnContext(v2 -> response.end());
          }
        }).start();
      }));
    });
    startServer(testAddress);
    client.request(requestOptions)
      .compose(req -> req.send().compose(HttpClientResponse::end))
      .await();
    assertTrue("Expected the foreign thread write to be rejected", failure.get() instanceof IllegalStateException);
  }

  /**
   * Negative selection: without strict thread mode the stream keeps its outbound queue and any thread
   * can write to it.
   */
  @Test
  public void testForeignThreadWriteAcceptedWithoutStrictThreadMode() throws Exception {
    server.close().await();
    server = vertx.createHttpServer(createBaseServerOptions().setStrictThreadMode(false));
    testForeignThreadWriteAccepted();
  }

  /**
   * Negative selection: strict thread mode only applies to the event-loop threading model, a server
   * bound to a worker context keeps its outbound queue.
   */
  @Test
  public void testForeignThreadWriteAcceptedWithWorkerThreadingModel() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse response = req.response();
      new Thread(() -> response.end("ok")).start();
    });
    startServer(testAddress, ((VertxInternal) vertx).createWorkerContext());
    Buffer body = client.request(requestOptions)
      .compose(req -> req.send().compose(HttpClientResponse::body))
      .await();
    assertEquals("ok", body.toString());
  }

  private void testForeignThreadWriteAccepted() throws Exception {
    server.requestHandler(req -> {
      HttpServerResponse response = req.response();
      new Thread(() -> response.end("ok")).start();
    });
    startServer(testAddress);
    Buffer body = client.request(requestOptions)
      .compose(req -> req.send().compose(HttpClientResponse::body))
      .await();
    assertEquals("ok", body.toString());
  }

  /**
   * Back-pressure round trip: the stream reports a full write queue when the stream window is exhausted
   * and the drain handler is called once, when the client consumes the response.
   */
  @Test
  public void testWriteQueueDrainedOnce() throws Exception {
    waitFor(2);
    Buffer chunk = Buffer.buffer(TestUtils.randomAlphaString(1024));
    AtomicInteger drains = new AtomicInteger();
    Promise<Void> full = Promise.promise();
    server.requestHandler(req -> {
      HttpServerResponse response = req.response().setChunked(true);
      vertx.setPeriodic(1, timerID -> {
        if (response.writeQueueFull()) {
          vertx.cancelTimer(timerID);
          response.drainHandler(v -> {
            assertEquals(1, drains.incrementAndGet());
            response.end();
            complete();
          });
          full.complete();
        } else {
          response.write(chunk.copy());
        }
      });
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        // Hold the stream window closed until the server sees a full write queue
        resp.pause();
        full.future().onComplete(onSuccess(v -> resp.resume()));
        resp.end().onComplete(onSuccess(v -> complete()));
      }));
    }));
    await();
  }

  /**
   * A stream reset must fail the writes the connection still holds. The direct queue has nothing pending of
   * its own, it relies on the connection failing their promises, which reports a different cause than the
   * {@code STREAM_CLOSED_EXCEPTION} the outbound queue reports: the codec implementation fails them with a
   * Netty {@code Http2Exception.StreamException} and the multiplex implementation with a
   * {@code ClosedChannelException}. This test pins the contract, not the cause.
   */
  @Test
  public void testResetFailsPendingWrite() throws Exception {
    Buffer chunk = Buffer.buffer(TestUtils.randomAlphaString(1024));
    AtomicReference<Throwable> cause = new AtomicReference<>();
    Promise<Void> full = Promise.promise();
    server.requestHandler(req -> {
      HttpServerResponse response = req.response().setChunked(true);
      vertx.setPeriodic(1, timerID -> {
        if (response.writeQueueFull()) {
          vertx.cancelTimer(timerID);
          // This write cannot be sent, it stays in the flow controller until the stream is reset
          response.write(chunk.copy()).onComplete(ar -> {
            assertTrue("Expected the pending write to fail", ar.failed());
            cause.set(ar.cause());
            complete();
          });
          full.complete();
        } else {
          response.write(chunk.copy());
        }
      });
    });
    startServer(testAddress);
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(ar -> {
        if (ar.succeeded()) {
          ar.result().pause();
        }
      });
      full.future().onComplete(onSuccess(v -> req.reset()));
    }));
    await();
    assertNotNull(cause.get());
  }
}
