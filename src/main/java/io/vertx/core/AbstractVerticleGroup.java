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

package io.vertx.core;

import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An abstract base class that you can extend to write your own VerticleGroup classes.
 * <p>
 * Instead of implementing {@link io.vertx.core.VerticleGroup} directly, it is often simpler to just extend this class.
 *
 * @author <a href="https://wang007.github.io">wang007</a>
 * @see io.vertx.core.AbstractVerticle
 */
public abstract class AbstractVerticleGroup extends AbstractVerticle implements VerticleGroup {

  private static final Logger logger = LoggerFactory.getLogger(AbstractVerticleGroup.class);

  /**
   * Save the verticle successfully deployed and associate the context
   */
  private final List<VerticleHolder> verticleHolders = new ArrayList<>();

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    start();
    List<Verticle> vs = verticles();
    if (vs == null || vs.size() == 0) {
      logger.warn("verticles is empty");
      startPromise.complete();
      return;
    }
    ContextInternal contextInternal = (ContextInternal) context;
    Promise<Void> deployedComplete = Promise.promise();

    VerticleHolder[] successDeployed = new VerticleHolder[vs.size()];

    boolean asyncDeploy = asyncDeploy();
    if (asyncDeploy) {
      AtomicInteger deployCount = new AtomicInteger();
      AtomicBoolean failureReported = new AtomicBoolean();
      Throwable[] causes = new Throwable[vs.size()]; //for order
      int index = 0;
      for (Verticle v : vs) {
        int index0 = index++;
        ContextInternal duplicate = contextInternal.duplicate();
        duplicate.runOnContext(h -> {
          try {
            v.init(vertx, duplicate);
            PromiseInternal<Void> promise = duplicate.promise();
            v.start(promise);
            promise.onComplete(ar -> {
              if (ar.succeeded()) {
                successDeployed[index0] = new VerticleHolder(v, duplicate);
              } else {
                causes[index0] = ar.cause();
                failureReported.set(true);
              }
              if (deployCount.incrementAndGet() == vs.size()) {
                if (failureReported.get()) {
                  deployedComplete.fail(new CompositeException("deployed failed", causes));
                } else {
                  deployedComplete.complete();
                }
              }
            });
          } catch (Throwable e) {
            causes[index0] = e;
            failureReported.set(true);
            if (deployCount.incrementAndGet() == vs.size()) {
              deployedComplete.fail(new CompositeException("deployed failed", causes));
            }
          }
        });
      }
    } else {
      AtomicInteger index = new AtomicInteger();
      syncDeploy(vs, index, contextInternal, successDeployed, deployedComplete);
    }

    deployedComplete.future().onComplete(ar -> {
      if (ar.succeeded()) {
        this.verticleHolders.addAll(Arrays.asList(successDeployed));
        return;
      }
      //failed, rollback
      List<VerticleHolder> collect = Stream.of(successDeployed).filter(Objects::nonNull).collect(Collectors.toList());
      undeploy(collect, Promise.promise());
    });

