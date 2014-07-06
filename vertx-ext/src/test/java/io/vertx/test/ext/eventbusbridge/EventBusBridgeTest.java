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

package io.vertx.test.ext.eventbusbridge;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sockjs.SockJSServer;
import io.vertx.test.core.HttpTestBase;
import io.vertx.test.core.VertxTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBusBridgeTest extends VertxTestBase {

  private HttpServer server;

  @Before
  public void before() {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));

    JsonArray permitted = new JsonArray();
    permitted.add(new JsonObject()); // Let everything through

    SockJSServer sockJSServer = SockJSServer.newSockJSServer(vertx, server);
    sockJSServer.bridge(new JsonObject().putString("prefix", "/eventbus"), permitted, permitted);

  }

  @After
  public void after() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    server.close(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);
  }

  @Test
  public void testSimple() {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions());

    server.listen(ar -> {
      assertTrue(ar.succeeded());
      // We use raw websocket transport
      WebSocketConnectOptions options = new WebSocketConnectOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT).setRequestURI("/eventbus/websocket");
      client.connectWebsocket(options, ws -> {

        // Register
        JsonObject msg = new JsonObject().putString("type", "register").putString("address", "someaddress");
        ws.writeTextFrame(msg.encode());

        // Send
        msg = new JsonObject().putString("type", "send").putString("address", "someaddress").putString("body", "hello world");
        ws.writeTextFrame(msg.encode());

        ws.dataHandler(buff -> {
          String str = buff.toString();
          JsonObject received = new JsonObject(str);
          assertEquals("hello world", received.getString("body"));
          testComplete();
        });
      });
    });

    await();
  }
}
