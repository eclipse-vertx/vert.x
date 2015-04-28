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
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.Deployment;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VerticleFactory;
import org.junit.Test;

import java.io.IOException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VerticleFactoryTest extends VertxTestBase {

  public void setUp() throws Exception {
    super.setUp();
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
  public void testResolve() {
    TestVerticle verticle = new TestVerticle();
    TestVerticleFactory fact = new TestVerticleFactory("actual", verticle);
    vertx.registerVerticleFactory(fact);
    TestVerticleFactory factResolve = new TestVerticleFactory("resolve", "actual:myverticle");
    vertx.registerVerticleFactory(factResolve);
    JsonObject config = new JsonObject().put("foo", "bar");
    DeploymentOptions original = new DeploymentOptions().setWorker(false).setConfig(config).setIsolationGroup("somegroup");
    DeploymentOptions options = new DeploymentOptions(original);
    vertx.deployVerticle("resolve:someid", options, res -> {
      assertTrue(res.succeeded());
      assertEquals("resolve:someid", factResolve.identifierToResolve);
      assertEquals(options, factResolve.deploymentOptionsToResolve);
      assertEquals("actual:myverticle", fact.identifier);
      assertTrue(verticle.startCalled);
      assertTrue(verticle.startCalled);
      assertEquals(1, vertx.deploymentIDs().size());
      Deployment dep = ((VertxInternal)vertx).getDeployment(res.result());
      assertNotNull(dep);
      assertFalse(original.equals(dep.deploymentOptions()));
      assertFalse(dep.deploymentOptions().getConfig().containsKey("foo"));
      assertEquals("quux", dep.deploymentOptions().getConfig().getString("wibble"));
      assertTrue(dep.deploymentOptions().isWorker());
      assertEquals("othergroup", dep.deploymentOptions().getIsolationGroup());
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
  public void testOrderingFailedInResolve() {
    TestVerticle verticle = new TestVerticle();

    TestVerticleFactory factActual = new TestVerticleFactory("actual", verticle);
    vertx.registerVerticleFactory(factActual);

    TestVerticleFactory fact2 = new TestVerticleFactory("aa", "actual:someverticle", 2);
    vertx.registerVerticleFactory(fact2);
    TestVerticleFactory fact1 = new TestVerticleFactory("aa", "actual:someverticle", 1, true);
    vertx.registerVerticleFactory(fact1);
    TestVerticleFactory fact3 = new TestVerticleFactory("aa", "actual:someverticle", 3);
    vertx.registerVerticleFactory(fact3);
    vertx.deployVerticle("aa:blah", res -> {
      assertTrue(res.succeeded());
      assertNull(fact2.identifier);
      assertNull(fact1.identifier);
      assertNull(fact3.identifier);
      assertEquals("aa:blah", fact2.identifierToResolve);
      assertNull(fact1.identifierToResolve);
      assertNull(fact3.identifierToResolve);
      assertEquals("actual:someverticle", factActual.identifier);
      testComplete();
    });
    await();
  }

  @Test
  public void testOrderingFailedInResolve2() {
    TestVerticle verticle = new TestVerticle();

    TestVerticleFactory factActual = new TestVerticleFactory("actual", verticle);
    vertx.registerVerticleFactory(factActual);

    TestVerticleFactory fact2 = new TestVerticleFactory("aa", "actual:someverticle", 2, true);
    vertx.registerVerticleFactory(fact2);
    TestVerticleFactory fact1 = new TestVerticleFactory("aa", "actual:someverticle", 1, true);
    vertx.registerVerticleFactory(fact1);
    TestVerticleFactory fact3 = new TestVerticleFactory("aa", "actual:someverticle", 3);
    vertx.registerVerticleFactory(fact3);
    vertx.deployVerticle("aa:blah", res -> {
      assertTrue(res.succeeded());
      assertNull(fact2.identifier);
      assertNull(fact1.identifier);
      assertNull(fact3.identifier);
      assertEquals("aa:blah", fact3.identifierToResolve);
      assertNull(fact1.identifierToResolve);
      assertNull(fact2.identifierToResolve);
      assertEquals("actual:someverticle", factActual.identifier);
      testComplete();
    });
    await();
  }

  @Test
  public void testOrderingAllFailedInResolve() {
    TestVerticle verticle = new TestVerticle();

    TestVerticleFactory factActual = new TestVerticleFactory("actual", verticle);
    vertx.registerVerticleFactory(factActual);

    TestVerticleFactory fact2 = new TestVerticleFactory("aa", "actual:someverticle", 2, true);
    vertx.registerVerticleFactory(fact2);
    TestVerticleFactory fact1 = new TestVerticleFactory("aa", "actual:someverticle", 1, true);
    vertx.registerVerticleFactory(fact1);
    TestVerticleFactory fact3 = new TestVerticleFactory("aa", "actual:someverticle", 3, true);
    vertx.registerVerticleFactory(fact3);
    vertx.deployVerticle("aa:blah", res -> {
      assertTrue(res.failed());
      assertTrue(res.cause() instanceof IOException);
      assertNull(fact2.identifier);
      assertNull(fact1.identifier);
      assertNull(fact3.identifier);
      assertNull(fact3.identifierToResolve);
      assertNull(fact1.identifierToResolve);
      assertNull(fact2.identifierToResolve);
      assertNull(factActual.identifier);
      testComplete();
    });
    await();
  }

  class TestVerticleFactory implements VerticleFactory {

    String prefix;
    Verticle verticle;
    String identifier;

    String resolvedIdentifier;

    String identifierToResolve;
    DeploymentOptions deploymentOptionsToResolve;
    int order;
    boolean failInCreate;
    boolean failInResolve;

    TestVerticleFactory(String prefix) {
      this.prefix = prefix;
    }

    TestVerticleFactory(String prefix, Verticle verticle) {
      this.prefix = prefix;
      this.verticle = verticle;
    }

    TestVerticleFactory(String prefix, String resolvedIdentifier) {
      this.prefix = prefix;
      this.resolvedIdentifier = resolvedIdentifier;
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

    TestVerticleFactory(String prefix, String resolvedIdentifier, int order) {
      this.prefix = prefix;
      this.resolvedIdentifier = resolvedIdentifier;
      this.order = order;
    }

    TestVerticleFactory(String prefix, String resolvedIdentifier, int order, boolean failInResolve) {
      this.prefix = prefix;
      this.resolvedIdentifier = resolvedIdentifier;
      this.order = order;
      this.failInResolve = failInResolve;
    }

    @Override
    public int order() {
      return order;
    }

    @Override
    public boolean requiresResolve() {
      return resolvedIdentifier != null;
    }

    @Override
    public void resolve(String identifier, DeploymentOptions deploymentOptions, ClassLoader classLoader, Future<String> resolution) {
      if (failInResolve) {
        resolution.fail(new IOException("whatever"));
      } else {
        identifierToResolve = identifier;
        deploymentOptionsToResolve = deploymentOptions;
        // Now we change the deployment options
        deploymentOptions.setConfig(new JsonObject().put("wibble", "quux"));
        deploymentOptions.setWorker(true);
        deploymentOptions.setIsolationGroup("othergroup");
        resolution.complete(resolvedIdentifier);
      }
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
      if (failInCreate) {
        throw new ClassNotFoundException("whatever");
      }
      this.identifier = verticleName;
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
