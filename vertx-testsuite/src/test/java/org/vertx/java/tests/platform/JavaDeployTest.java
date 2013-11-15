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

package org.vertx.java.tests.platform;

import org.junit.Test;
import org.vertx.java.testframework.TestBase;
import vertx.tests.AsyncStartClient;
import vertx.tests.MultiThreadedTestClient;
import vertx.tests.TestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaDeployTest extends TestBase {

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
  public void testDeployWithConfig() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testDeployVerticle() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testUndeployVerticle() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testDeployModule() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testUndeployModule() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testDeployNestedModule() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testStarted() throws Exception {
    startApp(AsyncStartClient.class.getName());
    startTest(getMethodName());
  }

  @Test
  public void testMultiThreaded() throws Exception {
    startApp(MultiThreadedTestClient.class.getName());
    startTest(getMethodName());
  }

}
