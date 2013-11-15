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

import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.testframework.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DrainingServer extends BaseServer {

  public DrainingServer() {
    super(true);
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      public void handle(final NetSocket sock) {
        tu.checkThread();

        tu.azzert(!sock.writeQueueFull());
        sock.setWriteQueueMaxSize(1000);

        final Buffer buff = TestUtils.generateRandomBuffer(10000);
        //Send data until the buffer is full
        vertx.setPeriodic(1, new Handler<Long>() {
          public void handle(Long id) {
            tu.checkThread();
            sock.write(buff.copy());
            if (sock.writeQueueFull()) {
              vertx.cancelTimer(id);
              sock.drainHandler(new VoidHandler() {
                public void handle() {
                  tu.checkThread();
                  tu.azzert(!sock.writeQueueFull());
                  // End test after a short delay to give the client some time to read the data
                  vertx.setTimer(100, new Handler<Long>() {
                    public void handle(Long id) {
                      tu.testComplete();
                    }
                  });
                }
              });

              // Tell the client to resume
              vertx.eventBus().send("client_resume", "");
            }
          }
        });
      }
    };
  }
}
