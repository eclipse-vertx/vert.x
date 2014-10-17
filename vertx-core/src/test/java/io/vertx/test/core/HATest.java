/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.test.core;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.Deployment;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HATest extends VertxTestBase {

  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  @Test
  public void testSimpleFailover() throws Exception {
    startNodes(2, new VertxOptions().setHAEnabled(true));
    DeploymentOptions options = new DeploymentOptions().setHA(true);
    JsonObject config = new JsonObject().putString("foo", "bar");
    options.setConfig(config);
    CountDownLatch latch = new CountDownLatch(1);
    vertices[0].deployVerticle("java:" + HAVerticle1.class.getName(), options, ar -> {
      assertTrue(ar.succeeded());
      assertEquals(1, vertices[0].deployments().size());
      assertEquals(0, vertices[1].deployments().size());
      latch.countDown();
    });
    awaitLatch(latch);
    kill(0);
    waitUntil(() -> vertices[1].deployments().size() == 1);
    checkDeploymentExists(1, "java:" + HAVerticle1.class.getName(), options);
  }

  @Test
  public void testQuorum() throws Exception {
    Vertx vertx1 = startVertx(2);
    DeploymentOptions options = new DeploymentOptions().setHA(true);
    JsonObject config = new JsonObject().putString("foo", "bar");
    options.setConfig(config);
    vertx1.deployVerticle("java:" + HAVerticle1.class.getName(), options, ar -> {
      assertTrue(ar.succeeded());
      assertTrue(vertx1.deployments().contains(ar.result()));
      testComplete();
    });
    // Shouldn't deploy until a quorum is obtained
    Thread.sleep(500);
    assertTrue(vertx1.deployments().isEmpty());
    Vertx vertx2 = startVertx(2);
    // Now should be deployed
    await();
    closeVertices(vertx1, vertx2);
  }

  @Rule
  public RepeatRule repeatRule = new RepeatRule();


  @Test
  public void testQuorumLost() throws Exception {
    Vertx vertx1 = startVertx(3);
    Vertx vertx2 = startVertx(3);
    Vertx vertx3 = startVertx(3);
    DeploymentOptions options = new DeploymentOptions().setHA(true);
    JsonObject config = new JsonObject().putString("foo", "bar");
    options.setConfig(config);
    vertx1.deployVerticle("java:" + HAVerticle1.class.getName(), options, ar -> {
      assertTrue(ar.succeeded());
      assertTrue(vertx1.deployments().contains(ar.result()));
      ;
    });
    vertx2.deployVerticle("java:" + HAVerticle2.class.getName(), options, ar -> {
      assertTrue(ar.succeeded());
      assertTrue(vertx2.deployments().contains(ar.result()));
      ;
    });
    waitUntil(() -> vertx1.deployments().size() == 1 && vertx2.deployments().size() == 1);
    // Now close vertx3 - quorum should then be lost and verticles undeployed
    CountDownLatch latch = new CountDownLatch(1);
    vertx3.close(ar -> {
      latch.countDown();
    });
    awaitLatch(latch);
    waitUntil(() -> vertx1.deployments().isEmpty() && vertx2.deployments().isEmpty());
    // Now re-instate the quorum
    Vertx vertx4 = startVertx(3);
    waitUntil(() -> vertx1.deployments().size() == 1 && vertx2.deployments().size() == 1);

    closeVertices(vertx1, vertx2, vertx4);
  }

  @Test
  public void testCleanCloseNoFailover() throws Exception {
    Vertx vertx1 = startVertx();
    Vertx vertx2 = startVertx();
    DeploymentOptions options = new DeploymentOptions().setHA(true);
    JsonObject config = new JsonObject().putString("foo", "bar");
    options.setConfig(config);
    vertx2.deployVerticle("java:" + HAVerticle1.class.getName(), options, ar -> {
      assertTrue(ar.succeeded());
    });
    waitUntil(() -> vertx2.deployments().size() == 1);
    ((VertxInternal)vertx1).failoverCompleteHandler(succeeded -> {
      fail("Should not be called");
    });
    vertx2.close(ar -> {
      vertx.setTimer(500, tid -> {
        // Wait a bit in case failover happens
        testComplete();
      });
    });
    await();
    closeVertices(vertx1);
  }

  @Test
  public void testFailureInFailover() throws Exception {
    Vertx vertx1 = startVertx();
    Vertx vertx2 = startVertx();
    Vertx vertx3 = startVertx();
    CountDownLatch latch1 = new CountDownLatch(1);
    vertx1.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHA(true), ar -> {
      assertTrue(ar.succeeded());
      assertTrue(vertx1.deployments().contains(ar.result()));
      latch1.countDown();
    });
    awaitLatch(latch1);
    ((VertxInternal)vertx2).failDuringFailover(true);
    ((VertxInternal)vertx3).failDuringFailover(true);
    CountDownLatch latch2 = new CountDownLatch(1);
    ((VertxInternal)vertx2).failoverCompleteHandler(succeeded -> {
      assertFalse(succeeded);
      latch2.countDown();
    });
    ((VertxInternal)vertx3).failoverCompleteHandler(succeeded -> {
      assertFalse(succeeded);
      latch2.countDown();
    });
    ((VertxInternal)vertx1).simulateKill();
    awaitLatch(latch2);

    // Now try again - this time failover should work

    assertTrue(vertx2.deployments().isEmpty());
    assertTrue(vertx3.deployments().isEmpty());
    ((VertxInternal)vertx2).failDuringFailover(false);
    CountDownLatch latch3 = new CountDownLatch(1);
    ((VertxInternal)vertx2).failoverCompleteHandler(succeeded -> {
      assertTrue(succeeded);
      latch3.countDown();
    });
    ((VertxInternal)vertx3).simulateKill();
    awaitLatch(latch3);
    waitUntil(() -> vertx2.deployments().size() == 1);
    closeVertices(vertx1, vertx2, vertx3);
  }

  @Test
  public void testHaGroups() throws Exception {
    Vertx vertx1 = startVertx("group1", 1);
    Vertx vertx2 = startVertx("group1", 1);
    Vertx vertx3 = startVertx("group2", 1);
    Vertx vertx4 = startVertx("group2", 1);
    CountDownLatch latch1 = new CountDownLatch(2);
    vertx1.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHA(true), ar -> {
      assertTrue(ar.succeeded());
      assertTrue(vertx1.deployments().contains(ar.result()));
      latch1.countDown();
    });
    vertx3.deployVerticle("java:" + HAVerticle2.class.getName(), new DeploymentOptions().setHA(true), ar -> {
      assertTrue(ar.succeeded());
      assertTrue(vertx3.deployments().contains(ar.result()));
      latch1.countDown();
    });
    awaitLatch(latch1);
    CountDownLatch latch2 = new CountDownLatch(1);
    ((VertxInternal)vertx1).failoverCompleteHandler(succeeded -> {
      fail("Should not failover here");
    });
    ((VertxInternal)vertx2).failoverCompleteHandler(succeeded -> {
      fail("Should not failover here");
    });
    ((VertxInternal)vertx4).failoverCompleteHandler(succeeded -> {
      assertTrue(succeeded);
      latch2.countDown();
    });
    ((VertxInternal)vertx3).simulateKill();
    awaitLatch(latch2);
    assertTrue(vertx4.deployments().size() == 1);
    CountDownLatch latch3 = new CountDownLatch(1);
    ((VertxInternal)vertx2).failoverCompleteHandler(succeeded -> {
      assertTrue(succeeded);
      latch3.countDown();
    });
    ((VertxInternal)vertx4).failoverCompleteHandler(succeeded -> {
      fail("Should not failover here");
    });
    ((VertxInternal)vertx1).simulateKill();
    awaitLatch(latch3);
    assertTrue(vertx2.deployments().size() == 1);
    closeVertices(vertx1, vertx2, vertx3, vertx4);

  }

  @Test
  public void testNonHADeployments() throws Exception {
    Vertx vertx1 = startVertx();
    Vertx vertx2 = startVertx();
    // Deploy an HA and a non HA deployment
    CountDownLatch latch1 = new CountDownLatch(2);
    vertx2.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHA(true), ar -> {
      assertTrue(ar.succeeded());
      assertTrue(vertx2.deployments().contains(ar.result()));
      latch1.countDown();
    });
    vertx2.deployVerticle("java:" + HAVerticle2.class.getName(), new DeploymentOptions().setHA(false), ar -> {
      assertTrue(ar.succeeded());
      assertTrue(vertx2.deployments().contains(ar.result()));
      latch1.countDown();
    });
    awaitLatch(latch1);
    CountDownLatch latch2 = new CountDownLatch(1);
    ((VertxInternal)vertx1).failoverCompleteHandler(succeeded -> {
      assertTrue(succeeded);
      latch2.countDown();
    });
    ((VertxInternal)vertx2).simulateKill();
    awaitLatch(latch2);

    assertTrue(vertx1.deployments().size() == 1);
    String depID = vertx1.deployments().iterator().next();
    assertTrue(((VertxInternal) vertx1).getDeployment(depID).identifier().equals("java:" + HAVerticle1.class.getName()));
    closeVertices(vertx1, vertx2);
  }

  @Test
  public void testCloseRemovesFromCluster() throws Exception {
    Vertx vertx1 = startVertx();
    Vertx vertx2 = startVertx();
    Vertx vertx3 = startVertx();
    CountDownLatch latch1 = new CountDownLatch(1);
    vertx3.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHA(true), ar -> {
      assertTrue(ar.succeeded());
      assertTrue(vertx3.deployments().contains(ar.result()));
      latch1.countDown();
    });
    awaitLatch(latch1);
    CountDownLatch latch2 = new CountDownLatch(1);
    // Close vertx2 - this should not then participate in failover
    vertx2.close(ar -> {
      ((VertxInternal) vertx1).failoverCompleteHandler(succeeded -> {
        assertTrue(succeeded);
        latch2.countDown();
      });
      ((VertxInternal) vertx3).simulateKill();
    });

    awaitLatch(latch2);

    assertTrue(vertx1.deployments().size() == 1);
    String depID = vertx1.deployments().iterator().next();
    assertTrue(((VertxInternal) vertx1).getDeployment(depID).identifier().equals("java:" + HAVerticle1.class.getName()));
    closeVertices(vertx1, vertx3);
  }

  @Test
  public void testQuorumWithHaGroups() throws Exception {

    Vertx vertx1 = startVertx("group1", 2);
    Vertx vertx2 = startVertx("group2", 2);

    vertx1.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHA(true), ar -> {
      assertTrue(ar.succeeded());
      assertTrue(vertx1.deployments().contains(ar.result()));
    });

    // Wait a little while
    Thread.sleep(500);
    //Should not be deployed yet
    assertTrue(vertx1.deployments().isEmpty());

    Vertx vertx3 = startVertx("group1", 2);
    // Now should deploy
    waitUntil(() -> vertx1.deployments().size() == 1);

    vertx2.deployVerticle("java:" + HAVerticle1.class.getName(), new DeploymentOptions().setHA(true), ar -> {
      assertTrue(ar.succeeded());
      assertTrue(vertx2.deployments().contains(ar.result()));
    });

    // Wait a little while
    Thread.sleep(500);
    //Should not be deployed yet
    assertTrue(vertx2.deployments().isEmpty());

    Vertx vertx4 = startVertx("group2", 2);
    // Now should deploy
    waitUntil(() -> vertx2.deployments().size() == 1);

    // Noow stop vertx4
    CountDownLatch latch = new CountDownLatch(1);
    vertx4.close(ar -> {
      latch.countDown();
    });

    awaitLatch(latch);
    waitUntil(() -> vertx2.deployments().isEmpty());

    assertTrue(vertx1.deployments().size() == 1);

    CountDownLatch latch2 = new CountDownLatch(1);
    vertx3.close(ar -> {
      latch2.countDown();
    });

    awaitLatch(latch2);

    waitUntil(() -> vertx1.deployments().isEmpty());
    closeVertices(vertx1, vertx2);
  }

  protected Vertx startVertx() throws Exception {
    return startVertx(null, 1);
  }

  protected Vertx startVertx(int quorumSize) throws Exception {
    return startVertx(null, quorumSize);
  }

  protected Vertx startVertx(String haGroup, int quorumSize) throws Exception {
    VertxOptions options = new VertxOptions().setHAEnabled(true).setQuorumSize(quorumSize).setHAGroup(haGroup).setClustered(true).
      setClusterHost("localhost").setClusterManager(getClusterManager());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Vertx> vertxRef = new AtomicReference<>();
    Vertx.vertxAsync(options, onSuccess(vertx -> {
      vertxRef.set(vertx);
      latch.countDown();
    }));
    latch.await(2, TimeUnit.MINUTES);
    return vertxRef.get();
  }


  protected void checkDeploymentExists(int pos, String verticleName, DeploymentOptions options) {
    VertxInternal vi = (VertxInternal)vertices[pos];
    for (String deploymentID: vi.deployments()) {
      Deployment dep = vi.getDeployment(deploymentID);
      if (verticleName.equals(dep.identifier()) && options.equals(dep.deploymentOptions())) {
        return;
      }
    }
    fail("Can't find deployment for verticleName: " + verticleName + " on node " + pos);
  }

  protected void kill(int pos) {
    VertxInternal v = (VertxInternal)vertices[pos];
    v.executeBlocking(() -> {
      v.simulateKill();
      return null;
    }, ar -> {
      assertTrue(ar.succeeded());
    });
  }

  protected void closeVertices(Vertx... vertices) throws Exception {
    CountDownLatch latch = new CountDownLatch(vertices.length);
    for (int i = 0; i < vertices.length; i++) {
      vertices[i].close(onSuccess(res -> {
        latch.countDown();
      }));
    }
    latch.await(2, TimeUnit.MINUTES);
  }


}

