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

package io.vertx.core.impl.deployment;

import io.vertx.core.*;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultDeploymentManager implements DeploymentManager {

  public static final Logger log = LoggerFactory.getLogger(DefaultDeploymentManager.class);

  private final VertxImpl vertx;
  private final Map<String, Deployment> deploying = new HashMap<>();
  private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();

  public DefaultDeploymentManager(VertxImpl vertx) {
    this.vertx = vertx;
  }

  private String generateDeploymentID() {
    return UUID.randomUUID().toString();
  }

  public Future<Void> undeploy(String deploymentID) {
    Deployment deployment = deployments.get(deploymentID);
    ContextInternal currentContext = vertx.getOrCreateContext();
    if (deployment == null) {
      return currentContext.failedFuture(new IllegalStateException("Unknown deployment"));
    } else {
      return deployment.doUndeploy(vertx.getOrCreateContext());
    }
  }

  public Set<String> deployments() {
    return Collections.unmodifiableSet(deployments.keySet());
  }

  public Deployment getDeployment(String deploymentID) {
    return deployments.get(deploymentID);
  }

  public Future<Void> undeployAll() {
    // TODO timeout if it takes too long - e.g. async stop verticle fails to call future

    // We only deploy the top level verticles as the children will be undeployed when the parent is
    while (true) {
      DeploymentImpl deployment;
      synchronized (deploying) {
        if (deploying.isEmpty()) {
          break;
        }
        Iterator<Map.Entry<String, Deployment>> it = deploying.entrySet().iterator();
        Map.Entry<String, Deployment> entry = it.next();
        it.remove();
        deployment = (DeploymentImpl) entry.getValue();
      }
      deployment.deployable.undeploy().andThen(ar -> deployments.remove(deployment.deploymentID));
    }
    Set<String> deploymentIDs = new HashSet<>();
    for (Map.Entry<String, Deployment> entry: deployments.entrySet()) {
      if (!entry.getValue().isChild()) {
        deploymentIDs.add(entry.getKey());
      }
    }
    List<Future<?>> completionList = new ArrayList<>();
    if (!deploymentIDs.isEmpty()) {
      for (String deploymentID : deploymentIDs) {
        Promise<Void> promise = Promise.promise();
        completionList.add(promise.future());
        undeploy(deploymentID).onComplete(ar -> {
          if (ar.failed()) {
            // Log but carry on regardless
            log.error("Undeploy failed", ar.cause());
          }
          promise.handle(ar);
        });
      }
      Promise<Void> promise = vertx.getOrCreateContext().promise();
      Future.join(completionList).<Void>mapEmpty().onComplete(promise);
      return promise.future();
    } else {
      return vertx.getOrCreateContext().succeededFuture();
    }
  }

  public Future<Deployment> deploy(DeploymentOptions options,
                                      ContextInternal parentContext,
                                      ContextInternal callingContext,
                                      Deployable deployable) {
    String deploymentID = generateDeploymentID();
    Deployment parent = parentContext.getDeployment();
    DeploymentImpl deployment = new DeploymentImpl(deployable, parent, deploymentID, options);
    synchronized (deploying) {
      deploying.put(deploymentID, deployment);
    }
    Promise<Deployment> result = callingContext.promise();
    Future<?> f = deployable.deploy(deployment);
    f.onComplete(ar -> {
      if (ar.succeeded()) {
        deployments.put(deploymentID, deployment);
        if (parent != null) {
          if (parent.addChild(deployment)) {
            deployment.child = true;
          } else {
            // Orphan
            deployment
              .doUndeploy(vertx.getOrCreateContext())
              .onComplete(ar2 -> {
                result.fail("Deployment failed.Could not be added as child of parent deployment");
            });
            return;
          }
        }
        synchronized (deploying) {
          deploying.remove(deploymentID);
        }
        result.complete(deployment);
      } else {
        deployment
          .rollback(callingContext)
          .onComplete(ar2 -> result.fail(ar.cause()));
      }
    });
    return result.future();
  }

  private class DeploymentImpl implements Deployment {

    private static final int ST_DEPLOYED = 0, ST_UNDEPLOYING = 1, ST_UNDEPLOYED = 2;

    private final Deployable deployable;
    private final Deployment parent;
    private final String deploymentID;
    private final JsonObject conf;
    private final Set<Deployment> children = ConcurrentHashMap.newKeySet();
    private final DeploymentOptions options;
    private int status = ST_DEPLOYED;
    private volatile boolean child;

    private DeploymentImpl(Deployable deployable,
                           Deployment parent,
                           String deploymentID,
                           DeploymentOptions options) {
      this.deployable = deployable;
      this.parent = parent;
      this.deploymentID = deploymentID;
      this.conf = options.getConfig() != null ? options.getConfig().copy() : new JsonObject();
      this.options = options;
    }

    private synchronized Future<?> rollback(ContextInternal callingContext) {
      if (status == ST_DEPLOYED) {
        status = ST_UNDEPLOYING;
        return doUndeployChildren(callingContext)
          .transform(childrenResult -> {
            synchronized (DeploymentImpl.this) {
              status = ST_UNDEPLOYED;
            }
            if (childrenResult.failed()) {
              return (Future)childrenResult;
            } else {
              return deployable.cleanup();
            }
          });
      } else {
        return callingContext.succeededFuture();
      }
    }

    private synchronized Future<?> doUndeployChildren(ContextInternal undeployingContext) {
      if (!children.isEmpty()) {
        List<Future<?>> childFuts = new ArrayList<>();
        for (Deployment childDeployment: new HashSet<>(children)) {
          childFuts.add(childDeployment
            .doUndeploy(undeployingContext)
            .andThen(ar -> children.remove(childDeployment)));
        }
        return Future.all(childFuts);
      } else {
        return Future.succeededFuture();
      }
    }

    public synchronized Future<Void> doUndeploy(ContextInternal undeployingContext) {
      if (status == ST_UNDEPLOYED) {
        return undeployingContext.failedFuture(new IllegalStateException("Already undeployed"));
      }
      if (!children.isEmpty()) {
        status = ST_UNDEPLOYING;
        return doUndeployChildren(undeployingContext)
          .compose(v -> doUndeploy(undeployingContext));
      } else {
        status = ST_UNDEPLOYED;
        if (parent != null) {
          parent.removeChild(this);
        }
        Future<?> undeployFutures = deployable
          .undeploy()
          .andThen(ar -> deployments.remove(deploymentID))
          .eventually(deployable::cleanup);
        return undeployingContext.future(p -> {
          undeployFutures.onComplete(ar -> {
            if (ar.succeeded()) {
              p.complete();
            } else {
              p.fail(ar.cause());
            }
          });
        });
      }
    }

    @Override
    public String identifier() {
      return deployable.identifier();
    }

    @Override
    public DeploymentOptions deploymentOptions() {
      return options;
    }

    @Override
    public JsonObject config() {
      return conf;
    }

    @Override
    public synchronized boolean addChild(Deployment deployment) {
      if (status == ST_DEPLOYED) {
        children.add(deployment);
        return true;
      } else {
        return false;
      }
    }

    @Override
    public void removeChild(Deployment deployment) {
      children.remove(deployment);
    }

    @Override
    public Deployable deployable() {
      return deployable;
    }

    @Override
    public boolean isChild() {
      return child;
    }

    @Override
    public String deploymentID() {
      return deploymentID;
    }
  }
}
