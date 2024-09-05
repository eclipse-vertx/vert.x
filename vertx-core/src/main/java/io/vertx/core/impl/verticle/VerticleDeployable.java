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
package io.vertx.core.impl.verticle;

import io.netty.channel.EventLoop;
import io.vertx.core.*;
import io.vertx.core.impl.*;
import io.vertx.core.impl.deployment.Deployable;
import io.vertx.core.impl.deployment.Deployment;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.logging.Logger;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class VerticleDeployable implements Deployable {

  public static Deployable deployable(VertxImpl vertx,
                                      Logger log,
                                      DeploymentOptions options,
                                      Function<Verticle, String> identifierProvider,
                                      ClassLoader tccl,
                                      Callable<Verticle> verticleSupplier) throws Exception {
    int nbInstances = options.getInstances();
    Set<Verticle> verticles = Collections.newSetFromMap(new IdentityHashMap<>());
    for (int i = 0; i < nbInstances; i++) {
      Verticle verticle;
      try {
        verticle = verticleSupplier.call();
      } catch (Exception e) {
        throw e;
      }
      if (verticle == null) {
        throw new VertxException("Supplied verticle is null", true);
      }
      verticles.add(verticle);
    }
    if (verticles.size() != nbInstances) {
      throw new VertxException("Same verticle supplied more than once", true);
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
    ArrayList<Verticle> list = new ArrayList<>(verticles);
    return new VerticleDeployable(vertx, log, list, identifierProvider.apply(list.get(0)), mode, workerPool, tccl);
  }

  private final VertxImpl vertx;
  private final Logger log;
  private final List<Verticle> verticles;
  private final ThreadingModel threading;
  private final WorkerPool workerPool;
  private final String identifier;
  private final List<VerticleHolder> holders = new CopyOnWriteArrayList<>();
  private final ClassLoader tccl;

  public VerticleDeployable(VertxImpl vertx,
                            Logger log,
                            List<Verticle> verticles,
                            String identifier,
                            ThreadingModel threading,
                            WorkerPool workerPool,
                            ClassLoader tccl) {
    this.vertx = vertx;
    this.log = log;
    this.workerPool = workerPool;
    this.verticles = verticles;
    this.identifier = identifier;
    this.threading = threading;
    this.tccl = tccl;
  }

  public Set<Context> contexts() {
    Set<Context> contexts = new HashSet<>();
    for (VerticleHolder holder: holders) {
      contexts.add(holder.context);
    }
    return contexts;
  }

  public Set<Verticle> verticles() {
    Set<Verticle> verts = new HashSet<>();
    for (VerticleHolder holder: holders) {
      verts.add(holder.verticle);
    }
    return verts;
  }

  @Override
  public String identifier() {
    return identifier;
  }

  @Override
  public Future<?> deploy(Deployment deployment) {
    EventLoop workerLoop = null;
    List<Future<?>> futures = new ArrayList<>();
    for (Verticle verticle: verticles) {
      CloseFuture closeFuture = new CloseFuture(log);
      ContextImpl context;
      switch (threading) {
        case WORKER:
          if (workerLoop == null) {
            context = vertx.createWorkerContext(deployment, closeFuture, workerPool, tccl);
            workerLoop = context.nettyEventLoop();
          } else {
            context = vertx.createWorkerContext(deployment, closeFuture, workerLoop, workerPool, tccl);
          }
          break;
        case VIRTUAL_THREAD:
          if (workerLoop == null) {
            context = vertx.createVirtualThreadContext(deployment, closeFuture, tccl);
            workerLoop = context.nettyEventLoop();
          } else {
            context = vertx.createVirtualThreadContext(deployment, closeFuture, workerLoop, tccl);
          }
          break;
        default:
          context = vertx.createEventLoopContext(deployment, closeFuture, workerPool, tccl);
          break;
      }
      VerticleHolder holder = new VerticleHolder(verticle, context, closeFuture);
      Promise<Void> startPromise = context.promise();
      holder.startPromise = startPromise;
      holders.add(holder);
      futures.add(startPromise
              .future()
              .andThen(ar -> {
                if (ar.succeeded()) {
                  holder.startPromise = null;
                }
              }));
      context.runOnContext(v -> {
        try {
          verticle.init(vertx, context);
          verticle.start(startPromise);
        } catch (Throwable t) {
          startPromise.tryFail(t);
        }
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

  @Override
  public Future<?> undeploy() {
    List<Future<?>> undeployFutures = new ArrayList<>();
    for (VerticleHolder holder: holders) {
      Promise<Void> startPromise = holder.startPromise;
      if (startPromise != null) {
        startPromise.tryFail(new VertxException("Verticle un-deployed", true));
      } else {
        ContextImpl context = holder.context;
        Promise<Void> p = Promise.promise();
        undeployFutures.add(p.future());
        context.runOnContext(v -> {
          Promise<Void> stopPromise = Promise.promise();
          Future<Void> stopFuture = stopPromise.future();
          stopFuture
            .eventually(() -> holder
              .close()
              .onFailure(err -> log.error("Failed to run close hook", err))).onComplete(p);
          try {
            holder.verticle.stop(stopPromise);
          } catch (Throwable t) {
            if (!stopPromise.tryFail(t)) {
              context.reportException(t);
            }
          }
        });
      }
    }
    return Future.join(undeployFutures);
  }

  @Override
  public Future<?> cleanup() {
    List<Future<?>> futs = new ArrayList<>();
    for (VerticleHolder holder : holders) {
      futs.add(holder.closeFuture.close());
    }
    Future<?> fut = Future.join(futs);
    if (workerPool != null) {
      fut = fut.andThen(ar -> workerPool.close());
      workerPool.close();
    }
    return fut;
  }

  private static class VerticleHolder {

    final Verticle verticle;
    final ContextImpl context;
    final CloseFuture closeFuture;
    Promise<Void> startPromise;

    VerticleHolder(Verticle verticle, ContextImpl context, CloseFuture closeFuture) {
      this.verticle = verticle;
      this.context = context;
      this.closeFuture = closeFuture;
    }

    Future<Void> close() {
      return closeFuture.close();
    }
  }
}
