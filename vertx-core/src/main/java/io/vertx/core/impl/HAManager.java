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

package io.vertx.core.impl;

import io.vertx.core.*;
import io.vertx.core.internal.deployment.Deployment;
import io.vertx.core.internal.deployment.DeploymentContext;
import io.vertx.core.internal.deployment.DeploymentManager;
import io.vertx.core.impl.verticle.VerticleManager;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 *
 * Handles HA
 *
 * We compute failover and whether there is a quorum synchronously as we receive nodeAdded and nodeRemoved events
 * from the cluster manager.
 *
 * It's vital that this is done synchronously as the cluster manager only guarantees that the set of nodes retrieved
 * from getNodes() is the same for each node in the cluster when processing the exact same nodeAdded/nodeRemoved
 * event.
 *
 * As HA modules are deployed, if a quorum has been attained they are deployed immediately, otherwise the deployment
 * information is added to a list.
 *
 * Periodically we check the value of attainedQuorum and if true we deploy any HA deploymentIDs waiting for a quorum.
 *
 * If false, we check if there are any HA deploymentIDs current deployed, and if so undeploy them, and add them to the list
 * of deploymentIDs waiting for a quorum.
 *
 * By doing this check periodically we can avoid race conditions resulting in modules being deployed after a quorum has
 * been lost, and without having to resort to exclusive locking which is actually quite tricky here, and prone to
 * deadlock·
 *
 * We maintain a clustered map where the key is the node id and the value is some stringified JSON which describes
 * the group of the cluster and an array of the HA modules deployed on that node.
 *
 * There is an entry in the map for each node of the cluster.
 *
 * When a node joins the cluster or an HA module is deployed or undeployed that entry is updated.
 *
 * When a node leaves the cluster cleanly, it removes it's own entry before leaving.
 *
 * When the cluster manager sends us an event to say a node has left the cluster we check if its entry in the cluster
 * map is there, and if so we infer a clean close has happened and no failover will occur.
 *
 * If the map entry is there it implies the node died suddenly. In that case each node of the cluster must compute
 * whether it is the failover node for the failed node.
 *
 * First each node of the cluster determines whether it is in the same group as the failed node, if not then it will not
 * be a candidate for the failover node. Nodes in the cluster only failover to other nodes in the same group.
 *
 * If the node is in the same group then the node takes the UUID of the failed node, computes the hash-code and chooses
 * a node from the list of nodes in the cluster by taking the hash-code modulo the number of nodes as an index to the
 * list of nodes.
 *
 * The cluster manager guarantees each node in the cluster sees the same set of nodes for each membership event that is
 * processed. Therefore it is guaranteed that each node in the cluster will compute the same value. It is critical that
 * any cluster manager implementation provides this guarantee!
 *
 * Once the value has been computed, it is compared to the current node, and if it is the same the current node
 * assumes failover for the failed node.
 *
 * During failover the failover node deploys all the HA modules from the failed node, as described in the JSON with the
 * same values of config and instances.
 *
 * Once failover is complete the failover node removes the cluster map entry for the failed node.
 *
 * If the failover node itself fails while it is processing failover for another node, then this is also checked by
 * other nodes when they detect the failure of the second node.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HAManager {

  private static final Logger log = LoggerFactory.getLogger(HAManager.class);

  private static final long QUORUM_CHECK_PERIOD = 1000;

  private final VertxInternal vertx;
  private final DeploymentManager deploymentManager;
  private final VerticleManager verticleFactoryManager;
  private final ClusterManager clusterManager;
  private final int quorumSize;
  private final String group;
  private final JsonObject haInfo;
  private final Map<String, String> clusterMap;
  private final String nodeID;
  private final Queue<Runnable> toDeployOnQuorum = new ConcurrentLinkedQueue<>();

  private long quorumTimerID;
  private long checkQuorumTimerID = -1L;
  private volatile boolean attainedQuorum;
  private volatile FailoverCompleteHandler failoverCompleteHandler;
  private volatile boolean failDuringFailover;
  private volatile boolean stopped;
  private volatile boolean killed;

  public HAManager(VertxInternal vertx, DeploymentManager deploymentManager, VerticleManager verticleFactoryManager, ClusterManager clusterManager,
                   Map<String, String> clusterMap, int quorumSize, String group) {
    this.vertx = vertx;
    this.deploymentManager = deploymentManager;
    this.verticleFactoryManager = verticleFactoryManager;
    this.clusterManager = clusterManager;
    this.clusterMap = clusterMap;
    this.quorumSize = quorumSize;
    this.group = group;
    this.haInfo = new JsonObject().put("verticles", new JsonArray()).put("group", this.group);
    this.nodeID = clusterManager.getNodeId();
  }

  /**
   * Initialize the ha manager, i.e register the node listener to propagates the node events and
   * start the quorum timer. The quorum will be checked as well.
   */
  void init() {
    synchronized (haInfo) {
      clusterMap.put(nodeID, haInfo.encode());
    }
    clusterManager.nodeListener(new NodeListener() {
      @Override
      public void nodeAdded(String nodeID) {
        HAManager.this.nodeAdded(nodeID);
      }
      @Override
      public void nodeLeft(String leftNodeID) {
        HAManager.this.nodeLeft(leftNodeID);
      }
    });
    quorumTimerID = vertx.setPeriodic(QUORUM_CHECK_PERIOD, tid -> checkHADeployments());
    // Call check quorum to compute whether we have an initial quorum
    synchronized (this) {
      checkQuorum();
    }
  }

  // Remove the information on the deployment from the cluster - this is called when an HA module is undeployed
  public void removeFromHA(String depID) {
    DeploymentContext deployment = deploymentManager.deployment(depID);
    if (deployment == null || !deployment.deployment().options().isHa()) {
      return;
    }
    synchronized (haInfo) {
      JsonArray haMods = haInfo.getJsonArray("verticles");
      Iterator<Object> iter = haMods.iterator();
      while (iter.hasNext()) {
        Object obj = iter.next();
        JsonObject mod = (JsonObject) obj;
        if (mod.getString("dep_id").equals(depID)) {
          iter.remove();
        }
      }
      clusterMap.put(nodeID, haInfo.encode());
    }
  }

  public void addDataToAHAInfo(String key, JsonObject value) {
    synchronized (haInfo) {
      haInfo.put(key, value);
      clusterMap.put(nodeID, haInfo.encode());
    }
  }
  // Deploy an HA verticle
  public void deployVerticle(String verticleName, DeploymentOptions deploymentOptions, Promise<DeploymentContext> doneHandler) {
    if (attainedQuorum) {
      doDeployVerticle(verticleName, deploymentOptions)
              .onComplete(doneHandler);
    } else {
      log.info("Quorum not attained. Deployment of verticle will be delayed until there's a quorum.");
      addToHADeployList(verticleName, deploymentOptions, doneHandler);
    }
  }


  public void stop() {
    if (!stopped) {
      if (clusterManager.isActive()) {
        clusterMap.remove(nodeID);
      }
      long timerID = checkQuorumTimerID;
      if (timerID >= 0L) {
        checkQuorumTimerID = -1L;
        vertx.cancelTimer(timerID);
      }
      vertx.cancelTimer(quorumTimerID);
      stopped = true;
    }
  }

  public void simulateKill() {
    if (!stopped) {
      killed = true;
      CountDownLatch latch = new CountDownLatch(1);
      Promise<Void> promise = Promise.promise();
      clusterManager.leave(promise);
      promise.future()
        .onFailure(t -> log.error("Failed to leave cluster", t))
        .onComplete(ar -> latch.countDown());
      long timerID = checkQuorumTimerID;
      if (timerID >= 0L) {
        checkQuorumTimerID = -1L;
        vertx.cancelTimer(timerID);
      }
      vertx.cancelTimer(quorumTimerID);

      boolean interrupted = false;
      try {
        long remainingNanos = MINUTES.toNanos(1);
        long end = System.nanoTime() + remainingNanos;

        while (true) {
          try {
            latch.await(remainingNanos, NANOSECONDS);
            break;
          } catch (InterruptedException e) {
            interrupted = true;
            remainingNanos = end - System.nanoTime();
          }
        }
      } finally {
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }

      stopped = true;
    }
  }

  public void setFailoverCompleteHandler(FailoverCompleteHandler failoverCompleteHandler) {
    this.failoverCompleteHandler = failoverCompleteHandler;
  }

  public boolean isKilled() {
    return killed;
  }

  // For testing:
  public void failDuringFailover(boolean fail) {
    failDuringFailover = fail;
  }

  private Future<DeploymentContext> doDeployVerticle(String verticleName, DeploymentOptions deploymentOptions) {
    return verticleFactoryManager
            .deployVerticle(verticleName, deploymentOptions)
            .compose(deployment -> vertx
                    .executeBlocking(() -> {
                      // Tell the other nodes of the cluster about the verticle for HA purposes
                      addToHA(deployment.id(), verticleName, deploymentOptions);
                      return deployment;
                    }, false));
  }

  // A node has joined the cluster
  // synchronize this in case the cluster manager is naughty and calls it concurrently
  private synchronized void nodeAdded(final String nodeID) {
    addHaInfoIfLost();
    // This is not ideal but we need to wait for the group information to appear - and this will be shortly
    // after the node has been added
    checkQuorumWhenAdded(nodeID, System.currentTimeMillis());
  }

  // A node has left the cluster
  // synchronize this in case the cluster manager is naughty and calls it concurrently
  private synchronized void nodeLeft(String leftNodeID) {
    addHaInfoIfLost();

    checkQuorum();
    if (attainedQuorum) {
      // Check for failover
      String sclusterInfo = clusterMap.get(leftNodeID);

      if (sclusterInfo == null) {
        // Clean close - do nothing
      } else {
        JsonObject clusterInfo = new JsonObject(sclusterInfo);
        checkFailover(leftNodeID, clusterInfo);
      }

      // We also check for and potentially resume any previous failovers that might have failed
      // We can determine this if there any ids in the cluster map which aren't in the node list
      List<String> nodes = clusterManager.getNodes();

      for (Map.Entry<String, String> entry: clusterMap.entrySet()) {
        if (!leftNodeID.equals(entry.getKey()) && !nodes.contains(entry.getKey())) {
          JsonObject haInfo = new JsonObject(entry.getValue());
          checkFailover(entry.getKey(), haInfo);
        }
      }
    }
  }

  private void addHaInfoIfLost() {
    if (clusterManager.getNodes().contains(nodeID) && !clusterMap.containsKey(nodeID)) {
      synchronized (haInfo) {
        clusterMap.put(nodeID, haInfo.encode());
      }
    }
  }

  private synchronized void checkQuorumWhenAdded(final String nodeID, final long start) {
    if (!stopped) {
      if (clusterMap.containsKey(nodeID)) {
        checkQuorum();
      } else {
        checkQuorumTimerID = vertx.setTimer(200, tid -> {
          checkQuorumTimerID = -1L;
          if (!stopped) {
            // This can block on a monitor so it needs to run as a worker
            vertx.<Void>executeBlockingInternal(() -> {
              if (System.currentTimeMillis() - start > 10000) {
                log.warn("Timed out waiting for group information to appear");
              } else {
                // Remove any context we have here (from the timer) otherwise will screw things up when verticles are deployed
                ((VertxImpl)vertx).executeIsolated(v -> {
                  checkQuorumWhenAdded(nodeID, start);
                });
              }
              return null;
            });
          }
        });
      }
    }
  }

  // Check if there is a quorum for our group
  private void checkQuorum() {
    if (quorumSize == 0) {
      this.attainedQuorum = true;
    } else {
      List<String> nodes = clusterManager.getNodes();
      int count = 0;
      for (String node : nodes) {
        String json = clusterMap.get(node);
        if (json != null) {
          JsonObject clusterInfo = new JsonObject(json);
          String group = clusterInfo.getString("group");
          if (group.equals(this.group)) {
            count++;
          }
        } else if (!attainedQuorum) {
          checkQuorumWhenAdded(node, System.currentTimeMillis());
        }
      }
      boolean attained = count >= quorumSize;
      if (!attainedQuorum && attained) {
        // A quorum has been attained so we can deploy any currently undeployed HA deploymentIDs
        log.info("A quorum has been obtained. Any deploymentIDs waiting on a quorum will now be deployed");
        this.attainedQuorum = true;
      } else if (attainedQuorum && !attained) {
        // We had a quorum but we lost it - we must undeploy any HA deploymentIDs
        log.info("There is no longer a quorum. Any HA deploymentIDs will be undeployed until a quorum is re-attained");
        this.attainedQuorum = false;
      }
    }
  }

  // Add some information on a deployment in the cluster so other nodes know about it
  private void addToHA(String deploymentID, String verticleName, DeploymentOptions deploymentOptions) {
    String encoded;
    synchronized (haInfo) {
      JsonObject verticleConf = new JsonObject().put("dep_id", deploymentID);
      verticleConf.put("verticle_name", verticleName);
      verticleConf.put("options", deploymentOptions.toJson());
      JsonArray haMods = haInfo.getJsonArray("verticles");
      haMods.add(verticleConf);
      encoded = haInfo.encode();
      clusterMap.put(nodeID, encoded);
    }
  }

  // Add the deployment to an internal list of deploymentIDs - these will be executed when a quorum is attained
  private void addToHADeployList(String verticleName, DeploymentOptions deploymentOptions, Promise<DeploymentContext> doneHandler) {
    toDeployOnQuorum.add(() -> {
      ((VertxImpl)vertx).executeIsolated(v -> {
        deployVerticle(verticleName, deploymentOptions, doneHandler);
      });
    });
   }

  private void checkHADeployments() {
    try {
      if (attainedQuorum) {
        deployHADeployments();
      } else {
        undeployHADeployments();
      }
    } catch (Throwable t) {
      log.error("Failed when checking HA deploymentIDs", t);
    }
  }

  // Undeploy any HA deploymentIDs now there is no quorum
  private void undeployHADeployments() {
    for (DeploymentContext deployment: deploymentManager.deployments()) {
      if (deployment != null) {
        String identifier = deployment.deployment().identifier();
        if (deployment.deployment().options().isHa()) {
          ((VertxImpl)vertx).executeIsolated(v -> {
            deploymentManager.undeploy(deployment.id()).onComplete(result -> {
              if (result.succeeded()) {
                log.info("Successfully undeployed HA deployment " + deployment.id() + "-" + identifier + " as there is no quorum");
                Future<DeploymentContext> fut = Future.future(promise -> addToHADeployList(identifier, deployment.deployment().options(), promise));
                fut.onComplete(ar -> {
                  if (ar.succeeded()) {
                    log.info("Successfully redeployed verticle " + identifier + " after quorum was re-attained");
                  } else {
                    log.error("Failed to redeploy verticle " + identifier + " after quorum was re-attained", ar.cause());
                  }
                });
              } else {
                log.error("Failed to undeploy deployment on lost quorum", result.cause());
              }
            });
          });
        }
      }
    }
  }

  // Deploy any deploymentIDs that are waiting for a quorum
  private void deployHADeployments() {
    int size = toDeployOnQuorum.size();
    if (size != 0) {
      log.info("There are " + size + " HA deploymentIDs waiting on a quorum. These will now be deployed");
      Runnable task;
      while ((task = toDeployOnQuorum.poll()) != null) {
        try {
          task.run();
        } catch (Throwable t) {
          log.error("Failed to run redeployment task", t);
        }
      }
    }
  }

  // Handle failover
  private void checkFailover(String failedNodeID, JsonObject theHAInfo) {
    try {
      JsonArray deployments = theHAInfo.getJsonArray("verticles");
      String group = theHAInfo.getString("group");
      String chosen = chooseHashedNode(group, failedNodeID.hashCode());
      if (chosen != null && chosen.equals(this.nodeID)) {
        if (deployments != null && deployments.size() != 0) {
          log.info("node" + nodeID + " says: Node " + failedNodeID + " has failed. This node will deploy " + deployments.size() + " deploymentIDs from that node.");
          for (Object obj: deployments) {
            JsonObject app = (JsonObject)obj;
            processFailover(app);
          }
        }
        // Failover is complete! We can now remove the failed node from the cluster map
        clusterMap.remove(failedNodeID);
        runOnContextAndWait(() -> {
          if (failoverCompleteHandler != null) {
            failoverCompleteHandler.handle(failedNodeID, theHAInfo, true);
          }
        });
      }
    } catch (Throwable t) {
      log.error("Failed to handle failover", t);
      runOnContextAndWait(() -> {
        if (failoverCompleteHandler != null) {
          failoverCompleteHandler.handle(failedNodeID, theHAInfo, false);
        }
      });
    }
  }

  private void runOnContextAndWait(Runnable runnable) {
    CountDownLatch latch = new CountDownLatch(1);
    // The testsuite requires that this is called on a Vert.x thread
    vertx.runOnContext(v -> {
      try {
        runnable.run();
      } finally {
        latch.countDown();
      }
    });
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException ignore) {
    }
  }

  // Process the failover of a deployment
  private void processFailover(JsonObject failedVerticle) {
    if (failDuringFailover) {
      throw new VertxException("Oops!");
    }
    // This method must block until the failover is complete - i.e. the verticle is successfully redeployed
    final String verticleName = failedVerticle.getString("verticle_name");
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Throwable> err = new AtomicReference<>();
    // Now deploy this verticle on this node
    ((VertxImpl)vertx).executeIsolated(v -> {
      JsonObject options = failedVerticle.getJsonObject("options");
      doDeployVerticle(verticleName, new DeploymentOptions(options)).onComplete(result -> {
        if (result.succeeded()) {
          log.info("Successfully redeployed verticle " + verticleName + " after failover");
        } else {
          log.error("Failed to redeploy verticle after failover", result.cause());
          err.set(result.cause());
        }
        latch.countDown();
        Throwable t = err.get();
        if (t != null) {
          throw new VertxException(t);
        }
      });
    });
    try {
      if (!latch.await(120, TimeUnit.SECONDS)) {
        throw new VertxException("Timed out waiting for redeploy on failover");
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  // Compute the failover node
  private String chooseHashedNode(String group, int hashCode) {
    List<String> nodes = clusterManager.getNodes();
    ArrayList<String> matchingMembers = new ArrayList<>();
    for (String node: nodes) {
      String sclusterInfo = clusterMap.get(node);
      if (sclusterInfo != null) {
        JsonObject clusterInfo = new JsonObject(sclusterInfo);
        String memberGroup = clusterInfo.getString("group");
        if (group == null || group.equals(memberGroup)) {
          matchingMembers.add(node);
        }
      }
    }
    if (!matchingMembers.isEmpty()) {
      // Hashcodes can be -ve so make it positive
      long absHash = (long)hashCode + Integer.MAX_VALUE;
      long lpos = absHash % matchingMembers.size();
      return matchingMembers.get((int)lpos);
    } else {
      return null;
    }
  }
}
