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
package org.vertx.java.testframework;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class TestClientBase extends Verticle {

  private static final Logger log = LoggerFactory.getLogger(TestClientBase.class);

  protected TestUtils tu;

  private boolean stopped;

  public void start() {
    tu = new TestUtils(vertx);
    tu.registerTests(this);
  }

  public void stop() {
    if (stopped) {
      throw new IllegalStateException("Already stopped");
    }
    tu.unregisterAll();
    tu.appStopped();
    stopped = true;
  }
}
