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

package vertx.tests.core.eventbusbridge;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.testframework.TestClientBase;

public class EventBusBridgeTestClient extends TestClientBase {

  private HttpServer server;

  @Override
  public void start() {
    super.start();

    server = vertx.createHttpServer();

    JsonArray permitted = new JsonArray();
    permitted.add(new JsonObject()); // Let everything through

    SockJSServer sockJSServer = vertx.createSockJSServer(server);
    sockJSServer.bridge(new JsonObject().putString("prefix", "/eventbus"), permitted, permitted);

    server.listen(8080, new Handler<AsyncResult<HttpServer>>() {
      @Override
      public void handle(AsyncResult<HttpServer> result) {
        if (result.succeeded()) {
          tu.appReady();
        } else {
          result.cause().printStackTrace();
          tu.azzert(false, "Failed to listen");
        }
      }
    });
  }

  @Override
  public void stop() {
    server.close(new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> result) {
        if (result.succeeded()) {
          EventBusBridgeTestClient.super.stop();
        } else {
          result.cause().printStackTrace();
          tu.azzert(false, "Failed to listen");
        }
      }
    });
  }

  public void testSimple() {
    HttpClient client = vertx.createHttpClient().setPort(8080);
    // We use rawwebsocket transport
    client.connectWebsocket("/eventbus/websocket", new Handler<WebSocket>() {
      @Override
      public void handle(WebSocket websocket) {

        // Register
        JsonObject msg = new JsonObject().putString("type", "register").putString("address", "someaddress");
        websocket.writeTextFrame(msg.encode());

        // Send
        msg = new JsonObject().putString("type", "send").putString("address", "someaddress").putString("body", "hello world");
        websocket.writeTextFrame(msg.encode());

        websocket.dataHandler(new Handler<Buffer>() {
          @Override
          public void handle(Buffer buff) {
            String msg = buff.toString();
            JsonObject received = new JsonObject(msg);
            tu.azzert(received.getString("body").equals("hello world"));
            tu.testComplete();
          }
        });

      }
    });
  }
}
