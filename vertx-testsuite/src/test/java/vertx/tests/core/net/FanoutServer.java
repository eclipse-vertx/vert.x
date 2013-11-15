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

import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.platform.Verticle;
import org.vertx.java.testframework.TestUtils;

import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FanoutServer extends Verticle {

  protected TestUtils tu;

  private NetServer server;

  public void start(final Future<Void> startedResult) {
    tu = new TestUtils(vertx);

    final Set<String> connections = vertx.sharedData().getSet("conns");

    server = vertx.createNetServer();
    server.connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        tu.checkThread();
        connections.add(socket.writeHandlerID());
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            tu.checkThread();
            for (String actorID : connections) {
              vertx.eventBus().publish(actorID, buffer);
            }
          }
        });
        socket.closeHandler(new VoidHandler() {
          public void handle() {
            tu.checkThread();
            connections.remove(socket.writeHandlerID());
          }
        });
      }
    });
    server.listen(1234, new AsyncResultHandler<NetServer>() {
      @Override
      public void handle(AsyncResult<NetServer> ar) {
        if (ar.succeeded()) {
          tu.appReady();
          startedResult.setResult(null);
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
