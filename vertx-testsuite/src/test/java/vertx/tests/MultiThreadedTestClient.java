/*
 * Copyright (c) 2011-2013 Red Hat Inc.
 * ------------------------------------
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

package vertx.tests;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.testframework.TestClientBase;

public class MultiThreadedTestClient extends TestClientBase {

  private EventBus eb;

  @Override
  public void start() {
    super.start();
    tu.appReady();
    eb = vertx.eventBus();
  }

  public void testMultiThreaded() {
    container.deployWorkerVerticle(MultiThreadedWorker.class.getName(), null, 1, true, new AsyncResultHandler<String>() {
      @Override
      public void handle(AsyncResult<String> res) {
        tu.checkThread();
        if (res.succeeded()) {
          for (int i = 0; i < 1000; i++) {
            eb.send("fooaddress", "blah");
          }
        }
      }
    });
  }
}
