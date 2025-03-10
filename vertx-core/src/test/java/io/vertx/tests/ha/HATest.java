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

package io.vertx.tests.ha;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.deployment.Deployment;
import io.vertx.core.internal.deployment.DeploymentContext;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HATest extends VertxTestBase {

  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  protected Vertx vertx1, vertx2, vertx3, vertx4 = null;

  @Override
  protected void tearDown() throws Exception {
    closeVertices(vertx1, vertx2, vertx3, vertx4);
    super.tearDown();
  }

  @Test
  public void testSimpleFailover() throws Exception {
    startNodes(2, new VertxOptions().setHAEnabled(true));
    DeploymentOptions options = new DeploymentOptions().setHa(true);
    JsonObject config = new JsonObject().put("foo", "bar");
    options.setConfig(config);
    CountDownLatch latch = new CountDownLatch(1);
    vertices[0].deployVerticle("java:" + HAVerticle1.class.getName(), options).onComplete(onSuccess(v -> {
      assertEquals(1, vertices[0].deploymentIDs().size());
      assertEquals(0, vertices[1].deploymentIDs().size());
      latch.countDown();
    }));
    awaitLatch(latch);
    kill(0);
    assertWaitUntil(() -> vertices[1].deploymentIDs().size() == 1);
    checkDeploymentExists(1, "java:" + HAVerticle1.class.getName(), options);
  }

  @Test
  public void testQuorum() throws Exception {
    vertx1 = startVertx(2);
    DeploymentOptions options = new DeploymentOptions().setHa(true);
    JsonObject config = new JsonObject().put("foo", "bar");
    options.setConfig(config);
    vertx1.deployVerticle("java:" + HAVerticle1.class.getName(), options).onComplete(onSuccess(id -> {
      assertTrue(vertx1.deploymentIDs().contains(id));
      testComplete();
    }));
    // Shouldn't deploy until a quorum is obtained
    assertWaitUntil(() -> vertx1.deploymentIDs().isEmpty());
    vertx2 = startVertx(2);
    // Now should be deployed
    await();
  }

  @Test
  public void testQuorumLost() throws Exception {
    vertx1 = startVertx(3);
    vertx2 = startVertx(3);
    vertx3 = startVertx(3);
    DeploymentOptions options = new DeploymentOptions().setHa(true);
    JsonObject config = new JsonObject().put("foo", "bar");
    options.setConfig(config);
    vertx1.deployVerticle("java:" + HAVerticle1.class.getName(), options).onComplete(onSuccess(id -> {
      assertTrue(vertx1.deploymentIDs().contains(id));
    }));
    vertx2.deployVerticle("java:" + HAVerticle2.class.getName(), options).onComplete(onSuccess(id -> {
      assertTrue(vertx2.deploymentIDs().contains(id));
    }));
    assertWaitUntil(() -> vertx1.deploymentIDs().size() == 1 && vertx2.deploymentIDs().size() == 1);
    // Now close vertx3 - quorum should then be lost and verticles undeployed
    CountDownLatch latch = new CountDownLatch(1);
    vertx3.close().onComplete(ar -> {
      latch.countDown();
    });
    awaitLatch(latch);
    assertWaitUntil(() -> vertx1.deploymentIDs().isEmpty() && vertx2.deploymentIDs().isEmpty());
    // Now re-instate the quorum
    vertx4 = startVertx(3);
    assertWaitUntil(() -> vertx1.deploymentIDs().size() == 1 && vertx2.deploymentIDs().size() == 1);

  }

  @Test
  public void testCleanCloseNoFailover() throws Exception {

    vertx1 = startVertx();
    vertx2 = startVertx();
    DeploymentOptions options = new DeploymentOptions().setHa(true);
    JsonObject config = new JsonObject().put("foo", "bar");
    options.setConfig(config);
    CountDownLatch deployLatch = new CountDownLatch(1);
    vertx2.deployVerticle("java:" + HAVerticle1.class.getName(), options).onComplete(onSuccess(id -> {
      deployLatch.countDown();
    }));
    awaitLatch(deployLatch);
    ((VertxImpl)vertx1).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
      fail("Should not be called");
    });
    vertx2.close().onComplete(ar -> {
      vertx.setTimer(500, tid -> {
        // Wait a bit in case failover happens
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testFailureInFailover() throws Exception {
    vertx1 = startVertx();
    vertx2 = startVertx();
    vertx3 = startVertx();
    CountDownLatch latch1 = new CountDownLatch(1);
    vertx1.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHa(true)).onComplete(onSuccess(id -> {
      assertTrue(vertx1.deploymentIDs().contains(id));
      latch1.countDown();
    }));
    awaitLatch(latch1);
    ((VertxImpl)vertx2).haManager().failDuringFailover(true);
    ((VertxImpl)vertx3).haManager().failDuringFailover(true);
    CountDownLatch latch2 = new CountDownLatch(1);
    ((VertxImpl)vertx2).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
      assertFalse(succeeded);
      latch2.countDown();
    });
    ((VertxImpl)vertx3).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
      assertFalse(succeeded);
      latch2.countDown();
    });
    ((VertxImpl)vertx1).haManager().simulateKill();
    awaitLatch(latch2);

    // Now try again - this time failover should work

    assertTrue(vertx2.deploymentIDs().isEmpty());
    assertTrue(vertx3.deploymentIDs().isEmpty());
    ((VertxImpl)vertx2).haManager().failDuringFailover(false);
    CountDownLatch latch3 = new CountDownLatch(1);
    ((VertxImpl)vertx2).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
      assertTrue(succeeded);
      latch3.countDown();
    });
    ((VertxImpl)vertx3).haManager().simulateKill();
    awaitLatch(latch3);
    assertWaitUntil(() -> vertx2.deploymentIDs().size() == 1);
  }

  @Test
  public void testHaGroups() throws Exception {
    vertx1 = startVertx("group1", 1);
    vertx2 = startVertx("group1", 1);
    vertx3 = startVertx("group2", 1);
    vertx4 = startVertx("group2", 1);
    CountDownLatch latch1 = new CountDownLatch(2);
    vertx1.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHa(true)).onComplete(onSuccess(id -> {
      assertTrue(vertx1.deploymentIDs().contains(id));
      latch1.countDown();
    }));
    vertx3.deployVerticle("java:" + HAVerticle2.class.getName(), new DeploymentOptions().setHa(true)).onComplete(onSuccess(id -> {
      assertTrue(vertx3.deploymentIDs().contains(id));
      latch1.countDown();
    }));
    awaitLatch(latch1);
    CountDownLatch latch2 = new CountDownLatch(1);
    ((VertxImpl)vertx1).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
      fail("Should not failover here 1");
    });
    ((VertxImpl)vertx2).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
      fail("Should not failover here 2");
    });
    ((VertxImpl)vertx4).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
      assertTrue(succeeded);
      latch2.countDown();
    });
    ((VertxImpl)vertx3).haManager().simulateKill();
    awaitLatch(latch2);
    assertTrue(vertx4.deploymentIDs().size() == 1);
    CountDownLatch latch3 = new CountDownLatch(1);
    ((VertxImpl)vertx2).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
      assertTrue(succeeded);
      latch3.countDown();
    });
    ((VertxImpl)vertx4).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
      fail("Should not failover here 4");
    });
    ((VertxImpl)vertx1).haManager().simulateKill();
    awaitLatch(latch3);
    assertTrue(vertx2.deploymentIDs().size() == 1);
  }

  @Test
  public void testNoFailoverToNonHANode() throws Exception {
    vertx1 = startVertx();
    // Create a non HA node
    vertx2 = startVertx(null, 0, false);

    CountDownLatch latch1 = new CountDownLatch(1);
    vertx1.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHa(true)).onComplete(onSuccess(id -> {
      assertTrue(vertx1.deploymentIDs().contains(id));
      latch1.countDown();
    }));
    awaitLatch(latch1);

    ((VertxImpl)vertx2).failoverCompleteHandler((nodeID, haInfo, succeeded) -> fail("Should not failover here 2"));
    ((VertxImpl)vertx1).failoverCompleteHandler((nodeID, haInfo, succeeded) -> fail("Should not failover here 1"));
    ((VertxImpl)vertx1).haManager().simulateKill();
    vertx2.close().onComplete(ar -> {
      vertx.setTimer(500, tid -> {
        // Wait a bit in case failover happens
        assertEquals("Verticle should still be deployed here 1", 1, vertx1.deploymentIDs().size());
        assertTrue("Verticle should not failover here 2", vertx2.deploymentIDs().isEmpty());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testNonHADeployments() throws Exception {
    vertx1 = startVertx();
    vertx2 = startVertx();
    // Deploy an HA and a non HA deployment
    CountDownLatch latch1 = new CountDownLatch(2);
    vertx2.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHa(true)).onComplete(onSuccess(id -> {
      assertTrue(vertx2.deploymentIDs().contains(id));
      latch1.countDown();
    }));
    vertx2.deployVerticle("java:" + HAVerticle2.class.getName(), new DeploymentOptions().setHa(false)).onComplete(onSuccess(id -> {
      assertTrue(vertx2.deploymentIDs().contains(id));
      latch1.countDown();
    }));
    awaitLatch(latch1);
    CountDownLatch latch2 = new CountDownLatch(1);
    ((VertxImpl)vertx1).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
      assertTrue(succeeded);
      latch2.countDown();
    });
    ((VertxImpl)vertx2).haManager().simulateKill();
    awaitLatch(latch2);

    assertTrue(vertx1.deploymentIDs().size() == 1);
    String depID = vertx1.deploymentIDs().iterator().next();
    assertTrue(((VertxInternal) vertx1).deploymentManager().deployment(depID).deployment().identifier().equals("java:" + HAVerticle1.class.getName()));
  }

  @Test
  public void testCloseRemovesFromCluster() throws Exception {
    vertx1 = startVertx();
    vertx2 = startVertx();
    vertx3 = startVertx();
    CountDownLatch latch1 = new CountDownLatch(1);
    vertx3.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHa(true)).onComplete(onSuccess(id -> {
      assertTrue(vertx3.deploymentIDs().contains(id));
      latch1.countDown();
    }));
    awaitLatch(latch1);
    CountDownLatch latch2 = new CountDownLatch(1);
    // Close vertx2 - this should not then participate in failover
    vertx2.close().onComplete(ar -> {
      ((VertxImpl)vertx1).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
        assertTrue(succeeded);
        latch2.countDown();
      });
      ((VertxImpl) vertx3).haManager().simulateKill();
    });

    awaitLatch(latch2);

    assertTrue(vertx1.deploymentIDs().size() == 1);
    String depID = vertx1.deploymentIDs().iterator().next();
    assertTrue(((VertxInternal) vertx1).deploymentManager().deployment(depID).deployment().identifier().equals("java:" + HAVerticle1.class.getName()));
  }

  @Test
  public void testQuorumWithHaGroups() throws Exception {

    vertx1 = startVertx("group1", 2);
    vertx2 = startVertx("group2", 2);

    vertx1.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHa(true)).onComplete(onSuccess(id -> {
      assertTrue(vertx1.deploymentIDs().contains(id));
    }));

    // Wait a little while
    Thread.sleep(500);
    //Should not be deployed yet
    assertTrue(vertx1.deploymentIDs().isEmpty());

    vertx3 = startVertx("group1", 2);
    // Now should deploy
    assertWaitUntil(() -> vertx1.deploymentIDs().size() == 1);

    vertx2.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHa(true)).onComplete(onSuccess(id -> {
      assertTrue(vertx2.deploymentIDs().contains(id));
    }));

    // Wait a little while
    Thread.sleep(500);
    //Should not be deployed yet
    assertTrue(vertx2.deploymentIDs().isEmpty());

    vertx4 = startVertx("group2", 2);
    // Now should deploy
    assertWaitUntil(() -> vertx2.deploymentIDs().size() == 1);

    // Noow stop vertx4
    CountDownLatch latch = new CountDownLatch(1);
    vertx4.close().onComplete(ar -> {
      latch.countDown();
    });

    awaitLatch(latch);
    assertWaitUntil(() -> vertx2.deploymentIDs().isEmpty());

    assertTrue(vertx1.deploymentIDs().size() == 1);

    CountDownLatch latch2 = new CountDownLatch(1);
    vertx3.close().onComplete(ar -> {
      latch2.countDown();
    });

    awaitLatch(latch2);

    assertWaitUntil(() -> vertx1.deploymentIDs().isEmpty());
  }

  protected Vertx startVertx() throws Exception {
    return startVertx(null, 1);
  }

  protected Vertx startVertx(int quorumSize) throws Exception {
    return startVertx(null, quorumSize);
  }

  protected Vertx startVertx(String haGroup, int quorumSize) throws Exception {
    return startVertx(haGroup, quorumSize, true);
  }

  protected Vertx startVertx(String haGroup, int quorumSize, boolean ha) throws Exception {
    VertxOptions options = new VertxOptions().setHAEnabled(ha);
    options.getEventBusOptions().setHost("localhost");
    if (ha) {
      options.setQuorumSize(quorumSize);
      if (haGroup != null) {
        options.setHAGroup(haGroup);
      }
    }
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Vertx> vertxRef = new AtomicReference<>();
    Vertx.builder()
      .with(options)
      .withClusterManager(getClusterManager())
      .buildClustered()
      .onComplete(onSuccess(vertx -> {
        vertxRef.set(vertx);
        latch.countDown();
      }));
    latch.await(2, TimeUnit.MINUTES);
    return vertxRef.get();
  }


  protected void checkDeploymentExists(int pos, String verticleName, DeploymentOptions options) {
    VertxImpl vi = (VertxImpl) vertices[pos];
    for (String deploymentID: vi.deploymentIDs()) {
      DeploymentContext dep = vi.deploymentManager().deployment(deploymentID);
      if (verticleName.equals(dep.deployment().identifier()) && options.toJson().equals(dep.deployment().options().toJson())) {
        return;
      }
    }
    fail("Can't find deployment for verticleName: " + verticleName + " on node " + pos);
  }

  protected void kill(int pos) {
    VertxImpl v = (VertxImpl) vertices[pos];
    v.executeBlocking(() -> {
      v.haManager().simulateKill();
      return null;
    }, false).onComplete(onSuccess(ar -> {
    }));
  }

  protected void closeVertices(Vertx... vertices) throws Exception {
    CountDownLatch latch = new CountDownLatch(vertices.length);
    for (int i = 0; i < vertices.length; i++) {
      if (vertices[i] != null) {
        vertices[i].close().onComplete(onSuccess(res -> {
          latch.countDown();
        }));
      } else {
        latch.countDown();
      }
    }
    awaitLatch(latch, 2, TimeUnit.MINUTES);
  }


}

