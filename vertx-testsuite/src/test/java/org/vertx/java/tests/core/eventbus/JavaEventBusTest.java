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

package org.vertx.java.tests.core.eventbus;

import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.testframework.TestBase;
import vertx.tests.core.eventbus.LocalClient;
import vertx.tests.core.eventbus.LocalPeer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaEventBusTest extends TestBase {

  private static final Logger log = LoggerFactory.getLogger(JavaEventBusTest.class);

  private int numPeers = 4;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    vertx.sharedData().getSet("addresses").clear();
    for (int i = 0; i < numPeers; i++) {
      startApp(getPeerClassName());
    }
    startApp(getClientClassName());
  }

  protected String getPeerClassName() {
    return LocalPeer.class.getName();
  }

  protected String getClientClassName() {
    return LocalClient.class.getName();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  private void runPeerTest(String testName) {
    runPeerTest(testName, numPeers);
  }

  private void runPeerTest(String testName, int numPeers) {
    startTest(testName + "Initialise", false);
    for (int i = 0; i < numPeers; i++) {
      super.waitTestComplete();
    }
    startTest(testName, false);
    for (int i = 0; i < numPeers; i++) {
      super.waitTestComplete();
    }
  }

  @Test
  public void testPubSub() throws Exception {
    runPeerTest(getMethodName());
  }

  @Test
  public void testPubSubMultipleHandlers() throws Exception {
    runPeerTest(getMethodName());
  }

  @Test
  public void testPointToPoint() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testPointToPointRoundRobin() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testReply() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testReplyDifferentType() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testReplyUntypedHandler() {
    runPeerTest(getMethodName());
  }

  @Test
  public void testLocal1() {
    startTest(getMethodName());
  }

  @Test
  public void testLocal2() {
    startTest(getMethodName());
  }

  @Test
  public void testRegisterNoAddress() {
    startTest(getMethodName());
  }

  public void testNoContext() throws Exception {
    Vertx vertx = VertxFactory.newVertx();
    final EventBus eb = vertx.eventBus();
    final CountDownLatch latch = new CountDownLatch(1);
    eb.registerHandler("foo", new Handler<Message<String>>() {
      public void handle(Message<String> msg) {
        assert("bar".equals(msg.body()));
        eb.unregisterHandler("foo", this);
        latch.countDown();
      }
    });
    eb.send("foo", "bar");
    assert(latch.await(5, TimeUnit.SECONDS));
    vertx.stop();
  }
}
