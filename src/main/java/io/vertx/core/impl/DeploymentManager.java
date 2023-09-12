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

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeploymentManager {

  private static final Logger log = LoggerFactory.getLogger(DeploymentManager.class);

  private final VertxInternal vertx;
  private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();

  public DeploymentManager(VertxInternal vertx) {
    this.vertx = vertx;
  }

  private String generateDeploymentID() {
    return UUID.randomUUID().toString();
  }

  public Future<String> deployVerticle(Callable<Verticle> verticleSupplier, DeploymentOptions options) {
    if (options.getInstances() < 1) {
      throw new IllegalArgumentException("Can't specify < 1 instances to deploy");
    }
    options.checkIsolationNotDefined();
    ContextInternal currentContext = vertx.getOrCreateContext();
    ClassLoader cl = options.getClassLoader();
    if (cl == null) {
      cl = Thread.currentThread().getContextClassLoader();
      if (cl == null) {
        cl = getClass().getClassLoader();
      }
    }
    return doDeploy(options, v -> "java:" + v.getClass().getName(), currentContext, currentContext, cl, verticleSupplier)
      .map(Deployment::deploymentID);
  }

  public Future<Void> undeployVerticle(String deploymentID) {
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
        undeployVerticle(deploymentID).onComplete(ar -> {
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

//  private <T> void reportFailure(Throwable t, ContextInternal context, Promise<T> completionHandler) {
//    if (completionHandler != null) {
//      reportResult(context, completionHandler, Future.failedFuture(t));
//    } else {
//      log.error(t.getMessage(), t);
//    }
//  }
//
//  private <T> void reportResult(Context context, Promise<T> completionHandler, AsyncResult<T> result) {
//    context.runOnContext(v -> {
//      try {
//        completionHandler.handle(result);
//      } catch (Throwable t) {
//        log.error("Failure in calling handler", t);
//        throw t;
//      }
//    });
//  }

  Future<Deployment> doDeploy(DeploymentOptions options,
                                  Function<Verticle, String> identifierProvider,
                                  ContextInternal parentContext,
                                  ContextInternal callingContext,
                                  ClassLoader tccl, Callable<Verticle> verticleSupplier) {
    int nbInstances = options.getInstances();
    Set<Verticle> verticles = Collections.newSetFromMap(new IdentityHashMap<>());
    for (int i = 0; i < nbInstances; i++) {
      Verticle verticle;
      try {
        verticle = verticleSupplier.call();
      } catch (Exception e) {
        return Future.failedFuture(e);
      }
      if (verticle == null) {
        return Future.failedFuture("Supplied verticle is null");
      }
      verticles.add(verticle);
    }
    if (verticles.size() != nbInstances) {
      return Future.failedFuture("Same verticle supplied more than once");
    }
    Verticle[] verticlesArray = verticles.toArray(new Verticle[0]);
    return doDeploy(identifierProvider.apply(verticlesArray[0]), options, parentContext, callingContext, tccl, verticlesArray);
  }

  private Future<Deployment> doDeploy(String identifier,
                        DeploymentOptions options,
                        ContextInternal parentContext,
                        ContextInternal callingContext,
                        ClassLoader tccl, Verticle... verticles) {
    Promise<Deployment> promise = callingContext.promise();
    String poolName = options.getWorkerPoolName();

    Deployment parent = parentContext.getDeployment();
    String deploymentID = generateDeploymentID();
    DeploymentImpl deployment = new DeploymentImpl(parent, deploymentID, identifier, options);

    AtomicInteger deployCount = new AtomicInteger();
    AtomicBoolean failureReported = new AtomicBoolean();
    for (Verticle verticle: verticles) {
      CloseFuture closeFuture = new CloseFuture(log);
      WorkerPool workerPool = poolName != null ? vertx.createSharedWorkerPool(poolName, options.getWorkerPoolSize(), options.getMaxWorkerExecuteTime(), options.getMaxWorkerExecuteTimeUnit()) : null;
      ContextBase context = (options.isWorker() ? vertx.createWorkerContext(deployment, closeFuture, workerPool, tccl) :
        vertx.createEventLoopContext(deployment, closeFuture, workerPool, tccl));
      VerticleHolder holder = new VerticleHolder(verticle, context, workerPool, closeFuture);
      deployment.addVerticle(holder);
      context.runOnContext(v -> {
        try {
          verticle.init(vertx, context);
          Promise<Void> startPromise = context.promise();
          Future<Void> startFuture = startPromise.future();
          verticle.start(startPromise);
          startFuture.onComplete(ar -> {
            if (ar.succeeded()) {
              if (parent != null) {
                if (parent.addChild(deployment)) {
                  deployment.child = true;
                } else {
                  // Orphan
                  deployment.doUndeploy(vertx.getOrCreateContext()).onComplete(ar2 -> promise.fail("Verticle deployment failed.Could not be added as child of parent verticle"));
                  return;
                }
              }
              deployments.put(deploymentID, deployment);
              if (deployCount.incrementAndGet() == verticles.length) {
                promise.complete(deployment);
              }
            } else if (failureReported.compareAndSet(false, true)) {
              deployment.rollback(callingContext, promise, context, holder, ar.cause());
            }
          });
        } catch (Throwable t) {
          if (failureReported.compareAndSet(false, true))
            deployment.rollback(callingContext, promise, context, holder, t);
        }
      });
    }

    return promise.future();
  }

  static class VerticleHolder {

    final Verticle verticle;
    final ContextBase context;
    final WorkerPool workerPool;
    final CloseFuture closeFuture;

    VerticleHolder(Verticle verticle, ContextBase context, WorkerPool workerPool, CloseFuture closeFuture) {
      this.verticle = verticle;
      this.context = context;
      this.workerPool = workerPool;
      this.closeFuture = closeFuture;
    }

    Future<Void> close() {
      return closeFuture.close().andThen(ar -> {
        if (workerPool != null) {
          workerPool.close();
        }
      });
    }
  }

  private class DeploymentImpl implements Deployment {

    private static final int ST_DEPLOYED = 0, ST_UNDEPLOYING = 1, ST_UNDEPLOYED = 2;

    private final Deployment parent;
    private final String deploymentID;
    private final JsonObject conf;
    private final String verticleIdentifier;
    private final List<VerticleHolder> verticles = new CopyOnWriteArrayList<>();
    private final Set<Deployment> children = ConcurrentHashMap.newKeySet();
    private final DeploymentOptions options;
    private Handler<Void> undeployHandler;
    private int status = ST_DEPLOYED;
    private volatile boolean child;

    private DeploymentImpl(Deployment parent, String deploymentID, String verticleIdentifier, DeploymentOptions options) {
      this.parent = parent;
      this.deploymentID = deploymentID;
      this.conf = options.getConfig() != null ? options.getConfig().copy() : new JsonObject();
      this.verticleIdentifier = verticleIdentifier;
      this.options = options;
    }

    public void addVerticle(VerticleHolder holder) {
      verticles.add(holder);
    }

    private synchronized void rollback(ContextInternal callingContext, Promise<Deployment> completionPromise, ContextBase context, VerticleHolder closeFuture, Throwable cause) {
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
              context.reportException(e);
            }
          }
          if (childrenResult.failed()) {
            completionPromise.fail(cause);
          } else {
            closeFuture.close().transform(ar -> Future.<Deployment>failedFuture(cause)).onComplete(completionPromise);
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
        List<Future<?>> undeployFutures = new ArrayList<>();
        if (parent != null) {
          parent.removeChild(this);
        }
        for (VerticleHolder verticleHolder: verticles) {
          ContextBase context = verticleHolder.context;
          Promise<Void> p = Promise.promise();
          undeployFutures.add(p.future());
          context.runOnContext(v -> {
            Promise<Void> stopPromise = undeployingContext.promise();
            Future<Void> stopFuture = stopPromise.future();
            stopFuture
              .eventually(() -> {
                deployments.remove(deploymentID);
                return verticleHolder
                  .close()
                  .onFailure(err -> log.error("Failed to run close hook", err));
              }).onComplete(p);
            try {
              verticleHolder.verticle.stop(stopPromise);
            } catch (Throwable t) {
              if (!stopPromise.tryFail(t)) {
                undeployingContext.reportException(t);
              }
            }
          });
        }
        Promise<Void> resolvingPromise = undeployingContext.promise();
        Future.all(undeployFutures).<Void>mapEmpty().onComplete(resolvingPromise);
        Future<Void> fut = resolvingPromise.future();
        Handler<Void> handler = undeployHandler;
        if (handler != null) {
          undeployHandler = null;
          return fut.andThen(ar -> handler.handle(null));
        }
        return fut;
      }
    }

    @Override
    public String verticleIdentifier() {
      return verticleIdentifier;
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
    public Set<Context> getContexts() {
      Set<Context> contexts = new HashSet<>();
      for (VerticleHolder holder: verticles) {
        contexts.add(holder.context);
      }
      return contexts;
    }

    @Override
    public Set<Verticle> getVerticles() {
      Set<Verticle> verts = new HashSet<>();
      for (VerticleHolder holder: verticles) {
        verts.add(holder.verticle);
      }
      return verts;
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
