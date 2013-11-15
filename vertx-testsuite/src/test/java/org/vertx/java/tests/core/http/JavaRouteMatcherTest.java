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

package org.vertx.java.tests.core.http;

import org.vertx.java.testframework.TestBase;
import vertx.tests.core.http.RouteMatcherTestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaRouteMatcherTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(RouteMatcherTestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testRouteWithPattern1GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPattern2GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPattern3GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPattern4GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPattern5GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPattern6GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPattern7GET() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternPUT() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternPOST() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternDELETE() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternHEAD() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternOPTIONS() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternTRACE() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternCONNECT() {
    startTest(getMethodName());
  }

  public void testRouteWithPatternPATCH() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexGET() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexPUT() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexPOST() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexDELETE() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexHEAD() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexOPTIONS() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexTRACE() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexCONNECT() {
    startTest(getMethodName());
  }

  public void testRouteWithRegexPATCH() {
    startTest(getMethodName());
  }

  public void testRouteNoMatchPattern() {
    startTest(getMethodName());
  }

  public void testRouteNoMatchRegex() {
    startTest(getMethodName());
  }
  public void testRouteNoMatchHandlerPattern() {
    startTest(getMethodName());
  }

  public void testRouteNoMatchHandlerRegex() {
    startTest(getMethodName());
  }
}
