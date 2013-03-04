package org.vertx.java.tests.ha;

import static junit.framework.Assert.*;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.platform.PlatformLocator;
import org.vertx.java.platform.PlatformManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
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
public class HATest   {

  @Test
  public void testFailover() throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);

    final PlatformManager pm1 = PlatformLocator.factory.createPlatformManager(5155, "localhost");
    PlatformManager pm2 = PlatformLocator.factory.createPlatformManager(5156, "localhost");

    pm2.getVertx().eventBus().registerHandler("hatest", new Handler<Message<String>>() {
      int count;
      public void handle(Message<String> msg) {
        count++;
        // We should receive the message twice - once when deployed and once when redeployed after failover
        if (count == 2) {
          latch.countDown();
        }
      }
    });

    pm1.deployModule("failovermod1", null, 1, true, new Handler<String>() {
      @Override
      public void handle(String depID) {
        assertTrue(depID != null);
        pm1.simulateNodeFailure();
      }
    });

    if (!latch.await(30, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Timed out");
    }

  }
}
