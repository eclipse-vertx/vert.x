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

/**
 * Include tests for modules
 *
 * The actual tests are actually written in JavaScript
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ModuleIncludeTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testSimpleInclude() throws Exception {
    startMod("io.vertx~testmod1-1~1.0");
    startTest(getMethodName());
  }

  @Test
  public void testChainedInclude() throws Exception {
    startMod("io.vertx~testmod2-1~1.0");
    startTest(getMethodName());
  }

  @Test
  public void testMultipleIncludes() throws Exception {
    startMod("io.vertx~testmod3-1~1.0");
    startTest(getMethodName());
  }

  @Test
  public void testCircularInclude() throws Exception {
    startMod("io.vertx~testmod4-1~1.0");
    startTest(getMethodName());
  }

  @Test
  public void testSimpleIncludeJar() throws Exception {
    startMod("io.vertx~testmod5-1~1.0");
    startTest(getMethodName());
  }

  @Test
  public void testChainedIncludeJar() throws Exception {
    startMod("io.vertx~testmod6-1~1.0");
    startTest(getMethodName());
  }

  @Test
  public void testMultipleIncludesJar() throws Exception {
    startMod("io.vertx~testmod7-1~1.0");
    startTest(getMethodName());
  }

  @Test
  public void testNestedIncludesJar() throws Exception {
    startMod("io.vertx~testmod8-1~1.0");
    startTest(getMethodName());
  }

}