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
package org.vertx.java.tests.platform;

import org.junit.Test;

/**
 * @author swilliams
 *
 */
public class SystemPropertyLanguageImplementationTest {

  @Test
  public void testDummy() {
  }

  // Commented out - Vert.x requires all languages other than Java to be implemented as modules!

//  private PlatformManager platformManager;
//
//  @BeforeClass
//  public static void beforeClass() {
//    System.setProperty("vertx.langs.foo", FooLangVerticleFactory.class.getName());
//  }
//
//  @Before
//  public void before() throws Exception {
//    platformManager = PlatformLocator.factory.createPlatformManager();
//  }
//
//  @Test
//  public void deployFooVerticle() {
//    String main = "test.foo";
//
//    JsonObject config = new JsonObject();
//    config.putString("foo", "foo");
//
//    URL[] urls = new URL[0];
//    String includes = null;
//
//    final CountDownLatch latch = new CountDownLatch(1);
//    Handler<String> doneHandler = new Handler<String>() {
//      @Override
//      public void handle(String event) {
//        if (event != null) {
//          latch.countDown();
//        }
//      }
//    };
//
//    platformManager.deployVerticle(main, config, urls, 1, includes, doneHandler);
//
//    boolean await;
//
//    while (true) {
//      try {
//        await = latch.await(5000, TimeUnit.MILLISECONDS);
//        break;
//      } catch (InterruptedException e) {
//        //
//      }
//    }
//
//    if (!await) {
//      Assert.fail("Probably not deployed still waiting for " + latch.getCount());
//    }
//  }
//
//  @Test
//  public void deployFooVerticleFailure() {
//    String main = "expected-to-fail.testfailure";
//
//    JsonObject config = new JsonObject();
//    config.putString("foo", "foo");
//
//    URL[] urls = new URL[0];
//    String includes = null;
//
//    final CountDownLatch latch = new CountDownLatch(1);
//    Handler<String> doneHandler = new Handler<String>() {
//      @Override
//      public void handle(String event) {
//        // null means deploy failed
//        if (event == null) {
//          latch.countDown();
//        }
//      }
//    };
//
//    platformManager.deployVerticle(main, config, urls, 1, includes, doneHandler);
//
//    boolean await;
//
//    while (true) {
//      try {
//        await = latch.await(5000, TimeUnit.MILLISECONDS);
//        break;
//      } catch (InterruptedException e) {
//        //
//      }
//    }
//
//    if (!await) {
//      Assert.assertEquals(1, latch.getCount());
//    }
//  }
//
//  @AfterClass
//  public static void cleanup() {
//    System.clearProperty("vertx.langs.foo");
//  }
}
