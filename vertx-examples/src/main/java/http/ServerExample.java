package http;

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

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;

public class ServerExample extends Verticle {

  public void start() {
    vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        System.out.println("Got request: " + req.uri);
        System.out.println("Headers are: ");
        for (String key : req.headers().keySet()) {
          System.out.println(key + ":" + req.headers().get(key));
        }
        req.response.headers().put("Content-Type", "text/html; charset=UTF-8");
        req.response.end("<html><body><h1>Hello from vert.x!</h1></body></html>");
      }
    }).listen(8080);
  }
}
