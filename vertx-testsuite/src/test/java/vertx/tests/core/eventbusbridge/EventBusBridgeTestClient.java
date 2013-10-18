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

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */
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
