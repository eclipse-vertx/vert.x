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

package org.nodex.java.examples.http;

import org.nodex.java.core.EventHandler;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.http.HttpServer;
import org.nodex.java.core.http.HttpServerRequest;

public class ServerExample extends NodexMain {
  public static void main(String[] args) throws Exception {
    new ServerExample().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    new HttpServer().requestHandler(new EventHandler<HttpServerRequest>() {
      public void onEvent(HttpServerRequest req) {
        System.out.println("Got request: " + req.uri);
        System.out.println("Headers are: ");
        for (String key : req.getHeaderNames()) {
          System.out.println(key + ":" + req.getHeader(key));
        }
        req.response.putHeader("Content-Type", "text/html; charset=UTF-8");
        req.response.setChunked(true);
        req.response.write("<html><body><h1>Hello from node.x!</h1></body></html>", "UTF-8").end();
      }
    }).listen(8080);
  }
}
