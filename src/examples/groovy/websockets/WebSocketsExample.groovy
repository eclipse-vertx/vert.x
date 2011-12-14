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

package websockets

import org.vertx.groovy.core.http.HttpServer
import org.vertx.java.core.app.VertxApp

class WebSocketsExample implements VertxApp {

  def server

  void start() {
    server = new HttpServer().websocketHandler { ws ->
      if (ws.uri == "/myapp") {
        ws.dataHandler { data ->
          ws.writeTextFrame(data.toString())
        }
      } else {
        // Reject it
        ws.close()
      }
    }.requestHandler { req ->
      if (req.uri == "/") req.response.sendFile("websockets/ws.html") // Serve the html
    }.listen(8080)
  }

  void stop() {
    server.close()
  }

}
