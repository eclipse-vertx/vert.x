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
package org.vertx.java.tests.deploy;

import org.junit.*;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.PlatformManager;
import org.vertx.java.platform.impl.DefaultPlatformManager;

import java.io.File;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author swilliams
 *
 */
public class SystemPropertyLanguageImplementationTest {

  private PlatformManager platformManager;
  private VertxInternal vertxInternal;

  @BeforeClass
  public static void beforeClass() {
    System.setProperty("vertx.langs.foo", "org.vertx.java.tests.deploy.FooLangVerticleFactory");
  }

  @Before
  public void before() throws Exception {
    vertxInternal = new DefaultVertx();
    platformManager = new DefaultPlatformManager(vertxInternal);
  }

  @Test
  public void deployFooVerticle() {
    String main = "test.foo";

    JsonObject config = new JsonObject();
    config.putString("foo", "foo");

    URL[] urls = new URL[0];
    File currentModDir = new File(System.getProperty("java.io.tmpdir"));
    String includes = null;

    final CountDownLatch latch = new CountDownLatch(1);
    Handler<String> doneHandler = new Handler<String>() {
      @Override
      public void handle(String event) {
        if (event != null && event.startsWith("deployment-")) {
          latch.countDown();
        }
      }
    };

    platformManager.deployVerticle(false, false, main, config, urls, 1, currentModDir, includes, doneHandler);

    boolean await = false;

    try {
      await = latch.await(1000L, TimeUnit.MILLISECONDS);

    } catch (InterruptedException e) {
      //
    } catch (Throwable e) {
      Assert.fail(e.getMessage());
    }

    if (!await) {
      Assert.fail("Probably not deployed still waiting for " + latch.getCount());
    }
  }

  @Test
  public void deployFooVerticleFailure() {
    String main = "expected-to-fail.testfailure";

    JsonObject config = new JsonObject();
    config.putString("foo", "foo");

    URL[] urls = new URL[0];
    File currentModDir = new File(System.getProperty("java.io.tmpdir"));
    String includes = null;

    final CountDownLatch latch = new CountDownLatch(1);
    Handler<String> doneHandler = new Handler<String>() {
      @Override
      public void handle(String event) {
        if (event != null && event.startsWith("deployment-")) {
          latch.countDown();
        }
      }
    };

    platformManager.deployVerticle(false, false, main, config, urls, 1, currentModDir, includes, doneHandler);

    boolean await = false;

    try {
      await = latch.await(250L, TimeUnit.MILLISECONDS);

    } catch (InterruptedException e) {
      //
    } catch (Throwable e) {
      Assert.fail(e.getMessage());
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
