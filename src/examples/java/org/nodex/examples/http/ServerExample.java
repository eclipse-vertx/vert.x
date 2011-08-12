/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.examples.http;

import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerConnectHandler;
import org.nodex.core.http.HttpServerConnection;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.core.http.HttpServerResponse;

public class ServerExample {
  public static void main(String[] args) throws Exception {
    HttpServer server = new HttpServer(new HttpServerConnectHandler() {
      public void onConnect(final HttpServerConnection conn) {
        conn.requestHandler(new HttpRequestHandler() {
          public void onRequest(HttpServerRequest req, HttpServerResponse resp) {
            System.out.println("Got request: " + req.uri);
            System.out.println("Headers are: ");
            for (String key : req.getHeaderNames()) {
              System.out.println(key + ":" + req.getHeader(key));
            }
            resp.putHeader("Content-Type", "text/html; charset=UTF-8");
            resp.write("<html><body><h1>Hello from node.x!</h1></body></html>", "UTF-8").end();
          }
        });
      }
    }).listen(8080);

    System.out.println("Any key to exit");
    System.in.read();

    server.close();
  }
}
