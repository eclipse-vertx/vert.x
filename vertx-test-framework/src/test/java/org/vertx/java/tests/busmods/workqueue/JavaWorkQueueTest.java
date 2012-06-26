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

package org.vertx.java.tests.busmods.workqueue;

import org.junit.Test;
import org.vertx.java.framework.TestBase;
import vertx.tests.busmods.workqueue.OrderProcessor;
import vertx.tests.busmods.workqueue.TestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaWorkQueueTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testSimple() throws Exception {
    start(getMethodName());
  }

  @Test
  public void testWithAcceptedReply() throws Exception {
    start(getMethodName());
  }

  private void start(String methName) throws Exception {
    startApp(TestClient.class.getName());
    int numProcessors = 10;
    for (int i = 0; i < numProcessors; i++) {
      startApp(OrderProcessor.class.getName());
    }
    startTest(methName);
  }

}
