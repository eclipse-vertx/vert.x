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

package io.vertx.it.jackson;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.VertxTestBase2;
import org.junit.Test;

import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTP_HOST;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTP_PORT;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JacksonAbsenceTest extends VertxTestBase2 {

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
    vertx.createHttpServer().requestHandler(req -> {
        req.response().end("hello");
      }).listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST)
      .await();
    HttpClient client = vertx.createHttpClient();
    Buffer body = client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body))
      .await();
    assertEquals("hello", body.toString());
  }

  @Test
  public void testEventBus() {
    EventBus eb = vertx.eventBus();
    eb.consumer("the-address", msg -> {
      assertEquals("ping", msg.body());
      msg.reply("pong");
    });
    Message<?> resp = eb.request("the-address", "ping")
      .await();
    assertEquals("pong", resp.body());
  }
}
