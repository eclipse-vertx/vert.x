/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package vertx.tests.core.json;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.testframework.TestClientBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  // The tests

  public void testChangesNotVisibleObject1() {
    JsonObject obj = new JsonObject();
    EventBus eb = vertx.eventBus();
    eb.registerHandler("foo", new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> msg) {
        tu.azzert(!msg.body().containsField("b"));
        tu.testComplete();
      }
    });
    eb.send("foo", obj);
    obj.putString("b", "blurrgg");
  }

  public void testChangesNotVisibleObject2() {
    final JsonObject obj = new JsonObject();
    EventBus eb = vertx.eventBus();
    eb.registerHandler("foo", new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> msg) {
        msg.body().putString("b", "uqwduihwqd");
      }
    });
    eb.send("foo", obj);
    vertx.setTimer(1000, new Handler<Long>() {
      @Override
      public void handle(Long id) {
        tu.azzert(!obj.containsField("b"));
        tu.testComplete();
      }
    });
  }

  public void testChangesNotVisibleObject3() {
    Map<String, Object> map = new HashMap<>();
    final JsonObject obj = new JsonObject(map);
    EventBus eb = vertx.eventBus();
    eb.registerHandler("foo", new Handler<Message<JsonObject>>() {
      @Override
      public void handle(final Message<JsonObject> msg) {
        vertx.setTimer(1000, new Handler<Long>() {
          @Override
          public void handle(Long id) {
            tu.azzert(!msg.body().containsField("b"));
            tu.testComplete();
          }
        });
      }
    });
    eb.send("foo", obj);
    map.put("b", "uhqdihuqwd");
  }

  public void testChangesNotVisibleArray1() {
    JsonArray obj = new JsonArray();
    EventBus eb = vertx.eventBus();
    eb.registerHandler("foo", new Handler<Message<JsonArray>>() {
      @Override
      public void handle(Message<JsonArray> msg) {
        tu.azzert(msg.body().size() == 0);
        tu.testComplete();
      }
    });
    eb.send("foo", obj);
    obj.add("blah");
  }

  public void testChangesNotVisibleArray2() {
    final JsonArray obj = new JsonArray();
    EventBus eb = vertx.eventBus();
    eb.registerHandler("foo", new Handler<Message<JsonArray>>() {
      @Override
      public void handle(Message<JsonArray> msg) {
        msg.body().add("blah");
      }
    });
    eb.send("foo", obj);
    vertx.setTimer(1000, new Handler<Long>() {
      @Override
      public void handle(Long id) {
        tu.azzert(obj.size() == 0);
        tu.testComplete();
      }
    });
  }

  public void testChangesNotVisibleArray3() {
    List<Object> list = new ArrayList<>();
    final JsonArray obj = new JsonArray(list);
    EventBus eb = vertx.eventBus();
    eb.registerHandler("foo", new Handler<Message<JsonArray>>() {
      @Override
      public void handle(final Message<JsonArray> msg) {
        vertx.setTimer(1000, new Handler<Long>() {
          @Override
          public void handle(Long id) {
            tu.azzert(msg.body().size() == 0);
            tu.testComplete();
          }
        });
      }
    });
    eb.send("foo", obj);
    list.add("uhwqdiuh");
  }


}