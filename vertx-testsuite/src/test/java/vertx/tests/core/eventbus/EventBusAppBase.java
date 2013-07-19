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

package vertx.tests.core.eventbus;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.eventbus.impl.ClusterManager;
import org.vertx.java.core.eventbus.impl.DefaultEventBus;
import org.vertx.java.core.eventbus.impl.hazelcast.HazelcastClusterManager;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.testframework.TestClientBase;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class EventBusAppBase extends TestClientBase {

  private static final Logger log = LoggerFactory.getLogger(EventBusAppBase.class);

  protected Map<String, Object> data;
  protected DefaultEventBus eb;

  @Override
  public void start(final Future<Void> startedResult) {
    super.start();
    data = vertx.sharedData().getMap("data");
    if (isLocal()) {
      eb = (DefaultEventBus)vertx.eventBus();
      tu.appReady();
      startedResult.setResult(null);
    } else {
      // FIXME - this test is a hack - we shouldn't be creating multiple eventbuses with a single vert.x
      // using private API!!
      VertxInternal vertxi = ((VertxInternal)vertx);
      ClusterManager clusterManager = new HazelcastClusterManager(vertxi);
      eb = new DefaultEventBus(vertxi, 0, "localhost", clusterManager, new AsyncResultHandler<Void>() {
        @Override
        public void handle(AsyncResult<Void> asyncResult) {
          if (asyncResult.succeeded()) {
            tu.appReady();
            startedResult.setResult(null);
          } else {
            startedResult.setFailure(asyncResult.cause());
          }
        }
      });
    }
  }

  @Override
  public void stop() {
    if (!isLocal()) {
      eb.close(new AsyncResultHandler<Void>() {
        public void handle(AsyncResult<Void> result) {
          EventBusAppBase.super.stop();
        }
      });
    } else {
      super.stop();
    }
  }

  protected abstract boolean isLocal();

}
