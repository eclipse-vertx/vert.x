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

package io.vertx.core.eventbus;

import io.vertx.core.json.JsonObject;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

/**
 * @author Tom Fingerman 03/07/2019.
 */
public class EventBusMessageExceptionTest extends VertxTestBase {

  @Test
  public void testMessageExceptionHandler() {
    vertx.messageExceptionHandler(event ->
      event.message().fail(500, event.exception().getMessage()));

    String unexpectedExceptionAddress = "unexpected-exception-address";
    String errorMessage = "the-exception-should-be-this!";

    vertx.eventBus().consumer(unexpectedExceptionAddress, message -> {
      throw new RuntimeException(errorMessage);
    });

    vertx.eventBus().request(unexpectedExceptionAddress, new JsonObject(), message -> {
      if (message.failed()) {
        assertEquals(errorMessage, message.cause().getMessage());
        testComplete();
      } else {
        fail("The message should have failed with exception");
      }
    });

    await();
  }

}
