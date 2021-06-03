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

import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="https://wang007.github.io">wang007</a>
 */
public class AbstractVerticleGroupTest extends VertxTestBase {


  @Test
  public void testDeployVerticleGroup() {
    VertilceInstance a = new VertilceInstance(true, true);
    VertilceInstance b = new VertilceInstance(true, true);
    AbstractVerticleGroup verticleGroup = new AbstractVerticleGroup() {

      @Override
      public List<Verticle> verticles() {
        return Arrays.asList(a, b);
      }
    };
    vertx.deployVerticle(verticleGroup)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertTrue(a.doStart.get());
        assertTrue(b.doStart.get());
        testComplete();
      });
    await();
  }


  @Test
  public void testUndeployVerticleGroup() {
    VertilceInstance a = new VertilceInstance(true, true);
    VertilceInstance b = new VertilceInstance(true, true);
    AbstractVerticleGroup verticleGroup = new AbstractVerticleGroup() {
      @Override
      public List<Verticle> verticles() {
        return Arrays.asList(a, b);
      }
    };
    vertx.deployVerticle(verticleGroup)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertTrue(a.doStart.get());
        assertTrue(b.doStart.get());
      })
      .flatMap(vertx::undeploy)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertTrue(a.doStop.get());
        assertTrue(b.doStop.get());
        testComplete();
      });
    await();
  }

  @Test
  public void testUndeployVerticleGroupWhenSubVerticleThrow() {
    VertilceInstance a = new VertilceInstance(true, true);
    VertilceInstance b = new VertilceInstance(true, true) {
      @Override
      public void stop(Promise<Void> stopPromise) throws Exception {
        throw new IllegalArgumentException("Expected exception");
      }
    };
    VertilceInstance c = new VertilceInstance(true, true);
    AbstractVerticleGroup verticleGroup = new AbstractVerticleGroup() {

      @Override
      public List<Verticle> verticles() {
        return Arrays.asList(a, b, c);
      }
    };
    vertx.deployVerticle(verticleGroup)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertTrue(a.doStart.get());
        assertTrue(b.doStart.get());
        assertTrue(c.doStart.get());
      }).flatMap(vertx::undeploy)
      .onComplete(ar -> {
        assertTrue(ar.failed());
        assertTrue(a.doStop.get());
        assertFalse(b.doStop.get());
        assertTrue(c.doStop.get());
        testComplete();
      });
    await();
  }


  @Test
  public void testRollbackWhenDeployFailed() {
    VertilceInstance a = new VertilceInstance(true, true);
    VertilceInstance b = new VertilceInstance(false, true);
    AbstractVerticleGroup verticleGroup = new AbstractVerticleGroup() {

      @Override
      public List<Verticle> verticles() {
        return Arrays.asList(a, b);
      }
    };
    vertx.deployVerticle(verticleGroup)
      .onComplete(ar -> {
        assertTrue(ar.failed());
        assertTrue(a.doStart.get());
        assertTrue(a.doStop.get()); //rollback
        assertFalse(b.doStart.get());
        assertFalse(b.doStop.get());
        testComplete();
      });
    await();
  }

  @Test
  public void testRollbackWhenSubVerticleThrow() {
    VertilceInstance a = new VertilceInstance(true, true);
    VertilceInstance b = new VertilceInstance(true, true) {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        throw new IllegalArgumentException("Expected exception");
      }
    };
    AbstractVerticleGroup verticleGroup = new AbstractVerticleGroup() {

      @Override
      public List<Verticle> verticles() {
        return Arrays.asList(a, b);
      }
    };
    vertx.deployVerticle(verticleGroup)
      .onComplete(ar -> {
        assertTrue(ar.failed());
        assertTrue(a.doStart.get());
        assertTrue(a.doStop.get()); //rollback
        assertFalse(b.doStart.get());
        assertFalse(b.doStop.get());
        testComplete();
      });
    await();
  }


  @Test
  public void testSyncDeployVerticleGroup() {
    final List<String> arr = new ArrayList<>();

    VertilceInstance a = new VertilceInstance(true, true) {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        vertx.setTimer(500, v -> {
          arr.add("a");
          try {
            super.start(startPromise);
          } catch (Throwable e) {
            startPromise.fail(e);
          }
        });
      }
    };
    VertilceInstance b = new VertilceInstance(true, true) {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        arr.add("b");
        super.start(startPromise);
      }
    };

    AbstractVerticleGroup verticleGroup = new AbstractVerticleGroup() {
      @Override
      public List<Verticle> verticles() {
        return Arrays.asList(a, b);
      }

      @Override
      public boolean asyncDeploy() {
        return false;
      }
    };
    vertx.deployVerticle(verticleGroup)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertTrue(a.doStart.get());
        assertTrue(b.doStart.get());
        assertEquals(arr.get(0), "a");
        assertEquals(arr.get(1), "b");
        testComplete();
      });
    await();
  }

  @Test
  public void testSyncUndeployVerticleGroup() {
    final List<String> arr = new ArrayList<>();
    VertilceInstance a = new VertilceInstance(true, true) {
      @Override
      public void stop(Promise<Void> stopPromise) throws Exception {
        vertx.setTimer(500, v -> {
          arr.add("a");
          try {
            super.stop(stopPromise);
          } catch (Throwable e) {
            stopPromise.fail(e);
          }
        });
      }
    };
    VertilceInstance b = new VertilceInstance(true, true) {
      @Override
      public void stop(Promise<Void> stopPromise) throws Exception {
        arr.add("b");
        super.stop(stopPromise);
      }
    };

    AbstractVerticleGroup verticleGroup = new AbstractVerticleGroup() {
      @Override
      public List<Verticle> verticles() {
        return Arrays.asList(a, b);
      }

      @Override
      public boolean asyncUndeploy() {
        return false;
      }
    };

    vertx.deployVerticle(verticleGroup)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertTrue(a.doStart.get());
        assertTrue(b.doStart.get());
      })
      .flatMap(vertx::undeploy)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertTrue(a.doStop.get());
        assertTrue(b.doStop.get());
        assertEquals(arr.get(0), "a");
        assertEquals(arr.get(1), "b");
        testComplete();
      });
    await();
  }

  @Test
  public void testSyncUndeployVerticleGroupWhenSubVerticleThrow() {
    VertilceInstance a = new VertilceInstance(true, true);
    VertilceInstance b = new VertilceInstance(true, true) {
      @Override
      public void stop(Promise<Void> stopPromise) throws Exception {
        throw new IllegalArgumentException("Expected exception");
      }
    };
    VertilceInstance c = new VertilceInstance(true, true);
    AbstractVerticleGroup verticleGroup = new AbstractVerticleGroup() {

      @Override
      public List<Verticle> verticles() {
        return Arrays.asList(a, b, c);
      }

      @Override
      public boolean asyncUndeploy() {
        return false;
      }
    };
    vertx.deployVerticle(verticleGroup)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertTrue(a.doStart.get());
        assertTrue(b.doStart.get());
        assertTrue(c.doStart.get());
      }).flatMap(vertx::undeploy)
      .onComplete(ar -> {
        assertTrue(ar.failed());
        assertTrue(a.doStop.get());
        assertFalse(b.doStop.get());
        assertTrue(c.doStop.get());
        testComplete();
      });
    await();
  }


  class VertilceInstance extends AbstractVerticle {
    public final AtomicBoolean doStart = new AtomicBoolean();
    public final AtomicBoolean doStop = new AtomicBoolean();

    private final boolean startSucceeded;
    private final boolean stopSucceeded;

    VertilceInstance(boolean startSucceeded, boolean stopSucceeded) {
      this.startSucceeded = startSucceeded;
      this.stopSucceeded = stopSucceeded;
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
      if (startSucceeded) {
        doStart.set(true);
        startPromise.complete();
        return;
      }
      startPromise.fail("");
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
      doStop.set(true);
      if (stopSucceeded) {
        stopPromise.complete();
        return;
      }
      stopPromise.fail("");
    }
  }


}
