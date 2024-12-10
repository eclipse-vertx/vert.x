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

package io.vertx.it.json;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTP_HOST;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTP_PORT;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NoJacksonTest extends VertxTestBase {

  @Test
  public void testJsonObject() {
    JsonObject obj = new JsonObject();
    obj.put("foo", "bar");
    try {
      obj.toString();
      fail();
    } catch (NoClassDefFoundError ignore) {
    }
    assertTrue(obj.containsKey("foo"));
    assertEquals(obj, obj.copy());
  }

  @Test
  public void testJsonArray() {
    JsonArray array = new JsonArray();
    array.add("foo");
    try {
      array.toString();
      fail();
    } catch (NoClassDefFoundError ignore) {
    }
    assertTrue(array.contains("foo"));
    assertEquals(array, array.copy());
  }

  @Test
  public void testHttp() {
    Vertx vertx = Vertx.vertx();
    try {
      vertx.createHttpServer().requestHandler(req -> {
        req.response().end("hello");
      }).listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).onComplete(onSuccess(s -> {
        HttpClient client = vertx.createHttpClient();
        client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
          .compose(req -> req
            .send()
            .compose(HttpClientResponse::body))
          .onComplete(onSuccess(body -> {
            assertEquals("hello", body.toString());
            testComplete();
        }));
      }));
      await();
    } finally {
      vertx.close();
    }
  }

  @Test
  public void testEventBus() {
    Vertx vertx = Vertx.vertx();
    try {
      EventBus eb = vertx.eventBus();
      eb.consumer("the-address", msg -> {
        assertEquals("ping", msg.body());
        msg.reply("pong");
      });
      eb.request("the-address", "ping").onComplete(onSuccess(resp -> {
        assertEquals("pong", resp.body());
        testComplete();
      }));
      await();
    } finally {
      vertx.close();
    }
  }
}
