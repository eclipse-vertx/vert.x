/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

import io.vertx.core.spi.VerticleFactory;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.Callable;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VerticleFactoryTest extends VertxTestBase {

  public void setUp() throws Exception {
    super.setUp();
    // Unregister the factories that are loaded from the classpath
    for (VerticleFactory factory : vertx.verticleFactories()) {
      vertx.unregisterVerticleFactory(factory);
    }
  }

  @Test
  public void testRegister() {
    assertTrue(vertx.verticleFactories().isEmpty());
    VerticleFactory fact1 = new TestVerticleFactory("foo");
    vertx.registerVerticleFactory(fact1);
    assertEquals(1, vertx.verticleFactories().size());
    assertTrue(vertx.verticleFactories().contains(fact1));
  }

  @Test
  public void testUnregister() {
    VerticleFactory fact1 = new TestVerticleFactory("foo");
    vertx.registerVerticleFactory(fact1);
    assertEquals(1, vertx.verticleFactories().size());
    assertTrue(vertx.verticleFactories().contains(fact1));
    vertx.unregisterVerticleFactory(fact1);
    assertFalse(vertx.verticleFactories().contains(fact1));
    assertTrue(vertx.verticleFactories().isEmpty());
  }

  @Test
  public void testRegisterTwice() {
    VerticleFactory fact1 = new TestVerticleFactory("foo");
    vertx.registerVerticleFactory(fact1);
    try {
      vertx.registerVerticleFactory(fact1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testUnregisterTwice() {
    VerticleFactory fact1 = new TestVerticleFactory("foo");
    vertx.registerVerticleFactory(fact1);
    vertx.unregisterVerticleFactory(fact1);
    try {
      vertx.unregisterVerticleFactory(fact1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testUnregisterNoFact() {
    VerticleFactory fact1 = new TestVerticleFactory("foo");
    try {
      vertx.unregisterVerticleFactory(fact1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testRegisterUnregisterTwo() {
    VerticleFactory fact1 = new TestVerticleFactory("foo");
    VerticleFactory fact2 = new TestVerticleFactory("bar");
    vertx.registerVerticleFactory(fact1);
    assertEquals(1, vertx.verticleFactories().size());
    vertx.registerVerticleFactory(fact2);
    assertEquals(2, vertx.verticleFactories().size());
    assertTrue(vertx.verticleFactories().contains(fact1));
    assertTrue(vertx.verticleFactories().contains(fact2));
    vertx.unregisterVerticleFactory(fact1);
    assertFalse(vertx.verticleFactories().contains(fact1));
    assertEquals(1, vertx.verticleFactories().size());
    assertTrue(vertx.verticleFactories().contains(fact2));
    vertx.unregisterVerticleFactory(fact2);
    assertTrue(vertx.verticleFactories().isEmpty());
    assertFalse(vertx.verticleFactories().contains(fact1));
    assertFalse(vertx.verticleFactories().contains(fact2));
  }

  @Test
  public void testMatchWithPrefix() {
    TestVerticle verticle1 = new TestVerticle();
    TestVerticle verticle2 = new TestVerticle();
    TestVerticle verticle3 = new TestVerticle();
    TestVerticleFactory fact1 = new TestVerticleFactory("aa", verticle1);
    TestVerticleFactory fact2 = new TestVerticleFactory("bb", verticle2);
    TestVerticleFactory fact3 = new TestVerticleFactory("cc", verticle3);
    vertx.registerVerticleFactory(fact1);
    vertx.registerVerticleFactory(fact2);
    vertx.registerVerticleFactory(fact3);
    String name1 = "aa:myverticle1";
    String name2 = "bb:myverticle2";
    String name3 = "cc:myverticle3";
    vertx.deployVerticle(name1, new DeploymentOptions(), ar -> {
      assertTrue(ar.succeeded());
      assertEquals(name1, fact1.identifier);
      assertTrue(verticle1.startCalled);
      assertFalse(verticle2.startCalled);
      assertFalse(verticle3.startCalled);
      assertNull(fact2.identifier);
      assertNull(fact3.identifier);
      vertx.deployVerticle(name2, new DeploymentOptions(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(name2, fact2.identifier);
        assertTrue(verticle2.startCalled);
        assertFalse(verticle3.startCalled);
        assertNull(fact3.identifier);
        vertx.deployVerticle(name3, new DeploymentOptions(), ar3 -> {
          assertTrue(ar3.succeeded());
          assertEquals(name3, fact3.identifier);
          assertTrue(verticle3.startCalled);
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testMatchWithSuffix() {
    TestVerticle verticle1 = new TestVerticle();
    TestVerticle verticle2 = new TestVerticle();
    TestVerticle verticle3 = new TestVerticle();
    TestVerticleFactory fact1 = new TestVerticleFactory("aa", verticle1);
    TestVerticleFactory fact2 = new TestVerticleFactory("bb", verticle2);
    TestVerticleFactory fact3 = new TestVerticleFactory("cc", verticle3);
    vertx.registerVerticleFactory(fact1);
    vertx.registerVerticleFactory(fact2);
    vertx.registerVerticleFactory(fact3);
    String name1 = "myverticle1.aa";
    String name2 = "myverticle2.bb";
    String name3 = "myverticle3.cc";
    vertx.deployVerticle(name1, new DeploymentOptions(), ar -> {
      assertTrue(ar.succeeded());
      assertEquals(name1, fact1.identifier);
      assertTrue(verticle1.startCalled);
      assertFalse(verticle2.startCalled);
      assertFalse(verticle3.startCalled);
      assertNull(fact2.identifier);
      assertNull(fact3.identifier);
      vertx.deployVerticle(name2, new DeploymentOptions(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(name2, fact2.identifier);
        assertTrue(verticle2.startCalled);
        assertFalse(verticle3.startCalled);
        assertNull(fact3.identifier);
        vertx.deployVerticle(name3, new DeploymentOptions(), ar3 -> {
          assertTrue(ar3.succeeded());
          assertEquals(name3, fact3.identifier);
          assertTrue(verticle3.startCalled);
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testNoMatch() {
    TestVerticle verticle1 = new TestVerticle();
    TestVerticle verticle2 = new TestVerticle();
    TestVerticleFactory fact1 = new TestVerticleFactory("aa", verticle1);
    TestVerticleFactory fact2 = new TestVerticleFactory("bb", verticle2);
    vertx.registerVerticleFactory(fact1);
    vertx.registerVerticleFactory(fact2);
    String name1 = "cc:myverticle1";
    // If no match it will default to the simple Java verticle factory and then fail with ClassNotFoundException
    vertx.deployVerticle(name1, new DeploymentOptions(), ar -> {
      assertFalse(ar.succeeded());
      assertFalse(verticle1.startCalled);
      assertFalse(verticle2.startCalled);
      assertTrue(ar.cause() instanceof ClassNotFoundException);
      testComplete();
    });
    await();
  }

  @Test
  public void testOrdering() {
    TestVerticle verticle = new TestVerticle();
    TestVerticleFactory fact2 = new TestVerticleFactory("aa", verticle, 2);
    vertx.registerVerticleFactory(fact2);
    TestVerticleFactory fact1 = new TestVerticleFactory("aa", verticle, 1);
    vertx.registerVerticleFactory(fact1);
    TestVerticleFactory fact3 = new TestVerticleFactory("aa", verticle, 3);
    vertx.registerVerticleFactory(fact3);
    vertx.deployVerticle("aa:someverticle", res -> {
      assertTrue(res.succeeded());
      assertEquals("aa:someverticle", fact1.identifier);
      assertNull(fact2.identifier);
      assertNull(fact3.identifier);
      testComplete();
    });
    await();
  }

  @Test
  public void testOrderingFailedInCreate() {
    TestVerticle verticle = new TestVerticle();
    TestVerticleFactory fact2 = new TestVerticleFactory("aa", verticle, 2);
    vertx.registerVerticleFactory(fact2);
    TestVerticleFactory fact1 = new TestVerticleFactory("aa", verticle, 1, true);
    vertx.registerVerticleFactory(fact1);
    TestVerticleFactory fact3 = new TestVerticleFactory("aa", verticle, 3);
    vertx.registerVerticleFactory(fact3);
    vertx.deployVerticle("aa:someverticle", res -> {
      assertTrue(res.succeeded());
      assertEquals("aa:someverticle", fact2.identifier);
      assertNull(fact1.identifier);
      assertNull(fact3.identifier);
      testComplete();
    });
    await();
  }

  @Test
  public void testOrderingFailedInCreate2() {
    TestVerticle verticle = new TestVerticle();
    TestVerticleFactory fact2 = new TestVerticleFactory("aa", verticle, 2, true);
    vertx.registerVerticleFactory(fact2);
    TestVerticleFactory fact1 = new TestVerticleFactory("aa", verticle, 1, true);
    vertx.registerVerticleFactory(fact1);
    TestVerticleFactory fact3 = new TestVerticleFactory("aa", verticle, 3);
    vertx.registerVerticleFactory(fact3);
    vertx.deployVerticle("aa:someverticle", res -> {
      assertTrue(res.succeeded());
      assertEquals("aa:someverticle", fact3.identifier);
      assertNull(fact1.identifier);
      assertNull(fact2.identifier);
      testComplete();
    });
    await();
  }

  @Test
  public void testOrderingFailedInCreateAll() {
    TestVerticle verticle = new TestVerticle();
    TestVerticleFactory fact2 = new TestVerticleFactory("aa", verticle, 2, true);
    vertx.registerVerticleFactory(fact2);
    TestVerticleFactory fact1 = new TestVerticleFactory("aa", verticle, 1, true);
    vertx.registerVerticleFactory(fact1);
    TestVerticleFactory fact3 = new TestVerticleFactory("aa", verticle, 3, true);
    vertx.registerVerticleFactory(fact3);
    vertx.deployVerticle("aa:someverticle", res -> {
      assertFalse(res.succeeded());
      assertTrue(res.cause() instanceof ClassNotFoundException);
      assertNull(fact1.identifier);
      assertNull(fact2.identifier);
      assertNull(fact3.identifier);
      testComplete();
    });
    await();
  }

  @Test
  public void testDeploymentOnClosedVertxWithCompletionHandler() {
    TestVerticle verticle = new TestVerticle();
    vertx.close(done -> {
      vertx.deployVerticle(verticle, ar -> {
        assertFalse(ar.succeeded());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDeploymentOnClosedVertxWithoutCompletionHandler() {
    TestVerticle verticle = new TestVerticle();
    vertx.close(done -> {
      vertx.deployVerticle(verticle);
      testComplete();
    });
    await();
  }


  class TestVerticleFactory implements VerticleFactory {

    String prefix;
    Verticle verticle;
    String identifier;
    String isolationGroup;

    int order;
    boolean failInCreate;
    Context createContext;
    boolean createWorkerThread;

    TestVerticleFactory(String prefix) {
      this.prefix = prefix;
    }

    TestVerticleFactory(String prefix, Verticle verticle) {
      this.prefix = prefix;
      this.verticle = verticle;
    }

    TestVerticleFactory(String prefix, Verticle verticle, int order) {
      this.prefix = prefix;
      this.verticle = verticle;
      this.order = order;
    }

    TestVerticleFactory(String prefix, Verticle verticle, int order, boolean failInCreate) {
      this.prefix = prefix;
      this.verticle = verticle;
      this.order = order;
      this.failInCreate = failInCreate;
    }

    @Override
    public int order() {
      return order;
    }

    @Override
    public void init(Vertx vertx) {
    }

    @Override
    public String prefix() {
      return prefix;
    }


    @Override
    public void createVerticle(String verticleName, ClassLoader classLoader, Promise<Callable<Verticle>> promise) {
      if (failInCreate) {
        promise.fail(new ClassNotFoundException("whatever"));
        return;
      }
      this.identifier = verticleName;
      this.createContext = Vertx.currentContext();
      this.createWorkerThread = Context.isOnWorkerThread();
      promise.complete(() -> verticle);
    }

    @Override
    public void close() {

    }
  }

  class TestVerticle extends AbstractVerticle {

    boolean startCalled;

    @Override
    public void start() throws Exception {
      startCalled = true;
    }

    @Override
    public void stop() throws Exception {

    }
  }
}
