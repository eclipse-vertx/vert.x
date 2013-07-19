package vertx.tests;/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.testframework.TestClientBase;

public class MultiThreadedTestClient extends TestClientBase {

  private EventBus eb;

  @Override
  public void start() {
    super.start();
    tu.appReady();
    eb = vertx.eventBus();
  }

  public void testMultiThreaded() {
    container.deployWorkerVerticle(MultiThreadedWorker.class.getName(), null, 1, true, new AsyncResultHandler<String>() {
      @Override
      public void handle(AsyncResult<String> res) {
        tu.checkThread();
        if (res.succeeded()) {
          for (int i = 0; i < 1000; i++) {
            eb.send("fooaddress", "blah");
          }
        }
      }
    });
  }
}
