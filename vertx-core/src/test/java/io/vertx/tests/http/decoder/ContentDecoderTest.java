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
package io.vertx.tests.http.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.FlowController;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.SimpleHttpTest2;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public abstract class ContentDecoderTest extends SimpleHttpTest2 {

  public ContentDecoderTest(HttpConfigurator config) {
    super(config);
  }

  @Test
  public void testContentDecoderLifecycleInvariantEventLoop(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    List<String> events = testContentDecoderLifecycleInvariant(ThreadingModel.EVENT_LOOP, checkpoint1, checkpoint2, false, false);
    assertEquals(List.of("init", "handle", "handleEnd", "handleClose", "future-success"), events);
  }

  @Test
  public void testContentDecoderLifecycleInvariantWorker(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    List<String> events = testContentDecoderLifecycleInvariant(ThreadingModel.WORKER, checkpoint1, checkpoint2, false, false);
    assertEquals(List.of("init", "handle", "handleEnd", "handleClose", "future-success"), events);
  }

  @Test
  public void testContentDecoderSetOnPausedRequestEventLoop(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    List<String> events = testContentDecoderLifecycleInvariant(ThreadingModel.EVENT_LOOP, checkpoint1, checkpoint2, false, true);
    assertEquals(List.of("init", "handle", "handleEnd", "handleClose", "future-success"), events);
  }

  @Test
  public void testContentDecoderSetOnPausedRequestWorker(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    List<String> events = testContentDecoderLifecycleInvariant(ThreadingModel.WORKER, checkpoint1, checkpoint2, false, true);
    assertEquals(List.of("init", "handle", "handleEnd", "handleClose", "future-success"), events);
  }

  @Test
  public void testContentDecoderLifecycleInvariantWhenConnectionClosedEventLoop(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    List<String> events = testContentDecoderLifecycleInvariant(ThreadingModel.EVENT_LOOP, checkpoint1, checkpoint2, true, false);
    assertEquals(List.of("init", "handle", "handleClose(abrupt)", "future-failure"), events);
  }

  @Test
  public void testContentDecoderLifecycleInvariantWhenConnectionClosedWorker(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    List<String> events = testContentDecoderLifecycleInvariant(ThreadingModel.WORKER, checkpoint1, checkpoint2, true, false);
    assertEquals(List.of("init", "handle", "handleClose(abrupt)", "future-failure"), events);
  }

  private List<String> testContentDecoderLifecycleInvariant(ThreadingModel threadingModel, Checkpoint checkpoint1, Checkpoint checkpoint2, boolean fail, boolean paused) throws Exception {

    Buffer expected = Buffer.buffer(TestUtils.randomAlphaString(1024));
    Runnable checkThread;
    switch (threadingModel) {
      case EVENT_LOOP:
        checkThread = () -> assertTrue(Context.isOnEventLoopThread());
        break;
      case WORKER:
        checkThread = () -> assertTrue(Context.isOnWorkerThread());
        break;
      default:
        throw new AssertionError();
    }

    List<String> events = Collections.synchronizedList(new ArrayList<>());
    Buffer received = Buffer.buffer();
    AtomicReference<Throwable> setAfterStart = new AtomicReference<>();
    AtomicReference<String> outcome = new AtomicReference<>();
    server.requestHandler(req -> {
      Context current = Vertx.currentContext();
      ContentDecoder<Void> decoder = new ContentDecoder<>() {
        @Override
        public void init(FlowController flowController) {
          checkThread.run();
          assertSame(current, Vertx.currentContext());
          events.add("init");
        }
        @Override
        public void handle(ByteBuf chunk) {
          checkThread.run();
          assertSame(current, Vertx.currentContext());
          if (!events.get(events.size() - 1).equals("handle")) {
            events.add("handle");
            try {
              ((HttpServerRequestInternal)req).contentDecoder(ContentDecoder.NULL);
            } catch (Throwable t) {
              setAfterStart.set(t);
            }
          }
          received.appendBytes(ByteBufUtil.getBytes(chunk));
          checkpoint2.succeed();
        }
        @Override
        public Void handleEnd() {
          checkThread.run();
          assertSame(current, Vertx.currentContext());
          events.add("handleEnd");
          req.response().end();
          return null;
        }
        @Override
        public void handleClose(Throwable cause) {
          checkThread.run();
          assertSame(current, Vertx.currentContext());
          events.add(cause != null ? "handleClose(abrupt)" : "handleClose");
          vertx.setTimer(100, id -> checkpoint1.succeed());
        }
      };
      Handler<AsyncResult<Void>> outcomeHandler = ar -> {
        checkThread.run();
        assertSame(current, Vertx.currentContext());
        outcome.set(ar.succeeded() ? "future-success" : "future-failure");
      };
      if (paused) {
        req.pause();
        vertx.setTimer(100, id -> {
          ((HttpServerRequestInternal)req).contentDecoder(decoder).onComplete(outcomeHandler);
          assertEquals(List.of("init"), events);
          req.resume();
        });
      } else {
        ((HttpServerRequestInternal)req).contentDecoder(decoder).onComplete(outcomeHandler);
        assertEquals(List.of("init"), events);
      }
    });

    startServer(((VertxInternal)vertx).createContext(threadingModel));

    Duration timeout = Duration.ofSeconds(10);
    HttpClientRequest request = client.request(requestOptions).await(timeout);
    if (fail) {
      request.write(expected).await(timeout);
      checkpoint2.awaitSuccess();
      request.connection().close();
    } else {
      request.send(expected).await(timeout).end().await(timeout);
    }
    checkpoint1.awaitSuccess();
    assertTrue(setAfterStart.get() instanceof IllegalStateException);
    if (!fail) {
      assertEquals(expected, received);
    }
    List<String> result = new ArrayList<>(events);
    result.add(outcome.get());
    return result;
  }
}
