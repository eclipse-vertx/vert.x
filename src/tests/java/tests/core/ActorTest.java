/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tests.core;

import org.nodex.core.Actor;
import org.nodex.core.NodexInternal;
import org.nodex.core.shared.SharedMap;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ActorTest extends TestBase {

  private NodexInternal nodex = NodexInternal.instance;

  @Test
  public void testActor() throws Exception {

    final String message = "Hello actor";

    final SharedMap<String, Long> map = new SharedMap<>();

    final CountDownLatch latch1 = new CountDownLatch(1);
    final CountDownLatch latch2 = new CountDownLatch(1);
    final long contextID1 = nodex.createAndAssociateContext();
    nodex.executeOnContext(contextID1, new Runnable() {
      public void run() {
        nodex.setContextID(contextID1);
        long actorID = nodex.registerActor(new Actor<String>() {
          public void onMessage(String message) {
            azzert(contextID1 == nodex.getContextID());
            latch2.countDown();
          }
        });
        map.put("actorid", actorID);
        latch1.countDown();
      }
    });

    azzert(latch1.await(5, TimeUnit.SECONDS));

    final long contextID2 = nodex.createAndAssociateContext();
    nodex.executeOnContext(contextID2, new Runnable() {
      public void run() {
        nodex.setContextID(contextID2);
        //Send msg to actor
        long actorID = map.get("actorid");
        nodex.<String>sendMessage(actorID, message);
      }
    });

    azzert(latch2.await(5, TimeUnit.SECONDS));
    nodex.destroyContext(contextID1);
    nodex.destroyContext(contextID2);

    throwAssertions();
  }


  @Test
  public void testActorNoContext() throws Exception {

    try {
      nodex.registerActor(new Actor<String>() {
        public void onMessage(String message) {
        }
      });
      azzert(false);
    } catch (IllegalStateException e) {
      //Expected
    }

    throwAssertions();
  }

}
