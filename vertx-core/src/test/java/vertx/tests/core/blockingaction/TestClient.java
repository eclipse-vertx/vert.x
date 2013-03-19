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

package vertx.tests.core.blockingaction;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.testframework.TestClientBase;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testBlockingAction() {

    final int numActions = 100;

    class AggHandler {
      int count;
      void complete() {
        if (++count == 2 * numActions) {
          tu.testComplete();
        }
      }
    }

    final AggHandler agg = new AggHandler();

    for (int i = 0; i < numActions; i++) {
      // One that succeeeds
      new BlockingAction<String>((VertxInternal)vertx, new AsyncResultHandler<String>() {
        public void handle(AsyncResult<String> event) {
          tu.azzert(event.succeeded());
          tu.azzert("foo".equals(event.result));
          agg.complete();
        }
      }) {
        public String action() throws Exception {
          return "foo";
        }
      }.run();

      // One that throws an exception
      new BlockingAction<String>((VertxInternal)vertx, new AsyncResultHandler<String>() {
        public void handle(AsyncResult<String> event) {
          tu.azzert(!event.succeeded());
          tu.azzert("Wibble".equals(event.exception.getMessage()));
          agg.complete();
        }
      }) {
        public String action() throws Exception {
          throw new Exception("Wibble");
        }
      }.run();
    }
  }


}
