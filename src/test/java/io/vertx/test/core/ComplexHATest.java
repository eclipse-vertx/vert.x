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
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.impl.Deployment;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ComplexHATest extends VertxTestBase {

  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

  private Random random = new Random();

  protected final int maxVerticlesPerNode = 20;
  protected Set<Deployment>[] deploymentSnapshots;
  protected volatile int totDeployed;
  protected volatile int killedNode;
  protected List<Integer> aliveNodes;

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
      await(10, TimeUnit.MINUTES);
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
    int index = 0;
    for (int pos: aliveNodes) {
      Vertx v = vertices[pos];
      int numToDeploy = numbersToDeploy.get(index);
      index++;
      for (int j = 0; j < numToDeploy; j++) {
        JsonObject config = new JsonObject();
        config.put("foo", TestUtils.randomAlphaString(100));
        DeploymentOptions options = new DeploymentOptions().setHa(true).setConfig(config);
        String verticleName = "java:io.vertx.test.core.HAVerticle" + (random.nextInt(3) + 1);
        v.deployVerticle(verticleName, options, ar -> {
          assertTrue(ar.succeeded());
          deployCount.incrementAndGet();
        });
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
      int ii = pos;
      for (int j = 0; j < numToUnDeploy; j++) {
        int depPos = random.nextInt(deployed.size());
        String depID = deployed.remove(depPos);
        toUndeploy++;
        v.undeploy(depID, onSuccess(d -> {
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
    doEventLoopWaitUntil(start, supplier, runner);
  }

  private void doEventLoopWaitUntil(long start, BooleanSupplier supplier, Runnable runner) {
    long now = System.currentTimeMillis();
    if (now - start > 10000) {
      fail("Timedout in waiting until");
    } else {
      if (supplier.getAsBoolean()) {
        runner.run();
      } else {
        vertx.setTimer(1, tid -> doEventLoopWaitUntil(start, supplier, runner));
      }
    }
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
    Set<Deployment> snapshot = new ConcurrentHashSet<>();
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
    v.executeBlocking(fut -> {
      v.simulateKill();
      fut.complete();
    }, ar -> {
      assertTrue(ar.succeeded());
    });

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
        if (curr.verticleIdentifier().equals(prev.verticleIdentifier()) && curr.deploymentOptions().equals(prev.deploymentOptions())) {
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
