/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus;

import io.vertx.core.eventbus.EventBusTestBase.MyPOJO;
import io.vertx.core.eventbus.EventBusTestBase.MyPOJOEncoder2;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;

public class ReplyFailureErrorTest extends VertxTestBase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    startNodes(2);
  }

  @Test
  public void testUnknownCodec() {
    MyPOJOEncoder2 codec = new MyPOJOEncoder2();
    vertices[0].eventBus().registerCodec(codec);
    vertices[1].eventBus().consumer("foo", msg -> {
      fail("Should not receive message");
    }).completionHandler(onSuccess(v -> {
      DeliveryOptions options = new DeliveryOptions().setCodecName(codec.name());
      vertices[0].eventBus().request("foo", new MyPOJO("bar"), options, onFailure(t -> {
        assertThat(t, instanceOf(ReplyException.class));
        ReplyException e = (ReplyException) t;
        assertSame(ReplyFailure.ERROR, e.failureType());
        testComplete();
      }));
    }));
    await();
  }
}
