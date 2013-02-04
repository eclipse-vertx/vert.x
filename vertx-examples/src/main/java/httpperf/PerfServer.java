package httpperf;

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

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;

public class PerfServer extends Verticle {

  public void start() {
    vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        // Just return OK
        //req.response.end();

        // If you want to serve a real file uncomment this and comment previous line
        //req.response.sendFile("httpperf/foo.html");
        vertx.fileSystem().readFile("httpperf/foo.html", new AsyncResultHandler<Buffer>() {
          public void handle(AsyncResult<Buffer> ar) {
            req.response.putHeader("Content-Length", ar.result.length());
            req.response.putHeader("Content-Type", "text/html");
            req.response.end(ar.result);
          }
        });
      }
    }).listen(8080, "localhost");
  }
}
