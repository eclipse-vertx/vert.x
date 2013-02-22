/*
 * Copyright 2013 the original author or authors.
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
package org.vertx.java.tests;

import org.junit.*;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.PlatformLocator;
import org.vertx.java.platform.PlatformManager;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author swilliams
 *
 */
public class PrefixLanguageImplementationTest {

  private PlatformManager platformManager;

  @BeforeClass
  public static void beforeClass() {
    System.setProperty("vertx.langs.foo", FooLangVerticleFactory.class.getName());
  }

  @Before
  public void before() throws Exception {
    platformManager = PlatformLocator.factory.createPlatformManager();
  }

  @Test
  public void deployFooVerticle() {
    String main = "test.foo";

    JsonObject config = new JsonObject();
    config.putString("foo", "foo");

    URL[] urls = new URL[0];
    String includes = null;

    final CountDownLatch latch = new CountDownLatch(1);
    Handler<String> doneHandler = new Handler<String>() {
      @Override
      public void handle(String event) {
        if (event != null) {
          latch.countDown();
        }
      }
    };

    platformManager.deployVerticle(main, config, urls, 1, includes, doneHandler);

    boolean await;

    while (true) {
      try {
        await = latch.await(5000L, TimeUnit.MILLISECONDS);
        break;
      } catch (InterruptedException e) {
        //
      }
    }

    if (!await) {
      Assert.fail("Probably not deployed still waiting for " + latch.getCount());
    }
  }

  @Test
  public void deployFooVerticleFailure() {
    String main = "expectedfailure:expected-to-fail.test";

    JsonObject config = new JsonObject();
    config.putString("foo", "foo");

    URL[] urls = new URL[0];
    String includes = null;

    final CountDownLatch latch = new CountDownLatch(1);
    Handler<String> doneHandler = new Handler<String>() {
      @Override
      public void handle(String event) {
        // null means failed to deploy
        if (event == null) {
          latch.countDown();
        }
      }
    };

    platformManager.deployVerticle(main, config, urls, 1, includes, doneHandler);

    boolean await;
    while (true) {
      try {
        await = latch.await(5000, TimeUnit.MILLISECONDS);
        break;
      } catch (InterruptedException e) {
        //
      }
    }

    if (!await) {
      Assert.assertEquals(1, latch.getCount());
    }
  }

  @AfterClass
  public static void cleanup() {
    System.clearProperty("vertx.langs.foo");
  }
}
