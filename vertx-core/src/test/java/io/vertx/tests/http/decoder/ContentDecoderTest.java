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
import io.vertx.core.Context;
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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public abstract class ContentDecoderTest extends SimpleHttpTest2 {

  public ContentDecoderTest(HttpConfigurator config) {
    super(config);
  }

  @Test
  public void testContentDecoderEventLoop(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testContentDecoder(ThreadingModel.EVENT_LOOP, checkpoint1, checkpoint2, false);
  }

  @Test
  public void testContentDecoderWorker(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testContentDecoder(ThreadingModel.WORKER, checkpoint1, checkpoint2, false);
  }

  @Test
  public void testContentDecoderEventLoopFail(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testContentDecoder(ThreadingModel.EVENT_LOOP, checkpoint1, checkpoint2, true);
  }

  @Test
  public void testContentDecoderWorkerFail(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    testContentDecoder(ThreadingModel.WORKER, checkpoint1, checkpoint2, true);
  }

  private void testContentDecoder(ThreadingModel threadingModel, Checkpoint checkpoint1, Checkpoint checkpoint2, boolean fail) throws Exception {

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

    server.requestHandler(req -> {
      Context current = Vertx.currentContext();
      ((HttpServerRequestInternal)req).contentDecoder(new ContentDecoder() {
        @Override
        public void init(FlowController flowController) {
          checkThread.run();
          assertSame(current, Vertx.currentContext());
        }
        @Override
        public void handle(ByteBuf chunk) {
          checkThread.run();
          assertSame(current, Vertx.currentContext());
          checkpoint2.succeed();
        }
        @Override
        public void handleEnd() {
          checkThread.run();
          assertSame(current, Vertx.currentContext());
          req.response().end();
        }
        @Override
        public void handleClose() {
          checkThread.run();
          assertSame(current, Vertx.currentContext());
          checkpoint1.succeed();
        }
      });
    });

    startServer(((VertxInternal)vertx).createContext(threadingModel));

    HttpClientRequest request = client.request(requestOptions).await();
    if (fail) {
      request.write(expected).await();
      checkpoint2.awaitSuccess();
      request.connection().close();
    } else {
      request.send(expected).await().end().await();
    }
  }
}
