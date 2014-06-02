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

package org.vertx.java.tests.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.sockjs.SockJSServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventBusBridgeTest extends VertxTestBase {

  private HttpServer server;

  @Before
  public void before() {
    server = vertx.createHttpServer();

    JsonArray permitted = new JsonArray();
    permitted.add(new JsonObject()); // Let everything through

    SockJSServer sockJSServer = vertx.createSockJSServer(server);
    sockJSServer.bridge(new JsonObject().putString("prefix", "/eventbus"), permitted, permitted);



  }

  @After
  public void after() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    server.close(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    assertTrue(latch.await(10, TimeUnit.SECONDS));
  }

  @Test
  public void testSimple() {
    HttpClient client = vertx.createHttpClient().setPort(8080);

    server.listen(8080, ar -> {
      assertTrue(ar.succeeded());
      // We use raw websocket transport
      client.connectWebsocket("/eventbus/websocket", ws -> {

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
