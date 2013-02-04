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
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;

public class ProxyServer extends Verticle {

  public void start()  {

    final HttpClient client = vertx.createHttpClient().setHost("localhost").setPort(8282);

    vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        System.out.println("Proxying request: " + req.uri);
        final HttpClientRequest cReq = client.request(req.method, req.uri, new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse cRes) {
            System.out.println("Proxying response: " + cRes.statusCode);
            req.response.statusCode = cRes.statusCode;
            req.response.headers().putAll(cRes.headers());
            req.response.setChunked(true);
            cRes.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                System.out.println("Proxying response body:" + data);
                req.response.write(data);
              }
            });
            cRes.endHandler(new SimpleHandler() {
              public void handle() {
                req.response.end();
              }
            });
          }
        });
        cReq.headers().putAll(req.headers());
        cReq.setChunked(true);
        req.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            System.out.println("Proxying request body:" + data);
            cReq.write(data);
          }
        });
        req.endHandler(new SimpleHandler() {
          public void handle() {
            cReq.end();
          }
        });
      }
    }).listen(8080);
  }
}
