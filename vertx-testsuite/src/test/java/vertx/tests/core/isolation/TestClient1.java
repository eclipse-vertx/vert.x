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

package vertx.tests.core.isolation;

import org.vertx.java.testframework.TestClientBase;

import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 * Test that different instances of the same app can't see each other via statics
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient1 extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  private static final AtomicInteger counter = new AtomicInteger(0);

  public void testIsolation() {
    tu.azzert(counter.incrementAndGet() == 1);
    tu.testComplete();
  }

}
