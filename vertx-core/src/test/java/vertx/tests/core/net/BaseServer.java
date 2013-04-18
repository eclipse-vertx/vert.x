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

package vertx.tests.core.net;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.platform.Verticle;
import org.vertx.java.testframework.TestUtils;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BaseServer extends Verticle {

  protected TestUtils tu;

  private NetServer server;

  private final boolean sendAppReady;

  protected BaseServer(boolean sendAppReady) {
    this.sendAppReady = sendAppReady;
  }

  public void start(final Future<Void> startedResult) {
    tu = new TestUtils(vertx);
    server = vertx.createNetServer();
    server.connectHandler(getConnectHandler());
    Integer port = vertx.sharedData().<String, Integer>getMap("params").get("listenport");
    int p = port == null ? 1234: port;

    server.listen(p, new AsyncResultHandler<NetServer>() {
      @Override
      public void handle(AsyncResult<NetServer> ar) {
        if (ar.succeeded()) {
          if (sendAppReady) {
            tu.appReady();
          }
          startedResult.setResult(null);
        } else {
          ar.cause().printStackTrace();
        }
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

  protected abstract Handler<NetSocket> getConnectHandler();
}
