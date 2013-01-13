/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
