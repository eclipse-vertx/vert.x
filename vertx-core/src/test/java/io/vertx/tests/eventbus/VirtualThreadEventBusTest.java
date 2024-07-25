/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.eventbus;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class VirtualThreadEventBusTest extends VertxTestBase {

  VertxInternal vertx;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    vertx = (VertxInternal) super.vertx;
  }

  @Test
  public void testEventBus() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    EventBus eb = vertx.eventBus();
    eb.consumer("test-addr", msg -> {
      msg.reply(msg.body());
    });
    vertx.createVirtualThreadContext().runOnContext(v -> {
      Message<String> ret = eb.<String>request("test-addr", "test").await();
      assertEquals("test", ret.body());
      testComplete();
    });
    await();
  }
}
