/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.examples.websockets;

import org.vertx.java.core.Handler;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.WebSocketHandler;

public class WebsocketsExample implements Verticle {

  private HttpServer server;

  public void start() {
    server = new HttpServer().websocketHandler(new WebSocketHandler() {
      public void handle(final WebSocket ws) {
        ws.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            ws.writeTextFrame(data.toString()); // Echo it back
          }
        });
      }

      public boolean accept(String path) {
        return path.equals("/myapp");
      }
    }).requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        if (req.path.equals("/")) req.response.sendFile("websockets/ws.html"); // Serve the html
      }
    }).listen(8080);
  }

  public void stop() {
    server.close();
  }
}
