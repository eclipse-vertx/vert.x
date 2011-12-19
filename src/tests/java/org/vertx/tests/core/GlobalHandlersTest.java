/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.tests.core;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.shareddata.SharedData;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class GlobalHandlersTest extends TestBase {

  private VertxInternal vertx = VertxInternal.instance;

  @Test
  public void testGlobalHandlers() throws Exception {

    final String message = "Hello actor";

    final Map<String, Long> map = SharedData.getMap("foo");

    final CountDownLatch latch1 = new CountDownLatch(1);
    final CountDownLatch latch2 = new CountDownLatch(1);
    final long contextID1 = vertx.createAndAssociateContext();
    vertx.executeOnContext(contextID1, new Runnable() {
      public void run() {
        vertx.setContextID(contextID1);
        long actorID = vertx.registerHandler(new Handler<String>() {
          public void handle(String message) {
            azzert(contextID1 == vertx.getContextID());
            vertx.unregisterHandler(map.get("actorid"));
            latch2.countDown();
          }
        });
        map.put("actorid", actorID);
        latch1.countDown();
      }
    });

    azzert(latch1.await(5, TimeUnit.SECONDS));

    final long contextID2 = vertx.createAndAssociateContext();
    vertx.executeOnContext(contextID2, new Runnable() {
      public void run() {
        vertx.setContextID(contextID2);
        //Send msg to actor
        long actorID = map.get("actorid");
        vertx.<String>sendToHandler(actorID, message);
      }
    });

    azzert(latch2.await(5, TimeUnit.SECONDS));
    vertx.destroyContext(contextID1);
    vertx.destroyContext(contextID2);

    throwAssertions();
  }


  @Test
  public void testActorNoContext() throws Exception {

    try {
      vertx.registerHandler(new Handler<String>() {
        public void handle(String message) {
        }
      });
      azzert(false);
    } catch (IllegalStateException e) {
      //Expected
    }

    throwAssertions();
  }

}
