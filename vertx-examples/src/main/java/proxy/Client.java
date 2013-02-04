package proxy;

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
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.platform.Verticle;

public class Client extends Verticle {

  public void start() {
    HttpClientRequest req = vertx.createHttpClient().setPort(8080).setHost("localhost").put("/some-url", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse response) {
        response.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            System.out.println("Got response data:" + data);
          }
        });
      }
    });
    //Write a few chunks
    req.setChunked(true);
    for (int i = 0; i < 10; i++) {
      req.write("client-data-chunk-" + i);
    }
    req.end();
  }
}
