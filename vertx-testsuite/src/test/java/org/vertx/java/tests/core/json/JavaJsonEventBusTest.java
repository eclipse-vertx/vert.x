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

package org.vertx.java.tests.core.json;

import org.junit.Test;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.testframework.TestBase;
import vertx.tests.core.json.TestClient;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaJsonEventBusTest extends TestBase {

  private static final Logger log = LoggerFactory.getLogger(JavaJsonEventBusTest.class);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(TestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testChangesNotVisibleObject1() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testChangesNotVisibleObject2() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testChangesNotVisibleObject3() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testChangesNotVisibleArray1() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testChangesNotVisibleArray2() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testChangesNotVisibleArray3() throws Exception {
    startTest(getMethodName());
  }

}

