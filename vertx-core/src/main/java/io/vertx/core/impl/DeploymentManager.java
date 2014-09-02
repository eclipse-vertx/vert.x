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
import io.vertx.core.impl.verticle.SimpleJavaVerticleFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.spi.VerticleFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
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
  private Map<String, VerticleFactory> verticleFactories = new ConcurrentHashMap<>();
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
      String prefix = factory.prefix();
      if (verticleFactories.containsKey(prefix)) {
        log.warn("Not loading verticle factory: " + factory + " as prefix " + prefix + " is already in use");
      } else {
        verticleFactories.put(prefix, factory);
      }
    }
  }

  public void deployVerticle(Verticle verticle, DeploymentOptions options,
                             Handler<AsyncResult<String>> completionHandler) {
    ContextImpl currentContext = vertx.getOrCreateContext();
    doDeploy("java:" + verticle.getClass().getName(), verticle, options, currentContext, completionHandler);
  }

  public void deployVerticle(String verticleName,
                             DeploymentOptions options,
                             Handler<AsyncResult<String>> completionHandler) {
    ContextImpl currentContext = vertx.getOrCreateContext();
    ClassLoader cl = getClassLoader(options.getIsolationGroup(), options);
    int pos = verticleName.indexOf(':');
    if (pos == -1) {
      throw new IllegalArgumentException("verticleName must start with prefix");
    }
    String prefix = verticleName.substring(0, pos);
    if (pos + 1 >= verticleName.length()) {
      throw new IllegalArgumentException("Invalid name: " + verticleName);
    }
    String actualName = verticleName.substring(pos + 1);
    VerticleFactory verticleFactory = verticleFactories.get(prefix);
    if (verticleFactory == null) {
      // Use default Java verticle factory
      verticleFactory = DEFAULT_VERTICLE_FACTORY;
    }
    try {
      Verticle verticle = verticleFactory.createVerticle(actualName, cl);
      if (verticle == null) {
        reportFailure(new NullPointerException("VerticleFactory::createVerticle returned null"), currentContext, completionHandler);
      } else {
        doDeploy(verticleName, verticle, options, currentContext, completionHandler);
      }
    } catch (Exception e) {
      reportFailure(e, currentContext, completionHandler);
    }
  }

  public void undeployVerticle(String deploymentID, Handler<AsyncResult<Void>> completionHandler) {
    Deployment deployment = deployments.get(deploymentID);
    Context currentContext = vertx.getOrCreateContext();
    if (deployment == null) {
      reportFailure(new IllegalStateException("Unknown deployment"), currentContext, completionHandler);
    } else {
      deployment.undeploy(completionHandler);
    }
  }

  public Set<String> deployments() {
    return Collections.unmodifiableSet(deployments.keySet());
  }

  public Deployment getDeployment(String deploymentID) {
    return deployments.get(deploymentID);
  }

  public void undeployAll(Handler<AsyncResult<Void>> completionHandler) {
    // TODO timeout if it takes too long - e.g. async stop verticle fails to call future

    // We only deploy the top level verticles as the children will be undeployed when the parent is
    Set<String> deploymentIDs = new HashSet<>();
    for (Map.Entry<String, Deployment> entry: deployments.entrySet()) {
      if (!entry.getValue().isChild()) {
        deploymentIDs.add(entry.getKey());
      }
    }
    if (!deploymentIDs.isEmpty()) {
      AtomicInteger count = new AtomicInteger(0);
      for (String deploymentID : deploymentIDs) {
        undeployVerticle(deploymentID, ar -> {
          if (ar.failed()) {
            // Log but carry on regardless
            log.error("Undeploy failed", ar.cause());
          }
          if (count.incrementAndGet() == deploymentIDs.size()) {
            completionHandler.handle(Future.completedFuture());
          }
        });
      }
    } else {
      Context context = vertx.getOrCreateContext();
      context.runOnContext(v -> completionHandler.handle(Future.completedFuture()));
    }
  }

  public void registerVerticleFactory(VerticleFactory factory) {
    if (factory.prefix() == null) {
      throw new IllegalArgumentException("factory.prefix() cannot be null");
    }
    if (verticleFactories.containsKey(factory.prefix())) {
      throw new IllegalArgumentException("There is already a registered verticle factory with prefix " + factory.prefix());
    }
    verticleFactories.put(factory.prefix(), factory);
  }

  public void unregisterVerticleFactory(VerticleFactory factory) {
    if (verticleFactories.remove(factory.prefix()) == null) {
      throw new IllegalArgumentException("Factory " + factory + " is not registered");
    }
  }

  public Set<VerticleFactory> verticleFactories() {
    return new HashSet<>(verticleFactories.values());
  }

  private ClassLoader getClassLoader(String isolationGroup, DeploymentOptions options) {
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
          List<URL> urls = new ArrayList<>();
          // Add any extra URLs to the beginning of the classpath
          List<String> extraClasspath = options.getExtraClasspath();
          if (extraClasspath != null) {
            for (String pathElement: extraClasspath) {
              File file = new File(pathElement);
              try {
                URL url = file.toURI().toURL();
                urls.add(url);
              } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
              }
            }
          }
          // And add the URLs of the Vert.x classloader
          urls.addAll(Arrays.asList(urlc.getURLs()));
          // Copy the URLS into the isolating classloader
          cl = new IsolatingClassLoader(urls.toArray(new URL[urls.size()]), getCurrentClassLoader());
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


  private <T> void reportFailure(Throwable t, Context context, Handler<AsyncResult<T>> completionHandler) {
    if (completionHandler != null) {
      reportResult(context, completionHandler, Future.completedFuture(t));
    } else {
      log.error(t.getMessage(), t);
    }
  }

  private <T> void reportSuccess(T result, Context context, Handler<AsyncResult<T>> completionHandler) {
    if (completionHandler != null) {
      reportResult(context, completionHandler, Future.completedFuture(result));
    }
  }

  private <T> void reportResult(Context context, Handler<AsyncResult<T>> completionHandler, AsyncResult<T> result) {
    context.runOnContext(v -> {
      try {
        completionHandler.handle(result);
      } catch (Throwable t) {
        log.error("Failure in calling handler", t);
      }
    });
  }

  private void doDeploy(String verticleName, Verticle verticle, DeploymentOptions options,
                        ContextImpl currentContext,
                        Handler<AsyncResult<String>> completionHandler) {
    if (options.isMultiThreaded() && !options.isWorker()) {
      throw new IllegalArgumentException("If multi-threaded then must be worker too");
    }
    String deploymentID = UUID.randomUUID().toString();
    JsonObject conf = options.getConfig() == null ? new JsonObject() : options.getConfig().copy(); // Copy it
    ContextImpl context = options.isWorker() ? vertx.createWorkerContext(options.isMultiThreaded(), deploymentID, conf) :
                                               vertx.createEventLoopContext(deploymentID, conf);

    DeploymentImpl deployment = new DeploymentImpl(deploymentID, context, verticleName, verticle, options);
    context.setDeployment(deployment);
    Deployment parent = currentContext.getDeployment();
    if (parent != null) {
      parent.addChild(deployment);
      deployment.child = true;
    }
    context.runOnContext(v -> {
      try {
        verticle.setVertx(vertx);
        Future<Void> startFuture = Future.future();
        verticle.start(startFuture);
        startFuture.setHandler(ar -> {
          if (ar.succeeded()) {
            deployments.put(deploymentID, deployment);
            reportSuccess(deploymentID, currentContext, completionHandler);
          } else {
            reportFailure(ar.cause(), currentContext, completionHandler);
          }
        });
      } catch (Throwable t) {
        reportFailure(t, currentContext, completionHandler);
      }
    });
  }

  private class DeploymentImpl implements Deployment {

    private final String id;
    private final ContextImpl context;
    private final String verticleName;
    private final Verticle verticle;
    private final Set<Deployment> children = new ConcurrentHashSet<>();
    private final DeploymentOptions options;
    private boolean undeployed;
    private volatile boolean child;

    private DeploymentImpl(String id, ContextImpl context, String verticleName, Verticle verticle, DeploymentOptions options) {
      this.id = id;
      this.context = context;
      this.verticleName = verticleName;
      this.verticle = verticle;
      this.options = options;
    }

    @Override
    public void undeploy(Handler<AsyncResult<Void>> completionHandler) {
      ContextImpl currentContext = vertx.getOrCreateContext();
      if (!undeployed) {
        doUndeploy(currentContext, completionHandler);
      } else {
        reportFailure(new IllegalStateException("Already undeployed"), currentContext, completionHandler);
      }
    }

    public void doUndeploy(ContextImpl undeployingContext, Handler<AsyncResult<Void>> completionHandler) {
      if (!children.isEmpty()) {
        final int size = children.size();
        AtomicInteger childCount = new AtomicInteger();
        for (Deployment childDeployment: new HashSet<>(children)) {
          childDeployment.doUndeploy(undeployingContext, ar -> {
            children.remove(childDeployment);
            if (ar.failed()) {
              reportFailure(ar.cause(), undeployingContext, completionHandler);
            } else if (childCount.incrementAndGet() == size) {
              // All children undeployed
              doUndeploy(undeployingContext, completionHandler);
            }
          });
        }
      } else {
        undeployed = true;
        context.runOnContext(v -> {
          Future<Void> stopFuture = Future.future();
          stopFuture.setHandler(ar -> {
            deployments.remove(id);
            context.runCloseHooks(ar2 -> {
              if (ar2.failed()) {
                // Log error but we report success anyway
                log.error("Failed to run close hook", ar2.cause());
              }
              if (ar.succeeded()) {
                reportSuccess(null, undeployingContext, completionHandler);
              } else {
                reportFailure(ar.cause(), undeployingContext, completionHandler);
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
    public String verticleName() {
      return verticleName;
    }

    @Override
    public DeploymentOptions deploymentOptions() {
      return options;
    }

    @Override
    public synchronized void addChild(Deployment deployment) {
      children.add(deployment);
    }

    @Override
    public Verticle getVerticle() {
      return verticle;
    }

    @Override
    public boolean isChild() {
      return child;
    }

  }

}
