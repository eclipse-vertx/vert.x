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

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonEventBusTest extends VertxTestBase {

  private EventBus eb;

  public void setUp() throws Exception {
    super.setUp();
    eb = vertx.eventBus();
  }

  @Test
  public void testChangesNotVisibleObject1() {
    JsonObject obj = new JsonObject();
    eb.<JsonObject>consumer("foo").handler((Message<JsonObject> msg) -> {
      assertFalse(msg.body().containsKey("b"));
      testComplete();
    });
    eb.send("foo", obj);
    obj.put("b", "blurrgg");
    await();
  }

  @Test
  public void testChangesNotVisibleObject2() {
    final JsonObject obj = new JsonObject();
    eb.<JsonObject>consumer("foo").handler((Message<JsonObject> msg) -> {
      msg.body().put("b", "uqwduihwqd");
    });
    eb.send("foo", obj);
    vertx.setTimer(1000, id -> {
      assertFalse(obj.containsKey("b"));
      testComplete();
    });
    await();
  }

  @Test
  public void testChangesNotVisibleObject3() {
    Map<String, Object> map = new HashMap<>();
    final JsonObject obj = new JsonObject(map);
    eb.<JsonObject>consumer("foo").handler((Message<JsonObject> msg) -> {
      vertx.setTimer(1000, id -> {
        assertFalse(msg.body().containsKey("b"));
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
    eb.<JsonArray>consumer("foo").handler((Message<JsonArray> msg) -> {
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
    eb.<JsonArray>consumer("foo").handler((Message<JsonArray> msg) ->  msg.body().add("blah"));
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
    eb.<JsonArray>consumer("foo").handler((Message<JsonArray> msg) -> {
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
