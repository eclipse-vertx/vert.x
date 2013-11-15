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

package org.vertx.java.tests.core.isolation;

import org.junit.Test;
import org.vertx.java.testframework.TestBase;
import vertx.tests.core.isolation.TestClient1;
import vertx.tests.core.isolation.TestClient2;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaIsolationTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  // Different verticle *types* should be in different classloaders
  public void testIsolation() throws Exception {
    startApp(TestClient1.class.getName());
    startApp(TestClient2.class.getName());

    startTest(getMethodName(), false);
    for (int i = 0; i < 2; i++) {
      waitTestComplete();
    }
  }


}
