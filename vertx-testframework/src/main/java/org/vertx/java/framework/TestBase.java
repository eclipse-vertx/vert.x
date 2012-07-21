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

package org.vertx.java.framework;

import junit.framework.TestCase;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.impl.VerticleManager;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestBase extends TestCase {
  private static final Logger log = LoggerFactory.getLogger(TestBase.class);
  private VertxTestFixture fixture = new VertxTestFixture();

  protected static VertxInternal vertx = VertxTestFixture.vertx;
  public static final String EVENTS_ADDRESS = VertxTestFixture.EVENTS_ADDRESS;

  @Override
  protected void setUp() throws Exception {
    fixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    fixture.tearDown();
  }

  protected String startApp(String main) throws Exception {
    return startApp(false, main, true);
  }

  protected String startApp(String main, JsonObject config) throws Exception {
    return startApp(false, main, config, 1, true);
  }

  protected String startApp(String main, boolean await) throws Exception {
    return startApp(false, main, null, 1, await);
  }

  protected String startApp(String main, int instances) throws Exception {
    return startApp(false, main, null, instances, true);
  }

  protected String startApp(boolean worker, String main) throws Exception {
    return startApp(worker, main, true);
  }

  protected String startApp(boolean worker, String main, JsonObject config) throws Exception {
    return startApp(worker, main, config, 1, true);
  }

  protected String startApp(boolean worker, String main, boolean await) throws Exception {
    return startApp(worker, main, null, 1, await);
  }

  protected String startApp(boolean worker, String main, int instances) throws Exception {
    return startApp(worker, main, null, instances, true);
  }

  protected String startApp(boolean worker, String main, JsonObject config, int instances, boolean await) throws Exception {
    return fixture.startApp(worker, main, config, instances, await);
  }

  public String startMod(String modName) throws Exception {
    return startMod(modName, null, 1, true);
  }

  public String startMod(String modName, JsonObject config, int instances, boolean await) throws Exception {
    return fixture.startMod(modName, config, instances, await);
  }

  protected void stopApp(String appName) throws Exception {
    fixture.stopApp(appName);
  }

  protected void startTest(String testName) {
    startTest(testName, true);
  }

  protected void startTest(String testName, boolean wait) {
    fixture.startTest(testName, wait);
  }

  protected String getMethodName() {
    return Thread.currentThread().getStackTrace()[2].getMethodName();
  }

  protected void waitTestComplete(int timeout) {
    waitEvent(timeout, EventFields.TEST_COMPLETE_EVENT);
  }

  protected void waitEvent(String eventName) {
    waitEvent(5, eventName);
  }

  protected void waitEvent(int timeout, String eventName) {
    fixture.waitEvent(timeout, eventName);
  }

  protected void waitTestComplete() {
    waitTestComplete(VertxTestFixture.DEFAULT_TIMEOUT);
  }

  @Test
  protected void runTestInLoop(String testName, int iters) throws Exception {
    Method meth = getClass().getMethod(testName, (Class<?>[])null);
    for (int i = 0; i < iters; i++) {
      log.info("****************************** ITER " + i);
      meth.invoke(this);
      tearDown();
      if (i != iters - 1) {
        setUp();
      }
    }
  }


}
