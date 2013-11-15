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

package vertx.tests.core.isolation;

import org.vertx.java.testframework.TestClientBase;

import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 * Test that different instances of the same app can't see each other via statics
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient2 extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  private static final AtomicInteger counter = new AtomicInteger(0);

  public void testIsolation() {
    tu.azzert(counter.incrementAndGet() == 1);
    tu.testComplete();
  }

}
