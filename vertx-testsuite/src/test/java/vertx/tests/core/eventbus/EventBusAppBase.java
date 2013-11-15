/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package vertx.tests.core.eventbus;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.eventbus.impl.DefaultEventBus;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.spi.cluster.ClusterManager;
import org.vertx.java.fakecluster.FakeClusterManager;
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
      ClusterManager clusterManager = new FakeClusterManager(vertxi);
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
