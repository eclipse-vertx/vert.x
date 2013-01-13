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

package org.vertx.java.tests.core.buffer;

import org.vertx.java.testframework.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyBufferTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/buffer/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_append_buff() {
    startTest(getMethodName());
  }

  public void test_append_fixnum_1() {
    startTest(getMethodName());
  }

  public void test_append_fixnum_2() {
    startTest(getMethodName());
  }

  public void test_append_fixnum_4() {
    startTest(getMethodName());
  }

  public void test_append_fixnum_8() {
    startTest(getMethodName());
  }

  public void test_append_float_4() {
    startTest(getMethodName());
  }

  public void test_append_float_8() {
    startTest(getMethodName());
  }

  public void test_append_string_1() {
    startTest(getMethodName());
  }

  public void test_append_string_2() {
    startTest(getMethodName());
  }

  public void test_set_fixnum_1() {
    startTest(getMethodName());
  }

  public void test_set_fixnum_2() {
    startTest(getMethodName());
  }

  public void test_set_fixnum_4() {
    startTest(getMethodName());
  }

  public void test_set_fixnum_8() {
    startTest(getMethodName());
  }

  public void test_set_float_4() {
    startTest(getMethodName());
  }

  public void test_set_float_8() {
    startTest(getMethodName());
  }

  public void test_length() {
    startTest(getMethodName());
  }

  public void test_copy() {
    startTest(getMethodName());
  }

  public void test_create() {
    startTest(getMethodName());
  }
}
