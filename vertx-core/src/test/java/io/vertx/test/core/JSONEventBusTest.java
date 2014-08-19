/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JSONEventBusTest extends VertxTestBase {

  private EventBus eb;

  public void setUp() throws Exception {
    super.setUp();
    eb = vertx.eventBus();
  }

  @Test
  public void testChangesNotVisibleObject1() {
    JsonObject obj = new JsonObject();
    eb.registerHandler("foo", (Message<JsonObject> msg) -> {
      assertFalse(msg.body().containsField("b"));
      testComplete();
    });
    eb.send("foo", obj);
    obj.putString("b", "blurrgg");
    await();
  }

  @Test
  public void testChangesNotVisibleObject2() {
    final JsonObject obj = new JsonObject();
    eb.registerHandler("foo", (Message<JsonObject> msg) -> {
      msg.body().putString("b", "uqwduihwqd");
    });
    eb.send("foo", obj);
    vertx.setTimer(1000, id -> {
      assertFalse(obj.containsField("b"));
      testComplete();
    });
    await();
  }

  @Test
  public void testChangesNotVisibleObject3() {
    Map<String, Object> map = new HashMap<>();
    final JsonObject obj = new JsonObject(map);
    eb.registerHandler("foo", (Message<JsonObject> msg) -> {
      vertx.setTimer(1000, id -> {
        assertFalse(msg.body().containsField("b"));
        testComplete();
      });
    });
    eb.send("foo", obj);
    map.put("b", "uhqdihuqwd");
    await();
  }

  @Test
  public void testChangesNotVisibleArray1() {
    JsonArray obj = new JsonArray();
    eb.registerHandler("foo", (Message<JsonArray> msg) -> {
      assertEquals(0, msg.body().size());
      testComplete();
    });
    eb.send("foo", obj);
    obj.add("blah");
    await();
  }

  @Test
  public void testChangesNotVisibleArray2() {
    final JsonArray obj = new JsonArray();
    eb.registerHandler("foo", (Message<JsonArray> msg) ->  msg.body().add("blah"));
    eb.send("foo", obj);
    vertx.setTimer(1000, id -> {
      assertEquals(0, obj.size());
      testComplete();
    });
    await();
  }

  @Test
  public void testChangesNotVisibleArray3() {
    List<Object> list = new ArrayList<>();
    final JsonArray obj = new JsonArray(list);
    eb.registerHandler("foo", (Message<JsonArray> msg) -> {
      vertx.setTimer(1000, id -> {
        assertEquals(0, msg.body().size());
        testComplete();
      });
    });
    eb.send("foo", obj);
    list.add("uhwqdiuh");
    await();
  }
}
