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
public class RubyRouteMatcherTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/routematcher/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_get_with_pattern() {
    startTest(getMethodName());
  }

  public void test_get_with_regex() {
    startTest(getMethodName());
  }

  public void test_put_with_pattern() {
    startTest(getMethodName());
  }

  public void test_put_with_regex() {
    startTest(getMethodName());
  }

  public void test_post_with_pattern() {
    startTest(getMethodName());
  }

  public void test_post_with_regex() {
    startTest(getMethodName());
  }

  public void test_delete_with_pattern() {
    startTest(getMethodName());
  }

  public void test_delete_with_regex() {
    startTest(getMethodName());
  }

  public void test_options_with_pattern() {
    startTest(getMethodName());
  }

  public void test_options_with_regex() {
    startTest(getMethodName());
  }

  public void test_head_with_pattern() {
    startTest(getMethodName());
  }

  public void test_head_with_regex() {
    startTest(getMethodName());
  }

  public void test_trace_with_pattern() {
    startTest(getMethodName());
  }

  public void test_trace_with_regex() {
    startTest(getMethodName());
  }

  public void test_patch_with_pattern() {
    startTest(getMethodName());
  }

  public void test_patch_with_regex() {
    startTest(getMethodName());
  }

  public void test_connect_with_pattern() {
    startTest(getMethodName());
  }

  public void test_connect_with_regex() {
    startTest(getMethodName());
  }

  public void test_all_with_pattern() {
    startTest(getMethodName());
  }

  public void test_all_with_regex() {
    startTest(getMethodName());
  }

  public void test_route_no_match() {
    startTest(getMethodName());
  }

}
