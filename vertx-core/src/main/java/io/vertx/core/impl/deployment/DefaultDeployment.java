/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl.deployment;

import io.netty.channel.EventLoop;
import io.vertx.core.*;
import io.vertx.core.impl.ContextBuilderImpl;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.WorkerPool;
import io.vertx.core.internal.deployment.Deployment;
import io.vertx.core.internal.deployment.DeploymentContext;
import io.vertx.core.internal.logging.Logger;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class DefaultDeployment implements Deployment {

  public static DefaultDeployment deployment(VertxImpl vertx,
                                             Logger log,
                                             DeploymentOptions options,
                                             Function<Deployable, String> identifierProvider,
                                             ClassLoader tccl,
                                             Callable<? extends Deployable> supplier) throws Exception {
    int numberOfInstances = options.getInstances();
    Set<Deployable> deployables = Collections.newSetFromMap(new IdentityHashMap<>());
    for (int i = 0; i < numberOfInstances;i++) {
      Deployable deployable;
      try {
        deployable = supplier.call();
      } catch (Exception e) {
        throw e;
      }
      if (deployable == null) {
        throw new VertxException("Supplied deployable is null", true);
      }
      deployables.add(deployable);
    }
    if (deployables.size() != numberOfInstances) {
      throw new VertxException("Same deployable supplied more than once", true);
    }
    WorkerPool workerPool = null;
    ThreadingModel mode = options.getThreadingModel();
    if (mode == null) {
      mode = ThreadingModel.EVENT_LOOP;
    }
    if (mode != ThreadingModel.VIRTUAL_THREAD) {
      if (options.getWorkerPoolName() != null) {
        workerPool = vertx.createSharedWorkerPool(options.getWorkerPoolName(), options.getWorkerPoolSize(), options.getMaxWorkerExecuteTime(), options.getMaxWorkerExecuteTimeUnit());
      }
    } else {
      if (!vertx.isVirtualThreadAvailable()) {
        throw new VertxException("This Java runtime does not support virtual threads", true);
      }
    }
    ArrayList<Deployable> list = new ArrayList<>(deployables);
    return new DefaultDeployment(vertx, options, log, list, identifierProvider.apply(list.get(0)), mode, workerPool, tccl);
  }

  private final VertxImpl vertx;
  private final DeploymentOptions options;
  private final Logger log;
  private final List<Deployable> deployables;
  private final ThreadingModel threading;
  private final WorkerPool workerPool;
  private final String identifier;
  private final List<Instance> instances = new CopyOnWriteArrayList<>();
  private final ClassLoader tccl;

  public DefaultDeployment(VertxImpl vertx,
                           DeploymentOptions options,
                           Logger log,
                           List<Deployable> deployables,
                           String identifier,
                           ThreadingModel threading,
                           WorkerPool workerPool,
                           ClassLoader tccl) {
    this.vertx = vertx;
    this.log = log;
    this.options = options;
    this.workerPool = workerPool;
    this.deployables = deployables;
    this.identifier = identifier;
    this.threading = threading;
    this.tccl = tccl;
  }

  public Set<Context> contexts() {
    Set<Context> contexts = new HashSet<>();
    for (Instance instance : instances) {
      contexts.add(instance.context);
    }
    return contexts;
  }

  public Set<Deployable> instances() {
    Set<Deployable> instances = new HashSet<>();
    for (Instance instance : this.instances) {
      instances.add(instance.deployable);
    }
    return instances;
  }

  public DeploymentOptions options() {
    return new DeploymentOptions(options);
  }

  public String identifier() {
    return identifier;
  }

  public Future<?> deploy(DeploymentContext deployment) {
    EventLoop workerLoop = null;
    List<Future<?>> futures = new ArrayList<>();
    for (Deployable verticle : deployables) {
      CloseFuture closeFuture = new CloseFuture(log);
      ContextBuilderImpl contextBuilder = ((ContextBuilderImpl) vertx.contextBuilder())
        .withDeploymentContext(deployment)
        .withCloseFuture(closeFuture)
        .withClassLoader(tccl);
      ContextInternal context;
      switch (threading) {
        case WORKER:
          if (workerLoop == null) {
            context = contextBuilder
              .withThreadingModel(ThreadingModel.WORKER)
              .withWorkerPool(workerPool)
              .build();
            workerLoop = context.nettyEventLoop();
          } else {
            context = contextBuilder
              .withThreadingModel(ThreadingModel.WORKER)
              .withEventLoop(workerLoop)
              .withWorkerPool(workerPool)
              .build();
          }
          break;
        case VIRTUAL_THREAD:
          if (workerLoop == null) {
            context = contextBuilder
              .withThreadingModel(ThreadingModel.VIRTUAL_THREAD)
              .build();
            workerLoop = context.nettyEventLoop();
          } else {
            context = contextBuilder
              .withThreadingModel(ThreadingModel.VIRTUAL_THREAD)
              .withEventLoop(workerLoop)
              .build();
          }
          break;
        default:
          context = contextBuilder
            .withWorkerPool(workerPool)
            .build();
          break;
      }
      Instance instance = new Instance(verticle, context);
      Promise<Object> startPromise = context.promise();
      instance.startPromise = startPromise;
      instances.add(instance);
      futures.add(startPromise
              .future()
              .andThen(ar -> {
                if (ar.succeeded()) {
                  instance.startPromise = null;
                }
              }));
      context.runOnContext(v -> {
        Future<?> fut;
        try {
          fut = verticle.deploy(context);
        } catch (Throwable t) {
          startPromise.tryFail(t);
          return;
        }
        fut.onComplete(startPromise);
      });
    }
    return Future
            .join(futures)
            .transform(ar -> {
      if (ar.failed()) {
        return undeploy().transform(ar2 -> (Future<?>) ar);
      } else {
        return Future.succeededFuture();
      }
    });
  }

  public Future<?> undeploy() {
    List<Future<?>> undeployFutures = new ArrayList<>();
    for (Instance instance : instances) {
      Promise<Object> startPromise = instance.startPromise;
      if (startPromise != null) {
        if (startPromise.tryFail(new VertxException("Verticle un-deployed", true))) {
          undeployFutures.add(instance.context.closeFuture().future());
        }
      } else {
        ContextInternal context = instance.context;
        Promise<Object> p = Promise.promise();
        undeployFutures.add(p.future());
        context.runOnContext(v -> {
          Promise<Object> stopPromise = Promise.promise();
          stopPromise
            .future()
            .eventually(() -> instance
              .close()
              .onFailure(err -> log.error("Failed to run close hook", err))).onComplete(p);
          Future<?> fut;
          try {
            fut = instance.deployable.undeploy(context);
          } catch (Throwable t) {
            // Not tested since shadowed by verticle
            if (!stopPromise.tryFail(t)) {
              context.reportException(t);
            }
            return;
          }
          fut.onComplete(stopPromise);
        });
      }
    }
    return Future.join(undeployFutures);
  }

  public Future<?> cleanup() {
    List<Future<?>> futs = new ArrayList<>();
    for (Instance instance : instances) {
      futs.add(instance.context.closeFuture().close());
    }
    Future<?> fut = Future.join(futs);
    if (workerPool != null) {
      fut = fut.andThen(ar -> workerPool.close());
      workerPool.close();
    }
    return fut;
  }

  private static class Instance {

    final Deployable deployable;
    final ContextInternal context;
    Promise<Object> startPromise;

    Instance(Deployable deployable, ContextInternal context) {
      this.deployable = deployable;
      this.context = context;
    }

    Future<Void> close() {
      return context.close();
    }
  }
}
