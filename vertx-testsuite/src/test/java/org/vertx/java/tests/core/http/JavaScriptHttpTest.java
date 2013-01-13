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
public class JavaScriptHttpTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp("core/http/test_client.js");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testGET() {
    startTest(getMethodName());
  }

  public void testGetSSL() {
    startTest(getMethodName());
  }

  public void testPUT() {
    startTest(getMethodName());
  }

  public void testPUTSSL() {
    startTest(getMethodName());
  }

  public void testPOST() {
    startTest(getMethodName());
  }

  public void testPOSTSSL() {
    startTest(getMethodName());
  }

  public void testHEAD() {
    startTest(getMethodName());
  }

  public void testHEADSSL() {
    startTest(getMethodName());
  }

  public void testOPTIONS() {
    startTest(getMethodName());
  }

  public void testOPTIONSSSL() {
    startTest(getMethodName());
  }
  public void testDELETE() {
    startTest(getMethodName());
  }

  public void testDELETESSL() {
    startTest(getMethodName());
  }

  public void testTRACE() {
    startTest(getMethodName());
  }

  public void testTRACESSL() {
   startTest(getMethodName());
  }

  public void testCONNECT() {
    startTest(getMethodName());
  }

  public void testCONNECTSSL() {
    startTest(getMethodName());
  }

  public void testPATCH() {
    startTest(getMethodName());
  }

  public void testPATCHSSL() {
   startTest(getMethodName());
  }




  public void testGETChunked() {
    startTest(getMethodName());
  }

  public void testGetSSLChunked() {
    startTest(getMethodName());
  }

  public void testPUTChunked() {
    startTest(getMethodName());
  }

  public void testPUTSSLChunked() {
    startTest(getMethodName());
  }

  public void testPOSTChunked() {
    startTest(getMethodName());
  }

  public void testPOSTSSLChunked() {
    startTest(getMethodName());
  }

  public void testHEADChunked() {
    startTest(getMethodName());
  }

  public void testHEADSSLChunked() {
    startTest(getMethodName());
  }

  public void testOPTIONSChunked() {
    startTest(getMethodName());
  }

  public void testOPTIONSSSLChunked() {
    startTest(getMethodName());
  }

  public void testDELETEChunked() {
    startTest(getMethodName());
  }

  public void testDELETESSLChunked() {
    startTest(getMethodName());
  }

  public void testTRACEChunked() {
    startTest(getMethodName());
  }

  public void testTRACESSLChunked() {
    startTest(getMethodName());
  }

  public void testCONNECTChunked() {
    startTest(getMethodName());
  }

  public void testCONNECTSSLChunked() {
    startTest(getMethodName());
  }

  public void testPATCHChunked() {
    startTest(getMethodName());
  }

  public void testPATCHSSLChunked() {
    startTest(getMethodName());
  }

}
