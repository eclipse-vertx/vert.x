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
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.streams.Pump;
import org.vertx.java.testframework.TestClientBase;

public class WorkerTestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  /*
  Make sure NetClient and NetServer can be used from worker verticles
   */
  public void testWorker() {

    final NetServer server = vertx.createNetServer();
    server.connectHandler(new Handler<NetSocket>() {
      public void handle(NetSocket socket) {
        Pump p = Pump.createPump(socket, socket);
        p.start();
      }
    });
    server.listen(1234, new AsyncResultHandler<NetServer>() {
      @Override
      public void handle(AsyncResult<NetServer> ar) {
        if (ar.succeeded()) {
          final NetClient client = vertx.createNetClient();
          client.connect(1234, new AsyncResultHandler<NetSocket>() {
            public void handle(AsyncResult<NetSocket> result) {
              if (result.succeeded()) {
                NetSocket socket = result.result();
                socket.dataHandler(new Handler<Buffer>() {
                  public void handle(Buffer data) {
                    server.close(new AsyncResultHandler<Void>() {
                      public void handle(AsyncResult<Void> res) {
                        if (res.succeeded()) {
                          client.close();
                        } else {
                          tu.azzert(false);
                        }
                        tu.testComplete();
                      }
                    });
                  }
                });
                socket.write("foo");
              } else {
                result.cause().printStackTrace();
                tu.azzert(false);
                tu.testComplete();
              }
            }
          });
        } else {
          ar.cause().printStackTrace();
          tu.azzert(false);
          tu.testComplete();
        }
      }
    });
  }
}
