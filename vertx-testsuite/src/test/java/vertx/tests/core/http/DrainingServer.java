/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
