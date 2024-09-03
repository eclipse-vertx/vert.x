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
    Promise<Deployment> promise = callingContext.promise();

    String deploymentID = generateDeploymentID();

    Deployment parent = parentContext.getDeployment();
    DeploymentImpl deployment = new DeploymentImpl(deployable, parent, deploymentID, options);
    synchronized (deploying) {
      deploying.put(deploymentID, deployment);
    }
    deployable.deploy(deployment, new Promise<>() {
      @Override
      public boolean tryComplete(Void result) {
        deployments.put(deploymentID, deployment);
        if (parent != null) {
          if (parent.addChild(deployment)) {
            deployment.child = true;
          } else {
            // Orphan
            deployment.doUndeploy(vertx.getOrCreateContext()).onComplete(ar2 -> promise.fail("Deployment failed.Could not be added as child of parent deployment"));
            return false;
          }
        }
        synchronized (deploying) {
          deploying.remove(deploymentID);
        }
        promise.complete(deployment);
        return false;
      }
      @Override
      public boolean tryFail(Throwable cause) {
        deployment.rollback(callingContext, promise, cause);
        return false;
      }
      @Override
      public Future<Void> future() {
        throw new UnsupportedOperationException();
      }
    });

    return promise.future();
  }

  private class DeploymentImpl implements Deployment {

    private static final int ST_DEPLOYED = 0, ST_UNDEPLOYING = 1, ST_UNDEPLOYED = 2;

    private final Deployable deployable;
    private final Deployment parent;
    private final String deploymentID;
    private final JsonObject conf;
    private final Set<Deployment> children = ConcurrentHashMap.newKeySet();
    private final DeploymentOptions options;
    private Handler<Void> undeployHandler;
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

    private synchronized void rollback(ContextInternal callingContext, Promise<Deployment> completionPromise, Throwable cause) {
      if (status == ST_DEPLOYED) {
        status = ST_UNDEPLOYING;
        doUndeployChildren(callingContext).onComplete(childrenResult -> {
          Handler<Void> handler;
          synchronized (DeploymentImpl.this) {
            status = ST_UNDEPLOYED;
            handler = undeployHandler;
            undeployHandler = null;
          }
          if (handler != null) {
            try {
              handler.handle(null);
            } catch (Exception e) {
              callingContext.reportException(e);
            }
          }
          if (childrenResult.failed()) {
            completionPromise.fail(cause);
          } else {
            deployable
              .cleanup()
              .transform(ar -> Future.<Deployment>failedFuture(cause)).onComplete(completionPromise);
          }
        });
      }
    }

    private synchronized Future<Void> doUndeployChildren(ContextInternal undeployingContext) {
      if (!children.isEmpty()) {
        List<Future<?>> childFuts = new ArrayList<>();
        for (Deployment childDeployment: new HashSet<>(children)) {
          Promise<Void> p = Promise.promise();
          childFuts.add(p.future());
          childDeployment.doUndeploy(undeployingContext).onComplete(ar -> {
            children.remove(childDeployment);
            p.handle(ar);
          });
        }
        return Future.all(childFuts).mapEmpty();
      } else {
        return Future.succeededFuture();
      }
    }

    public synchronized Future<Void> doUndeploy(ContextInternal undeployingContext) {
      if (status == ST_UNDEPLOYED) {
        return Future.failedFuture(new IllegalStateException("Already undeployed"));
      }
      if (!children.isEmpty()) {
        status = ST_UNDEPLOYING;
        return doUndeployChildren(undeployingContext).compose(v -> doUndeploy(undeployingContext));
      } else {
        status = ST_UNDEPLOYED;
        if (parent != null) {
          parent.removeChild(this);
        }
        Future<?> undeployFutures = deployable.undeploy().andThen(ar -> deployments.remove(deploymentID));
        Promise<Void> resolvingPromise = undeployingContext.promise();
        undeployFutures.<Void>mapEmpty().onComplete(resolvingPromise);
        Future<Void> fut = resolvingPromise.future();
        fut = fut.eventually(deployable::cleanup);
        Handler<Void> handler = undeployHandler;
        if (handler != null) {
          undeployHandler = null;
          return fut.andThen(ar -> handler.handle(null));
        }
        return fut;
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
    public void undeployHandler(Handler<Void> handler) {
      synchronized (this) {
        if (status != ST_UNDEPLOYED) {
          undeployHandler = handler;
          return;
        }
      }
      handler.handle(null);
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
