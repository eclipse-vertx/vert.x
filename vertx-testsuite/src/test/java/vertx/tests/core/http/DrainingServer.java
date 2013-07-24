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

import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;
import org.vertx.java.testframework.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DrainingServer extends Verticle {

  protected TestUtils tu;
  private HttpServer server;

  public void start(final Future<Void> startedResult) {
    tu = new TestUtils(vertx);
    server = vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        tu.checkThread();

        req.response().setChunked(true);

        tu.azzert(!req.response().writeQueueFull());
        req.response().setWriteQueueMaxSize(1000);

        final Buffer buff = TestUtils.generateRandomBuffer(10000);
        //Send data until the buffer is full
        vertx.setPeriodic(1, new Handler<Long>() {
          public void handle(Long id) {
            tu.checkThread();
            req.response().write(buff);
            if (req.response().writeQueueFull()) {
              vertx.cancelTimer(id);
              req.response().drainHandler(new VoidHandler() {
                public void handle() {
                  tu.checkThread();
                  tu.azzert(!req.response().writeQueueFull());
                  tu.testComplete();
                }
              });

              // Tell the client to resume
              vertx.eventBus().send("client_resume", "");
            }
          }
        });
      }
    });
    server.listen(8080, new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        tu.appReady();
        startedResult.setResult(null);
      }
    });
  }

  public void stop() {
    server.close(new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> result) {
        tu.checkThread();
        tu.appStopped();
      }
    });
  }

}
