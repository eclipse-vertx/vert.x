/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.core.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.VerticleFactory;
import io.vertx.core.VertxException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeploymentManager {

  private static final Logger log = LoggerFactory.getLogger(DeploymentManager.class);

  private final VertxInternal vertx;
  private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();
  private final Map<String, ClassLoader> classloaders = new WeakHashMap<>();
  private List<VerticleFactory> verticleFactories = new CopyOnWriteArrayList<>();
  private static final VerticleFactory DEFAULT_VERTICLE_FACTORY = new SimpleJavaVerticleFactory();

  public DeploymentManager(VertxInternal vertx) {
    this.vertx = vertx;
    loadVerticleFactories();
  }

  private void loadVerticleFactories() {
    ServiceLoader<VerticleFactory> factories = ServiceLoader.load(VerticleFactory.class);
    Iterator<VerticleFactory> iter = factories.iterator();
    while (iter.hasNext()) {
      VerticleFactory factory = iter.next();
      factory.init(vertx);
      verticleFactories.add(factory);
    }
  }

  public void deployVerticle(Verticle verticle, DeploymentOptions options,
                             Handler<AsyncResult<String>> doneHandler) {
    ContextImpl currentContext = vertx.getOrCreateContext();
    doDeploy(verticle, options, currentContext, doneHandler);
  }

  public void deployVerticle(String verticleName,
                             DeploymentOptions options,
                             Handler<AsyncResult<String>> doneHandler) {
    ContextImpl currentContext = vertx.getOrCreateContext();
    ClassLoader cl = getClassLoader(options.getIsolationGroup());
    try {
      VerticleFactory verticleFactory = null;
      for (VerticleFactory factory: verticleFactories) {
        if (factory.matches(verticleName)) {
          if (verticleFactory != null) {
            reportFailure(new VertxException("Multiple VerticleFactory matches for verticleName: " + verticleName),
                          currentContext, doneHandler);
            return;
          }
          verticleFactory = factory;
        }
      }
      if (verticleFactory == null) {
        // Use default Java verticle factory
        verticleFactory = DEFAULT_VERTICLE_FACTORY;
      }
      Verticle verticle = verticleFactory.createVerticle(verticleName, cl);
      if (verticle == null) {
        reportFailure(new NullPointerException("VerticleFactory::createVerticle returned null"), currentContext, doneHandler);
      } else {
        doDeploy(verticle, options, currentContext, doneHandler);
      }
    } catch (Exception e) {
      reportFailure(e, currentContext, doneHandler);
    }
  }

  public void undeployVerticle(String deploymentID, Handler<AsyncResult<Void>> doneHandler) {
    Deployment deployment = deployments.get(deploymentID);
    Context currentContext = vertx.getOrCreateContext();
    if (deployment == null) {
      reportFailure(new IllegalStateException("Unknown deployment"), currentContext, doneHandler);
    } else {
      deployment.undeploy(doneHandler);
    }
  }

  public Set<String> deployments() {
    return Collections.unmodifiableSet(deployments.keySet());
  }

  public void undeployAll(Handler<AsyncResult<Void>> doneHandler) {
    Set<String> deploymentIDs = new HashSet<>(deployments.keySet());
    AtomicInteger count = new AtomicInteger(deploymentIDs.size());
    for (String deploymentID: deploymentIDs) {
      undeployVerticle(deploymentID, ar -> {
        if (ar.failed()) {
          log.error("Undeploy failed", ar.cause());
        }
        if (count.incrementAndGet() == deploymentIDs.size()) {
          doneHandler.handle(new FutureResultImpl<>((Void)null));
        }
      });
    }
  }

  public void registerVerticleFactory(VerticleFactory factory) {
    if (verticleFactories.contains(factory)) {
      throw new IllegalArgumentException("Factory " + factory + " is already registered");
    }
    verticleFactories.add(factory);
  }

  public void unregisterVerticleFactory(VerticleFactory factory) {
    if (!verticleFactories.remove(factory)) {
      throw new IllegalArgumentException("Factory " + factory + " is not registered");
    }
  }

  public List<VerticleFactory> verticleFactories() {
    return Collections.unmodifiableList(verticleFactories);
  }

  private ClassLoader getClassLoader(String isolationGroup) {
    ClassLoader cl;
    if (isolationGroup == null) {
      cl = getCurrentClassLoader();
    } else {
      synchronized (this) {
        cl = classloaders.get(isolationGroup);
        if (cl == null) {
          ClassLoader current = getCurrentClassLoader();
          if (!(current instanceof URLClassLoader)) {
            throw new IllegalStateException("Current classloader must be URLClassLoader");
          }
          URLClassLoader urlc = (URLClassLoader)current;
          URL[] urls = urlc.getURLs();
          // Copy the URLS into the isolating classloader
          cl = new IsolatingClassLoader(urls, getCurrentClassLoader());
          classloaders.put(isolationGroup, cl);
        }
      }
    }
    return cl;
  }

  private ClassLoader getCurrentClassLoader() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = getClass().getClassLoader();
    }
    return cl;
  }


  private <T> void reportFailure(Throwable t, Context context, Handler<AsyncResult<T>> doneHandler) {
    if (doneHandler != null) {
      reportResult(context, doneHandler, new FutureResultImpl<>(t));
    } else {
      log.error(t.getMessage(), t);
    }
  }

  private <T> void reportSuccess(T result, Context context, Handler<AsyncResult<T>> doneHandler) {
    if (doneHandler != null) {
      reportResult(context, doneHandler, new FutureResultImpl<>(result));
    }
  }

  private <T> void reportResult(Context context, Handler<AsyncResult<T>> doneHandler, AsyncResult<T> result) {
    context.runOnContext(v -> {
      try {
        doneHandler.handle(result);
      } catch (Throwable t) {
        log.error("Failure in calling handler", t);
      }
    });
  }

  private void doDeploy(Verticle verticle, DeploymentOptions options,
                        ContextImpl currentContext,
                        Handler<AsyncResult<String>> doneHandler) {
    if (options.isMultiThreaded() && !options.isWorker()) {
      throw new IllegalArgumentException("If multi-threaded then must be worker too");
    }
    ContextImpl context = options.isWorker() ? vertx.createWorkerContext(options.isMultiThreaded()) : vertx.createEventLoopContext();
    String deploymentID = UUID.randomUUID().toString();
    DeploymentImpl deployment = new DeploymentImpl(deploymentID, context, verticle);
    context.setDeployment(deployment);
    Deployment parent = currentContext.getDeployment();
    if (parent != null) {
      parent.addChild(deployment);
    }
    JsonObject conf = options.getConfig() == null ? null : options.getConfig().copy(); // Copy it
    context.runOnContext(v -> {
      try {
        verticle.setVertx(vertx);
        verticle.setConfig(conf);
        verticle.setDeploymentID(deploymentID);
        Future<Void> startFuture = new FutureResultImpl<>();
        verticle.start(startFuture);
        startFuture.setHandler(ar -> {
          if (ar.succeeded()) {
            deployments.put(deploymentID, deployment);
            reportSuccess(deploymentID, currentContext, doneHandler);
          } else {
            reportFailure(ar.cause(), currentContext, doneHandler);
          }
        });
      } catch (Throwable t) {
        reportFailure(t, currentContext, doneHandler);
      }
    });
  }

  private class DeploymentImpl implements Deployment {

    private final String id;
    private final ContextImpl context;
    private final Verticle verticle;
    private final Set<Deployment> children = new ConcurrentHashSet<>();
    private boolean undeployed;

    private DeploymentImpl(String id, ContextImpl context, Verticle verticle) {
      this.id = id;
      this.context = context;
      this.verticle = verticle;
    }

    @Override
    public void undeploy(Handler<AsyncResult<Void>> doneHandler) {
      ContextImpl currentContext = vertx.getOrCreateContext();
      if (!undeployed) {
        doUndeploy(currentContext, doneHandler);
      } else {
        reportFailure(new IllegalStateException("Already undeployed"), currentContext, doneHandler);
      }
    }

    public void doUndeploy(ContextImpl undeployingContext, Handler<AsyncResult<Void>> doneHandler) {

      if (!children.isEmpty()) {
        final int size = children.size();
        AtomicInteger childCount = new AtomicInteger();
        for (Deployment childDeployment: new HashSet<>(children)) {
          childDeployment.doUndeploy(undeployingContext, ar -> {
            children.remove(childDeployment);
            if (ar.failed()) {
              reportFailure(ar.cause(), undeployingContext, doneHandler);
            } else if (childCount.incrementAndGet() == size) {
              // All children undeployed
              doUndeploy(undeployingContext, doneHandler);
            }
          });
        }
      } else {
        undeployed = true;
        context.runOnContext(v -> {
          Future<Void> stopFuture = new FutureResultImpl<>();
          stopFuture.setHandler(ar -> {
            deployments.remove(id);
            context.runCloseHooks(ar2 -> {
              if (ar2.failed()) {
                // Log error but we report success anyway
                log.error("Failed to run close hook", ar2.cause());
              }
              if (ar.succeeded()) {
                reportSuccess(null, undeployingContext, doneHandler);
              } else {
                reportFailure(ar.cause(), undeployingContext, doneHandler);
              }
            });
          });
          try {
            verticle.stop(stopFuture);
          } catch (Throwable t) {
            stopFuture.setFailure(t);
          }
        });
      }
    }

    @Override
    public void addChild(Deployment deployment) {
      children.add(deployment);
    }

  }

  /**
   * Before delegating to the parent, this classloader attempts to load the class first
   * (opposite of normal delegation model).
   * This allows multiple versions of the same class to be loaded by different classloaders which allows
   * us to isolate verticles so they can't easily interact
   */
  private static class IsolatingClassLoader extends URLClassLoader {

    private IsolatingClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);
        if (c == null) {
          // We don't want to load Vert.x (or Vert.x dependency) classes from an isolating loader
          if (isVertxOrSystemClass(name)) {
            try {
              c = super.loadClass(name, false);
            } catch (ClassNotFoundException e) {
              // Fall through
            }
          }
          if (c == null) {
            // Try and load with this classloader
            try {
              c = findClass(name);
            } catch (ClassNotFoundException e) {
              // Now try with parent
              c = super.loadClass(name, false);
            }
          }
        }
        if (resolve) {
          resolveClass(c);
        }
        return c;
      }
    }

    private boolean isVertxOrSystemClass(String name) {
      return
        name.startsWith("java.") ||
        name.startsWith("javax.") ||
        name.startsWith("com.sun.") ||
        name.startsWith("io.vertx.core") ||
        name.startsWith("com.hazelcast") ||
        name.startsWith("io.netty.");
    }
  }

}
