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

package org.vertx.java.tests.core.isolation;

import org.junit.Test;
import org.vertx.java.testframework.TestBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaScriptIsolationTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testIsolation() throws Exception {
    int numInstances = 10;
    for (int i = 0; i < numInstances; i++) {
      startApp("core/isolation/test_client.js");
    }
    startApp("core/isolation/test_client.js", numInstances);
    startTest(getMethodName(), false);
    for (int i = 0; i < numInstances * 2; i++) {
      waitTestComplete();
    }
  }


}
