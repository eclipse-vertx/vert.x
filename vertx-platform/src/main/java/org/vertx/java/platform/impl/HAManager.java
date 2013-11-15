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

package org.vertx.java.platform.impl;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VertxException;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.spi.Action;
import org.vertx.java.core.spi.cluster.ClusterManager;
import org.vertx.java.core.spi.cluster.NodeListener;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
 * Periodically we check the value of attainedQuorum and if true we deploy any HA deployments waiting for a quorum.
 *
 * If false, we check if there are any HA deployments current deployed, and if so undeploy them, and add them to the list
 * of deployments waiting for a quorum.
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
  private static final String CLUSTER_MAP_NAME = "__vertx.haInfo";
  private static final long QUORUM_CHECK_PERIOD = 1000;

  private final VertxInternal vertx;
  private final PlatformManagerInternal platformManager;
  private final ClusterManager clusterManager;
  private final int quorumSize;
  private final String group;
  private final JsonObject haInfo;
  private final JsonArray haMods;
  private final Map<String, String> clusterMap;
  private final String nodeID;
  private final Queue<Runnable> toDeployOnQuorum = new ConcurrentLinkedQueue<>();
  private long quorumTimerID;
  private volatile boolean attainedQuorum;
  private volatile Handler<Boolean> failoverCompleteHandler;
  private volatile boolean failDuringFailover;
  private volatile boolean stopped;

  public HAManager(VertxInternal vertx, PlatformManagerInternal platformManager, ClusterManager clusterManager, int quorumSize, String group) {
    this.vertx = vertx;
    this.platformManager = platformManager;
    this.clusterManager = clusterManager;
    this.quorumSize = quorumSize;
    this.group = group == null ? "__DEFAULT__" : group;
    this.haInfo = new JsonObject();
    this.haMods = new JsonArray();
    haInfo.putArray("mods", haMods);
    haInfo.putString("group", this.group);
    this.clusterMap = clusterManager.getSyncMap(CLUSTER_MAP_NAME);
    this.nodeID = clusterManager.getNodeID();
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
    clusterMap.put(nodeID, haInfo.encode());
    quorumTimerID = vertx.setPeriodic(QUORUM_CHECK_PERIOD, new Handler<Long>() {
      @Override
      public void handle(Long timerID) {
        checkHADeployments();
      }
    });
    // Call check quorum to compute whether we have an initial quorum
    synchronized (this) {
      checkQuorum();
    }
  }

  // Remove the information on the deployment from the cluster - this is called when an HA module is undeployed
  public void removeFromHA(String depID) {
    synchronized (haMods) {
      Iterator<Object> iter = haMods.iterator();
      while (iter.hasNext()) {
        Object obj = iter.next();
        JsonObject mod = (JsonObject)obj;
        if (mod.getString("dep_id").equals(depID)) {
          iter.remove();
        }
      }
    }
    clusterMap.put(nodeID, haInfo.encode());
  }

  // Deploy an HA module
  public void deployModule(final String moduleName, final JsonObject config, final int instances,
                                        final Handler<AsyncResult<String>> doneHandler) {
    if (attainedQuorum) {
      final Handler<AsyncResult<String>> wrappedHandler = new Handler<AsyncResult<String>>() {
        @Override
        public void handle(AsyncResult<String> asyncResult) {
          if (asyncResult.succeeded()) {
            // Tell the other nodes of the cluster about the module for HA purposes
            addToHA(asyncResult.result(), moduleName, config, instances);
          }
          if (doneHandler != null) {
            doneHandler.handle(asyncResult);
          } else if (asyncResult.failed()) {
            log.error("Failed to deploy module", asyncResult.cause());
          }
        }
      };
      platformManager.deployModuleInternal(moduleName, config, instances, true, wrappedHandler);
    } else {
      log.info("Quorum not attained. Deployment of module will be delayed until there's a quorum.");
      addToHADeployList(moduleName, config, instances, doneHandler);
    }
  }

  public void stop() {
    if (!stopped) {
      clusterMap.remove(nodeID);
      vertx.cancelTimer(quorumTimerID);
      stopped = true;
    }
  }

  public void simulateKill() {
    if (!stopped) {
      clusterManager.leave();
      vertx.cancelTimer(quorumTimerID);
      stopped = true;
    }
  }

  // Set a handler that will be called when failover is complete - used in testing
  public void failoverCompleteHandler(Handler<Boolean> failoverCompleteHandler) {
    this.failoverCompleteHandler = failoverCompleteHandler;
  }

  // For testing:
  public void failDuringFailover(boolean fail) {
    failDuringFailover = fail;
  }

  // A node has joined the cluster
  // synchronize this in case the cluster manager is naughty and calls it concurrently
  private synchronized void nodeAdded(final String nodeID) {
     // This is not ideal but we need to wait for the group information to appear - and this will be shortly
    // after the node has been added
    checkQuorumWhenAdded(nodeID, System.currentTimeMillis());
  }

  // A node has left the cluster
  // synchronize this in case the cluster manager is naughty and calls it concurrently
  private synchronized void nodeLeft(String leftNodeID) {

    checkQuorum();
    if (attainedQuorum) {

      // Check for failover

      String sclusterInfo = clusterMap.get(leftNodeID);
      if (sclusterInfo == null) {
        // Clean close - do nothing
      } else {
        checkFailover(leftNodeID, new JsonObject(sclusterInfo));
      }

      // We also check for and potentially resume any previous failovers that might have failed
      // We can determine this if there any ids in the cluster map which aren't in the node list
      List<String> nodes = clusterManager.getNodes();

      for (Map.Entry<String, String> entry: clusterMap.entrySet()) {
        if (!nodes.contains(entry.getKey())) {
          checkFailover(entry.getKey(), new JsonObject(entry.getValue()));
        }
      }
    }
  }

  private synchronized void checkQuorumWhenAdded(final String nodeID, final long start) {
    if (clusterMap.containsKey(nodeID)) {
      checkQuorum();
    } else {
      vertx.setTimer(200, new Handler<Long>() {
        @Override
        public void handle(Long event) {

          // This can block on a monitor so it needs to run as a worker
          vertx.executeBlocking(new Action<Void>() {
            @Override
            public Void perform() {
              if (System.currentTimeMillis() - start > 10000) {
                log.warn("Timed out waiting for group information to appear");
              } else if (!stopped) {
                DefaultContext context = vertx.getContext();
                try {
                  // Remove any context we have here (from the timer) otherwise will screw things up when modules are deployed
                  vertx.setContext(null);
                  checkQuorumWhenAdded(nodeID, start);
                } finally {
                  vertx.setContext(context);
                }
              }
              return null;
            }
          }, null);
        }
      });
    }
  }

  // Check if there is a quorum for our group
  private void checkQuorum() {
    List<String> nodes = clusterManager.getNodes();
    int count = 0;
    for (String node: nodes) {
      String json = clusterMap.get(node);
      if (json != null) {
        JsonObject clusterInfo = new JsonObject(json);
        String group = clusterInfo.getString("group");
        if (group.equals(this.group)) {
          count++;
        }
      }
    }
    boolean attained = count >= quorumSize;
    if (!attainedQuorum && attained) {
      // A quorum has been attained so we can deploy any currently undeployed HA deployments
      log.info("A quorum has been obtained. Any deployments waiting on a quorum will now be deployed");
      this.attainedQuorum = true;
    } else if (attainedQuorum && !attained) {
      // We had a quorum but we lost it - we must undeploy any HA deployments
      log.info("There is no longer a quorum. Any HA deployments will be undeployed until a quorum is re-attained");
      this.attainedQuorum = false;
    }
  }

  // Add some information on a deployment in the cluster so other nodes know about it
  private void addToHA(String deploymentID, String moduleName, JsonObject conf, int instances) {
    JsonObject moduleConf = new JsonObject().putString("dep_id", deploymentID);
    moduleConf.putString("module_name", moduleName);
    if (conf != null) {
      moduleConf.putObject("conf", conf);
    }
    moduleConf.putNumber("instances", instances);
    synchronized (haMods) {
      haMods.addObject(moduleConf);
    }
    clusterMap.put(nodeID, haInfo.encode());
  }

  // Add the deployment to an internal list of deployments - these will be executed when a quorum is attained
  private void addToHADeployList(final String moduleName, final JsonObject config, final int instances,
                                 final Handler<AsyncResult<String>> doneHandler) {
    toDeployOnQuorum.add(new Runnable() {
      public void run() {
        DefaultContext ctx = vertx.getContext();
        try {
          vertx.setContext(null);
          deployModule(moduleName, config, instances, doneHandler);
        } finally {
          vertx.setContext(ctx);
        }
      }
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
      log.error("Failed when checking HA deployments", t);
    }
  }

  // Undeploy any HA deployments now there is no quorum
  private void undeployHADeployments() {
    for (final Map.Entry<String, Deployment> entry: platformManager.deployments().entrySet()) {
      if (entry.getValue().ha) {
        DefaultContext ctx = vertx.getContext();
        try {
          vertx.setContext(null);
          platformManager.undeploy(entry.getKey(), new AsyncResultHandler<Void>() {
            @Override
            public void handle(AsyncResult<Void> result) {
              if (result.succeeded()) {
                final Deployment dep = entry.getValue();
                log.info("Successfully undeployed HA deployment " + dep.modID + " as there is no quorum");
                addToHADeployList(dep.modID.toString(), dep.config, dep.instances, new AsyncResultHandler<String>() {
                  @Override
                  public void handle(AsyncResult<String> result) {
                    if (result.succeeded()) {
                      log.info("Successfully redeployed module " + entry.getValue().modID + " after quorum was re-attained");
                    } else {
                      log.error("Failed to redeploy module " + entry.getValue().modID + " after quorum was re-attained", result.cause());
                    }
                  }
                });
              } else {
                log.error("Failed to undeploy deployment on lost quorum", result.cause());
              }
            }
          });
        } finally {
          vertx.setContext(ctx);
        }
      }
    }
  }

  // Deploy any deployments that are waiting for a quorum
  private void deployHADeployments() {
    int size = toDeployOnQuorum.size();
    if (size != 0) {
      log.info("There are " + size + " HA deployments waiting on a quorum. These will now be deployed");
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
      JsonArray deployments = theHAInfo.getArray("mods");
      String group = theHAInfo.getString("group");
      String chosen = chooseHashedNode(group, failedNodeID.hashCode());
      if (chosen != null && chosen.equals(this.nodeID)) {
        log.info("Node " + failedNodeID + " has failed. This node will deploy " + deployments.size() + " deployments from that node.");
        if (deployments != null) {
          for (Object obj: deployments) {
            JsonObject app = (JsonObject)obj;
            processFailover(app);
          }
        }
        // Failover is complete! We can now remove the failed node from the cluster map
        clusterMap.remove(failedNodeID);
        if (failoverCompleteHandler != null) {
          failoverCompleteHandler.handle(true);
        }
      }
    } catch (Throwable t) {
      log.error("Failed to handle failover", t);
      // This is used for testing:
      if (failoverCompleteHandler != null) {
        failoverCompleteHandler.handle(false);
      }
    }
  }

  // Process the failover of a deployment
  private void processFailover(JsonObject failedModule) {
    if (failDuringFailover) {
      throw new VertxException("Oops!");
    }
    // This method must block until the failover is complete - i.e. the module is successfully redeployed
    final String moduleName = failedModule.getString("module_name");
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Throwable> err = new AtomicReference<>();
    // Now deploy this module on this node
    DefaultContext ctx = vertx.getContext();
    vertx.setContext(null);
    platformManager.deployModule(moduleName, failedModule.getObject("conf"), failedModule.getInteger("instances"), true, new Handler<AsyncResult<String>>() {
      @Override
      public void handle(AsyncResult<String> result) {
        if (result.succeeded()) {
          log.info("Successfully redeployed module " + moduleName + " after failover");
        } else {
          log.error("Failed to redeploy module after failover", result.cause());
          err.set(result.cause());
        }
        latch.countDown();
        Throwable t = err.get();
        if (t != null) {
          throw new VertxException(t);
        }
      }
    });
    vertx.setContext(ctx);
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
        if (group.equals(memberGroup)) {
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
