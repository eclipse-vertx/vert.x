/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vertx.tests.core.http;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;
import org.vertx.java.testframework.TestUtils;

import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class InstanceCheckServer extends Verticle {

  protected TestUtils tu;

  private HttpServer server;

  private final String id = UUID.randomUUID().toString();

  public void start(final Future<Void> result) {
    tu = new TestUtils(vertx);
    server = vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        tu.checkThread();

         //We add the object id of the server to the set
        vertx.sharedData().getSet("instances").add(id);
        vertx.sharedData().getSet("requests").add(UUID.randomUUID().toString());

        req.response().end();

      }
    });
    server.listen(8080, new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        if (ar.succeeded()) {
          tu.appReady();
          result.setResult(null);
        } else {
          ar.cause().printStackTrace();
        }
      }
    });
  }

  public void stop() {
    server.close(new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> res) {
        tu.checkThread();
        tu.appStopped();
      }
    });
  }

}
