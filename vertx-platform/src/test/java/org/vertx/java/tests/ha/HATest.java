package org.vertx.java.tests.ha;

import static junit.framework.Assert.*;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.PlatformLocator;
import org.vertx.java.platform.PlatformManager;
import org.vertx.java.platform.impl.PlatformManagerInternal;

import java.util.HashMap;
import java.util.Map;
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
  public void testFailover1() throws Exception {
    testFailover(10);
  }

  private void testFailover(int nodes) throws Exception {

    final CountDownLatch latch = new CountDownLatch(nodes);

    final Map<String, PlatformManager> pms = new HashMap<>();

    int portBase = 5155;
    for (int i = 0; i < nodes; i++) {
      PlatformManagerInternal pim = (PlatformManagerInternal)PlatformLocator.factory.createPlatformManager(portBase + i, "localhost");
      pms.put(pim.getNodeID(), pim);
    }

    final Map.Entry<String, PlatformManager> pimEntry = pms.entrySet().iterator().next();
    pimEntry.getValue().getVertx().eventBus().registerHandler("hatest", new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> msg) {
        System.out.println("Received message " + msg.body);
        String nodeID = msg.body.getString("node_id");
        System.out.println("node id is " + nodeID);

        //pimEntry.getValue().simulateNodeFailure();
        System.out.println("Killing node " + nodeID);
        pms.get(nodeID).simulateNodeFailure();
        pms.remove(nodeID);
        latch.countDown();
      }
    });
    pimEntry.getValue().deployModule("failovermod1", null, 1, true, null, new Handler<String>() {
      @Override
      public void handle(String depID) {
        assertTrue(depID != null);
      }
    });

    if (!latch.await(60000, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Timed out");
    }

    assertTrue(pms.isEmpty());
  }
}

