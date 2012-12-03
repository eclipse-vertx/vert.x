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

package org.vertx.java.tests.core.eventbus;

import org.vertx.java.framework.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptEventBusTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/eventbus/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testSimple() {
    startTest(getMethodName());
  }

  public void testUnregister() {
    startTest(getMethodName());
  }

  public void testWithReply() {
    startTest(getMethodName());
  }

  public void testEmptyReply() {
    startTest(getMethodName());
  }

  public void testEmptyMessage() {
    startTest(getMethodName());
  }

  public void testEchoString() {
    startTest(getMethodName());
  }

  public void testEchoNumber1() {
    startTest(getMethodName());
  }

  public void testEchoNumber2() {
    startTest(getMethodName());
  }

  public void testEchoBooleanTrue() {
    startTest(getMethodName());
  }

  public void testEchoBooleanFalse() {
    startTest(getMethodName());
  }

  public void testEchoJson() {
    startTest(getMethodName());
  }

  public void testEchoBuffer() {
    startTest(getMethodName());
  }

  public void testEchoNull() {
    startTest(getMethodName());
  }

  public void testReplyOfReplyOfReply() {
    startTest(getMethodName());
  }

}
