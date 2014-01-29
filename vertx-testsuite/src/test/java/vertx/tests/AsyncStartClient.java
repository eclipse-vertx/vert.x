/*
 * Copyright (c) 2011-2013 Red Hat Inc.
 * -----------------------------------
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
import org.vertx.java.core.Future;
import org.vertx.java.testframework.TestClientBase;

public class AsyncStartClient extends TestClientBase {

  @Override
  public void start(final Future<Void> startedResult) {
    super.start();
    container.deployVerticle(AsyncStartChildVerticle.class.getName(), new AsyncResultHandler<String>() {
      public void handle(AsyncResult<String> res) {
        if (res.succeeded()) {
          startedResult.setResult(null);
          tu.appReady();
        }
      }
    });
  }

  public void testStarted() {
    tu.azzert(vertx.sharedData().getMap("mymap").get("childstarted").equals("true"));
    tu.testComplete();
  }
}
