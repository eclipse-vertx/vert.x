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

package io.vertx.it;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpTestBase;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Test;

import static io.vertx.core.http.HttpTestBase.DEFAULT_HTTP_HOST;
import static io.vertx.core.http.HttpTestBase.DEFAULT_HTTP_PORT;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JsonTest extends VertxTestBase {

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
      }).listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(s -> {
        HttpClient client = vertx.createHttpClient();
        client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", onSuccess(resp -> {
          resp.exceptionHandler(this::fail);
          resp.bodyHandler(body -> {
            assertEquals("hello", body.toString());
            testComplete();
          });
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
      eb.request("the-address", "ping", onSuccess(resp -> {
        assertEquals("pong", resp.body());
        testComplete();
      }));
      await();
    } finally {
      vertx.close();
    }
  }
}
