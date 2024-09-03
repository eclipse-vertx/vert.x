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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.deployment.Deployment;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ComplexHATest extends VertxTestBase {

  @Override
  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  private final Random random = new Random();

  protected final int maxVerticlesPerNode = 20;
  protected Set<Deployment>[] deploymentSnapshots;
  protected volatile int totDeployed;
  protected volatile int killedNode;
  protected List<Integer> aliveNodes;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    deploymentSnapshots = null;
    totDeployed = 0;
    killedNode = 0;
    aliveNodes = null;
  }

  @Test
  @Repeat(times = 10)
  public void testComplexFailover() {
    try {
      int numNodes = 8;
      createNodes(numNodes);
      deployRandomVerticles(() -> {
        killRandom();
      });
      await(2, TimeUnit.MINUTES);
    } catch (Throwable t) {
      // Need to explicitly catch throwables in repeats or they will be swallowed
      t.printStackTrace();
      // Don't forget to fail!
      fail(t.getMessage());
    }
  }

  protected void deployRandomVerticles(Runnable runner) {
    int toDeploy = 0;
    AtomicInteger deployCount = new AtomicInteger();
    List<Integer> numbersToDeploy = new ArrayList<>();
    for (int i = 0; i < aliveNodes.size(); i++) {
      int numToDeploy = random.nextInt(maxVerticlesPerNode + 1);
      numbersToDeploy.add(numToDeploy);
      toDeploy += numToDeploy;
    }
    List<Class<? extends AbstractVerticle>> classes = List.of(HAVerticle1.class, HAVerticle2.class, HAVerticle3.class);
    int index = 0;
    for (int pos: aliveNodes) {
      Vertx v = vertices[pos];
      int numToDeploy = numbersToDeploy.get(index);
      index++;
      for (int j = 0; j < numToDeploy; j++) {
        JsonObject config = new JsonObject();
        config.put("foo", TestUtils.randomAlphaString(100));
        DeploymentOptions options = new DeploymentOptions().setHa(true).setConfig(config);
        String verticleName = "io.vertx.tests.ha.HAVerticle" + (1 + random.nextInt(3));
        v.deployVerticle(verticleName, options).onComplete(onSuccess(v2 -> {
          deployCount.incrementAndGet();
        }));
      }
    }
    int ttoDeploy = toDeploy;
    eventLoopWaitUntil(() -> ttoDeploy == deployCount.get(), () -> {
      totDeployed += ttoDeploy;
      runner.run();
    });
  }

  protected void undeployRandomVerticles(Runnable runner) {
    int toUndeploy = 0;
    AtomicInteger undeployCount = new AtomicInteger();
    for (int pos: aliveNodes) {
      Vertx v = vertices[pos];
      int deployedNum = v.deploymentIDs().size();
      int numToUnDeploy = random.nextInt(deployedNum + 1);
      List<String> deployed = new ArrayList<>(v.deploymentIDs());
      for (int j = 0; j < numToUnDeploy; j++) {
        int depPos = random.nextInt(deployed.size());
        String depID = deployed.remove(depPos);
        toUndeploy++;
        v.undeploy(depID).onComplete(onSuccess(d -> {
          undeployCount.incrementAndGet();
        }));
      }
    }
    int totUndeployed = toUndeploy;
    eventLoopWaitUntil(() -> totUndeployed == undeployCount.get(), () -> {
      totDeployed -= totUndeployed;
      runner.run();
    });
  }

  private void eventLoopWaitUntil(BooleanSupplier supplier, Runnable runner) {
    long start = System.currentTimeMillis();
    // Use a thread and not the event loop to avoid interference
    new Thread(() -> {
      while (!supplier.getAsBoolean()) {
        if (System.currentTimeMillis() - start > 10000) {
          fail("Timedout in waiting until");
          return;
        }
        try {
          Thread.sleep(1);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      runner.run();
    }).start();
  }

  protected void takeDeploymentSnapshots() {
    for (int i = 0; i < vertices.length; i++) {
      VertxInternal v = (VertxInternal)vertices[i];
      if (!v.isKilled()) {
        deploymentSnapshots[i] = takeDeploymentSnapshot(i);
      }
    }
  }

  protected Set<Deployment> takeDeploymentSnapshot(int pos) {
    Set<Deployment> snapshot = ConcurrentHashMap.newKeySet();
    VertxInternal v = (VertxInternal)vertices[pos];
    for (String depID: v.deploymentIDs()) {
      snapshot.add(v.getDeployment(depID));
    }
    return snapshot;
  }

  protected void kill(int pos) {
    // Save the deploymentIDs first
    takeDeploymentSnapshots();
    VertxInternal v = (VertxInternal)vertices[pos];
    killedNode = pos;
    v.executeBlocking(() -> {
      v.simulateKill();
      return null;
    }, false).onComplete(onSuccess(v2 -> {}));

  }

  protected void createNodes(int nodes) {
    startNodes(nodes, new VertxOptions().setHAEnabled(true));
    aliveNodes = new CopyOnWriteArrayList<>();
    for (int i = 0; i < nodes; i++) {
      aliveNodes.add(i);
      int pos = i;
      ((VertxInternal)vertices[i]).failoverCompleteHandler((nodeID, haInfo, succeeded) -> {
        failedOverOnto(pos);
      });
    }
    deploymentSnapshots = new Set[nodes];
  }

  protected void failedOverOnto(int node) {
    checkDeployments();
    checkHasDeployments(node, killedNode);
    if (aliveNodes.size() > 1) {
      undeployRandomVerticles(() -> {
        deployRandomVerticles(() -> {
          killRandom();
        });
      });
    } else {
      testComplete();
    }
  }

  protected void checkDeployments() {
    int totalDeployed = 0;
    for (int i = 0; i < vertices.length; i++) {
      VertxInternal v = (VertxInternal)vertices[i];
      if (!v.isKilled()) {
        totalDeployed += checkHasDeployments(i, i);
      }
    }
    assertEquals(totDeployed, totalDeployed);
  }

  protected int checkHasDeployments(int pos, int prevPos) {
    Set<Deployment> prevSet = deploymentSnapshots[prevPos];
    Set<Deployment> currSet = takeDeploymentSnapshot(pos);
    for (Deployment prev: prevSet) {
      boolean contains = false;
      for (Deployment curr: currSet) {
        if (curr.identifier().equals(prev.identifier()) && curr.deploymentOptions().toJson().equals(prev.deploymentOptions().toJson())) {
          contains = true;
          break;
        }
      }
      assertTrue(contains);
    }
    return currSet.size();
  }

  protected void killRandom() {
    int i = random.nextInt(aliveNodes.size());
    int pos = aliveNodes.get(i);
    aliveNodes.remove(i);
    kill(pos);
  }

}
