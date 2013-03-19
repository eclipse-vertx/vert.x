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

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;
import org.vertx.java.testframework.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PausingServer extends Verticle {

  protected TestUtils tu;

  private HttpServer server;

  public void start() {
    tu = new TestUtils(vertx);
    server = vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        tu.checkThread();
        req.response.setChunked(true);
        req.pause();
        final Handler<Message<Buffer>> resumeHandler = new Handler<Message<Buffer>>() {
          public void handle(Message message) {
            tu.checkThread();
            req.resume();
          }
        };
        vertx.eventBus().registerHandler("server_resume", resumeHandler);
        req.endHandler(new SimpleHandler() {
          public void handle() {
            tu.checkThread();
            vertx.eventBus().unregisterHandler("server_resume", resumeHandler);
          }
        });
        req.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            tu.checkThread();
            req.response.write(buffer);
          }
        });
      }
    }).listen(8080);

    tu.appReady();
  }

  public void stop() {
    server.close(new SimpleHandler() {
      public void handle() {
        tu.checkThread();
        tu.appStopped();
      }
    });
  }

}