    deployedComplete.future().onComplete(startPromise);
  }

  private void syncDeploy(List<Verticle> verticles,
                          AtomicInteger index,
                          ContextInternal contextInternal,
                          VerticleHolder[] successDeployed,
                          Promise<Void> deployComplete) {
    Verticle v = verticles.get(index.get());
    ContextInternal duplicate = contextInternal.duplicate();
    duplicate.runOnContext(h -> {
      try {
        v.init(vertx, duplicate);
        PromiseInternal<Void> promise = duplicate.promise();
        v.start(promise);
        promise.onComplete(ar -> {
          if (ar.succeeded()) {
            successDeployed[index.get()] = new VerticleHolder(v, duplicate);
            if (index.incrementAndGet() < verticles.size()) {
              syncDeploy(verticles, index, contextInternal, successDeployed, deployComplete);
            } else {
              deployComplete.complete();
            }
            return;
          }
          deployComplete.fail(ar.cause());
        });
      } catch (Throwable e) {
        deployComplete.fail(e);
      }
    });
  }

  private void undeploy(List<VerticleHolder> verticleHolders, Promise<Void> stopPromise) {
    if (asyncUndeploy()) {
      asyncUndeploy(verticleHolders, stopPromise);
    } else {
      syncUndeploy(verticleHolders, new AtomicInteger(), stopPromise, new Throwable[verticleHolders.size()], false);
    }
  }

  private void asyncUndeploy(List<VerticleHolder> verticleHolders, Promise<Void> stopPromise) {
    if (verticleHolders.isEmpty()) {
      stopPromise.complete();
      return;
    }
    AtomicInteger undeployCount = new AtomicInteger();
    AtomicBoolean failureReported = new AtomicBoolean();
    Throwable[] causes = new Throwable[verticleHolders.size()]; //for order
    int index = 0;
    for (VerticleHolder vh : verticleHolders) {
      int index0 = index++;
      vh.context.runOnContext(v -> {
        try {
          PromiseInternal<Void> promise = vh.context.promise();
          vh.verticle.stop(promise);
          promise.onComplete(ar -> {
            if (ar.failed()) {
              causes[index0] = ar.cause();
              failureReported.set(true);
            }
            if (undeployCount.incrementAndGet() == verticleHolders.size()) {
              if (failureReported.get()) {
                stopPromise.fail(new CompositeException("undeployed failed", causes));
              } else {
                stopPromise.complete();
              }
            }
          });
        } catch (Throwable e) {
          causes[index0] = e;
          failureReported.set(true);
          if (undeployCount.incrementAndGet() == verticleHolders.size()) {
            stopPromise.fail(new CompositeException("undeployed failed", causes));
          }
        }
      });
    }
  }

  private void syncUndeploy(List<VerticleHolder> verticleHolders,
                            AtomicInteger index,
                            Promise<Void> stopPromise,
                            Throwable[] caused,
                            boolean syncUndeployFailed) {
    if (verticleHolders.isEmpty()) {
      stopPromise.complete();
      return;
    }
    VerticleHolder vh = verticleHolders.get(index.get());
    vh.context.runOnContext(v -> {
      try {
        PromiseInternal<Void> promise = vh.context.promise();
        vh.verticle.stop(promise);
        promise.onComplete(ar -> {
          boolean syncUndeployFailed0 = syncUndeployFailed;
          //Make sure that the stop method of all successfully deployed verticle is called
          if (ar.failed()) {
            syncUndeployFailed0 = true;
            caused[index.get()] = ar.cause();
          }
          if (index.incrementAndGet() < verticleHolders.size()) {
            syncUndeploy(verticleHolders, index, stopPromise, caused, syncUndeployFailed0);
            return;
          }

          if (syncUndeployFailed0) {
            stopPromise.fail(new CompositeException("undeployed failed", caused));
          } else {
            stopPromise.complete();
          }
        });
      } catch (Throwable e) {
        caused[index.get()] = e;
        if (index.incrementAndGet() < verticleHolders.size()) {
          syncUndeploy(verticleHolders, index, stopPromise, caused, true);
          return;
        }
        stopPromise.fail(new CompositeException("undeployed failed", caused));
      }
    });
  }


  @Override
  public void stop(Promise<Void> stopPromise) throws Exception {
    stop();
    List<VerticleHolder> vhs = new ArrayList<>(this.verticleHolders);
    this.verticleHolders.clear();
    undeploy(vhs, stopPromise);
  }


  static class VerticleHolder {
    private final Verticle verticle;
    private final ContextInternal context;

    public VerticleHolder(Verticle verticle, ContextInternal context) {
      this.verticle = verticle;
      this.context = context;
    }
  }


  public final static class CompositeException extends NoStackTraceThrowable {

    private final List<Throwable> causes;

    private CompositeException(String message, Throwable[] causes) {
      super(message);
      List<Throwable> collect = Arrays.stream(causes).filter(Objects::nonNull).collect(Collectors.toList());
      this.causes = Collections.unmodifiableList(collect);
    }

    @Override
    public void printStackTrace(PrintStream s) {
      super.printStackTrace(s);
      boolean printIndex = causes.size() > 1;
      int index = 1;
      for (Throwable cause : causes) {
        if (printIndex) {
          s.println("cause " + index++);
        }
        cause.printStackTrace(s);
      }
    }

    public List<Throwable> getCauses() {
      return causes;
    }
  }

}
