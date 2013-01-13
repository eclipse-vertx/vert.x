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

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptRouteMatcherTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/routematcher/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testGetWithPattern() {
    startTest(getMethodName());
  }

  public void testGetWithRegEx() {
  startTest(getMethodName());
  }

  public void testPutWithPattern() {
    startTest(getMethodName());
  }

  public void testPutWithRegEx() {
    startTest(getMethodName());
  }

  public void testPostWithPattern() {
    startTest(getMethodName());
  }

  public void testPostWithRegEx() {
    startTest(getMethodName());
  }

  public void testDeleteWithPattern() {
    startTest(getMethodName());
  }

  public void testDeleteWithRegEx() {
    startTest(getMethodName());
  }

  public void testOptionsWithPattern() {
    startTest(getMethodName());
  }

  public void testOptionsWithRegEx() {
    startTest(getMethodName());
  }

  public void testHeadWithPattern() {
    startTest(getMethodName());
  }

  public void testHeadWithRegEx() {
    startTest(getMethodName());
  }

  public void testTraceWithPattern() {
    startTest(getMethodName());
  }

  public void testTraceWithRegEx() {
    startTest(getMethodName());
  }

  public void testPatchWithPattern() {
    startTest(getMethodName());
  }

  public void testPatchWithRegEx() {
    startTest(getMethodName());
  }

  public void testConnectWithPattern() {
    startTest(getMethodName());
  }

  public void testConnectWithRegEx() {
    startTest(getMethodName());
  }

  public void testAllWithPattern() {
    startTest(getMethodName());
  }

  public void testAllWithRegEx() {
    startTest(getMethodName());
  }

  public void testRouteNoMatch() {
    startTest(getMethodName());
  }
  
  
}
