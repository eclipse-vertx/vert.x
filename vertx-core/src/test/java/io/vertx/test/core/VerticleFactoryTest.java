/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.spi.VerticleFactory;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VerticleFactoryTest extends VertxTestBase {

  @Before
  public void before() {
    // Unregister the factory that's loaded from the classpath
    VerticleFactory factory = vertx.verticleFactories().iterator().next();
    vertx.unregisterVerticleFactory(factory);
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
  public void testMatch() {
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
    vertx.deployVerticle(name1, DeploymentOptions.options(), ar -> {
      assertTrue(ar.succeeded());
      assertEquals(name1.substring(3), fact1.verticleName);
      assertTrue(verticle1.startCalled);
      assertFalse(verticle2.startCalled);
      assertFalse(verticle3.startCalled);
      assertNull(fact2.verticleName);
      assertNull(fact3.verticleName);
      vertx.deployVerticle(name2, DeploymentOptions.options(), ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(name2.substring(3), fact2.verticleName);
        assertTrue(verticle2.startCalled);
        assertFalse(verticle3.startCalled);
        assertNull(fact3.verticleName);
        vertx.deployVerticle(name3, DeploymentOptions.options(), ar3 -> {
          assertTrue(ar3.succeeded());
          assertEquals(name3.substring(3), fact3.verticleName);
          assertTrue(verticle3.startCalled);
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testMultipleMatch() {
    TestVerticle verticle1 = new TestVerticle();
    TestVerticle verticle2 = new TestVerticle();
    TestVerticleFactory fact1 = new TestVerticleFactory("aa", verticle1);
    TestVerticleFactory fact2 = new TestVerticleFactory("aa", verticle2);
    vertx.registerVerticleFactory(fact1);
    try {
      vertx.registerVerticleFactory(fact2);
      fail("Should throw exception") ;
    } catch (IllegalArgumentException e) {
      // OK
    }
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
    vertx.deployVerticle(name1, DeploymentOptions.options(), ar -> {
      assertFalse(ar.succeeded());
      assertFalse(verticle1.startCalled);
      assertFalse(verticle2.startCalled);
      assertTrue(ar.cause() instanceof ClassNotFoundException);
      testComplete();
    });
    await();
  }

  class TestVerticleFactory implements VerticleFactory {

    String prefix;
    Verticle verticle;
    String verticleName;

    TestVerticleFactory(String prefix) {
      this.prefix = prefix;
    }

    TestVerticleFactory(String prefix, Verticle verticle) {
      this.prefix = prefix;
      this.verticle = verticle;
    }


    @Override
    public void init(Vertx vertx) {

    }

    @Override
    public String prefix() {
      return prefix;
    }


    @Override
    public Verticle createVerticle(String verticleName, ClassLoader classLoader) throws Exception {
      this.verticleName = verticleName;
      return verticle;
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
