/*
 * Copyright 2015 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.core.instrumentation;

import io.vertx.core.eventbus.DeliveryOptions;
import org.junit.Test;

public class EventBusTest extends InstrumentationTestBase {

  @Test
  public void testSendNoHandlers() {
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.eventBus().send("addr", "ping", onFailure(err -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    }));
    cont.suspend();
    await();
  }

  @Test
  public void testReply() {
    TestContinuation cont = instrumentation.continuation();
    vertx.eventBus().consumer("addr", msg -> {
      assertNull(instrumentation.current());
      msg.reply("pong");
    });
    cont.resume();
    vertx.eventBus().send("addr", "ping", onSuccess(reply -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    }));
    cont.suspend();
    await();
  }

  @Test
  public void testReplyFailure() {
    TestContinuation cont = instrumentation.continuation();
    vertx.eventBus().consumer("addr", msg -> {
      msg.fail(0, "pong");
    });
    cont.resume();
    vertx.eventBus().send("addr", "ping", onFailure(reply -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    }));
    cont.suspend();
    await();
  }

  @Test
  public void testSendTimeout() {
    TestContinuation cont = instrumentation.continuation();
    vertx.eventBus().consumer("addr", msg -> {
    });
    cont.resume();
    vertx.eventBus().send("addr", "ping", new DeliveryOptions().setSendTimeout(5), onFailure(reply -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    }));
    cont.suspend();
    await();
  }

  @Test
  public void testSendInterceptor() {
    TestContinuation cont = instrumentation.continuation();
    vertx.eventBus().consumer("addr", msg -> {
    });
    vertx.eventBus().addInterceptor(ctx -> {
      assertSame(cont, instrumentation.current());
      vertx.setTimer(10, id -> {
        ctx.next();
      });
    });
    vertx.eventBus().addInterceptor(ctx -> {
      assertSame(cont, instrumentation.current());
      testComplete();
    });
    cont.resume();
    vertx.eventBus().send("addr", "ping");
    cont.suspend();
    await();
  }

  @Test
  public void testClusterReply() {
    waitFor(2);
    startNodes(2);
    vertices[1].eventBus().consumer("addr", msg -> {
      assertNull(instrumentation.current());
      complete();
    });
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertices[0].eventBus().send("addr", "ping", new DeliveryOptions().setSendTimeout(5), onFailure(reply -> {
      assertNull(instrumentation.current());
      complete();
    }));
    cont.suspend();
    await();
  }
}
