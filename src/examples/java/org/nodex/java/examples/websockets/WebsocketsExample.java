/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.examples.websockets;

import org.nodex.java.core.Handler;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.http.HttpServer;
import org.nodex.java.core.http.HttpServerRequest;
import org.nodex.java.core.http.Websocket;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 08:05
 */
public class WebsocketsExample extends NodexMain {
  public static void main(String[] args) throws Exception {
    new WebsocketsExample().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    new HttpServer().websocketHandler(new Handler<Websocket>() {
      public void handle(final Websocket ws) {
        if (ws.uri.equals("/myapp")) {
          ws.dataHandler(new Handler<Buffer>() {
            public void handle(Buffer data) {
              ws.writeTextFrame(data.toString()); // Echo it back
            }
          });
        } else {
          //Reject it
          ws.close();
        }
      }
    }).requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        if (req.path.equals("/")) req.response.sendFile("websockets/ws.html"); // Serve the html
      }
    }).listen(8080);
  }
}
