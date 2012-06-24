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
public class RubyEventBusTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/eventbus/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_simple_send() {
    startTest(getMethodName());
  }

  public void test_send_empty() {
    startTest(getMethodName());
  }

  public void test_reply() {
    startTest(getMethodName());
  }

  public void test_empty_reply() {
    startTest(getMethodName());
  }

  public void test_send_unregister_send() {
    startTest(getMethodName());
  }

  public void test_send_multiple_matching_handlers() {
    startTest(getMethodName());
  }

  public void test_echo_string() {
    startTest(getMethodName());
  }

  public void test_echo_fixnum() {
    startTest(getMethodName());
  }

  public void test_echo_float() {
    startTest(getMethodName());
  }

  public void test_echo_boolean_true() {
    startTest(getMethodName());
  }

  public void test_echo_boolean_false() {
    startTest(getMethodName());
  }

  public void test_echo_json() {
    startTest(getMethodName());
  }

  public void test_reply_of_reply_of_reply() {
    startTest(getMethodName());
  }

}
