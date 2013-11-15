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
