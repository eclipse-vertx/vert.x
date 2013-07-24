package vertx.tests.core.http;/*
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
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.testframework.TestClientBase;

public class WorkerHttpTestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  public void testWorker() {
    final HttpServer server = vertx.createHttpServer();
    server.requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        req.response().end();
      }
    });
    server.listen(8080, new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        final HttpClient client = vertx.createHttpClient().setPort(8080);
        client.getNow("some-uri", new Handler<HttpClientResponse>() {
          public void handle(HttpClientResponse resp) {
            server.close(new AsyncResultHandler<Void>() {
              public void handle(AsyncResult<Void> done) {
                client.close();
                tu.testComplete();
              }
            });
          }
        });
      }
    });
  }
}