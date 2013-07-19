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
import vertx.tests.core.isolation.TestClient1;
import vertx.tests.core.isolation.TestClient2;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaIsolationTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  // Different verticle *types* should be in different classloaders
  public void testIsolation() throws Exception {
    startApp(TestClient1.class.getName());
    startApp(TestClient2.class.getName());

    startTest(getMethodName(), false);
    for (int i = 0; i < 2; i++) {
      waitTestComplete();
    }
  }


}
