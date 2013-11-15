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
import org.vertx.java.core.net.NetSocket;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CloseHandlerServer extends BaseServer {

  protected boolean closeFromServer;

  public CloseHandlerServer() {
    super(true);
    closeFromServer = false;
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      final AtomicInteger counter = new AtomicInteger(0);
      public void handle(final NetSocket sock) {
        tu.checkThread();
        sock.endHandler(new VoidHandler() {
          public void handle() {
            tu.checkThread();
            tu.azzert(counter.incrementAndGet() == 1);
          }
        });
        sock.closeHandler(new VoidHandler() {
          public void handle() {
            tu.checkThread();
            tu.azzert(counter.incrementAndGet() == 2);
            tu.testComplete();
          }
        });
        if (closeFromServer) {
          sock.close();
        }
      }
    };
  }
}
