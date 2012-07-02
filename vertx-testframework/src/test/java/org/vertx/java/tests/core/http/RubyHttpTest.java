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

import org.vertx.java.framework.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RubyHttpTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/http/test_client.rb");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void test_get() {
    startTest(getMethodName());
  }

  public void test_get_ssl() {
    startTest(getMethodName());
  }

  public void test_put() {
    startTest(getMethodName());
  }

  public void test_put_ssl() {
    startTest(getMethodName());
  }

  public void test_post() {
    startTest(getMethodName());
  }

  public void test_post_ssl() {
    startTest(getMethodName());
  }

  public void test_head() {
    startTest(getMethodName());
  }

  public void test_head_ssl() {
    startTest(getMethodName());
  }

  public void test_options() {
    startTest(getMethodName());
  }

  public void test_options_ssl() {
    startTest(getMethodName());
  }

  public void test_delete() {
    startTest(getMethodName());
  }

  public void test_delete_ssl() {
    startTest(getMethodName());
  }

  public void test_trace() {
    startTest(getMethodName());
  }

  public void test_trace_ssl() {
    startTest(getMethodName());
  }

  public void test_connect() {
    startTest(getMethodName());
  }

  public void test_connect_ssl() {
    startTest(getMethodName());
  }

  public void test_patch() {
    startTest(getMethodName());
  }

  public void test_patch_ssl() {
    startTest(getMethodName());
  }



  public void test_get_chunked() {
    startTest(getMethodName());
  }

  public void test_get_ssl_chunked() {
    startTest(getMethodName());
  }

  public void test_put_chunked() {
    startTest(getMethodName());
  }

  public void test_put_ssl_chunked() {
    startTest(getMethodName());
  }

  public void test_post_chunked() {
    startTest(getMethodName());
  }

  public void test_post_ssl_chunked() {
    startTest(getMethodName());
  }

  public void test_head_chunked() {
    startTest(getMethodName());
  }

  public void test_head_ssl_chunked() {
    startTest(getMethodName());
  }

  public void test_options_chunked() {
    startTest(getMethodName());
  }

  public void test_options_ssl_chunked() {
    startTest(getMethodName());
  }

  public void test_delete_chunked() {
    startTest(getMethodName());
  }

  public void test_delete_ssl_chunked() {
    startTest(getMethodName());
  }

  public void test_trace_chunked() {
    startTest(getMethodName());
  }

  public void test_trace_ssl_chunked() {
    startTest(getMethodName());
  }

  public void test_connect_chunked() {
    startTest(getMethodName());
  }

  public void test_connect_ssl_chunked() {
    startTest(getMethodName());
  }

  public void test_patch_chunked() {
    startTest(getMethodName());
  }

  public void test_patch_ssl_chunked() {
    startTest(getMethodName());
  }
}
