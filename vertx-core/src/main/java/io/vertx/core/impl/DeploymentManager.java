/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
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
  private final Map<String, List<VerticleFactory>> verticleFactories = new ConcurrentHashMap<>();
  private final List<VerticleFactory> defaultFactories = new ArrayList<>();

  public DeploymentManager(VertxInternal vertx) {
    this.vertx = vertx;
    loadVerticleFactories();
  }

  private void loadVerticleFactories() {
    ServiceLoader<VerticleFactory> factories = ServiceLoader.load(VerticleFactory.class);
    for (VerticleFactory factory: factories) {
      registerVerticleFactory(factory);
    }
    VerticleFactory defaultFactory = new JavaVerticleFactory();
    defaultFactory.init(vertx);
    defaultFactories.add(defaultFactory);
  }

  public void deployVerticle(Verticle verticle, DeploymentOptions options,
                             Handler<AsyncResult<String>> completionHandler) {
    ContextImpl currentContext = vertx.getOrCreateContext();
    doDeploy("java:" + verticle.getClass().getName(), verticle, options, currentContext, completionHandler,
             getCurrentClassLoader());
  }

  public void deployVerticle(String identifier,
                             DeploymentOptions options,
                             Handler<AsyncResult<String>> completionHandler) {
    doDeployVerticle(identifier, options, completionHandler);
  }

  private void doDeployVerticle(String identifier,
                                DeploymentOptions options,
                                Handler<AsyncResult<String>> completionHandler) {
    ContextImpl currentContext = vertx.getOrCreateContext();
    ClassLoader cl = getClassLoader(options);
    List<VerticleFactory> verticleFactories = resolveFactories(identifier);
    Iterator<VerticleFactory> iter = verticleFactories.iterator();
    while (iter.hasNext()) {
      try {
        VerticleFactory verticleFactory = iter.next();
        if (verticleFactory.requiresResolve()) {
          String resolvedName = verticleFactory.resolve(identifier, options, cl);
          if (!resolvedName.equals(identifier)) {
            deployVerticle(resolvedName, options, completionHandler);
            return;
          }
        }
        Verticle verticle = verticleFactory.createVerticle(identifier, cl);
        if (verticle == null) {
          throw new NullPointerException("VerticleFactory::createVerticle returned null");
        } else {
          doDeploy(identifier, verticle, options, currentContext, completionHandler, cl);
          return;
        }
      } catch (Exception e) {
        if (!iter.hasNext()) {
          // Report failure if there are no more factories to try otherwise try the next one
          reportFailure(e, currentContext, completionHandler);
        }
      }
    }
  }

  private String getSuffix(int pos, String str) {
    if (pos + 1 >= str.length()) {
      throw new IllegalArgumentException("Invalid name: " + str);
    }
    return str.substring(pos + 1);
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
    String prefix = factory.prefix();
    if (prefix == null) {
      throw new IllegalArgumentException("factory.prefix() cannot be null");
    }
    List<VerticleFactory> facts = verticleFactories.get(prefix);
    if (facts == null) {
      facts = new ArrayList<>();
      verticleFactories.put(prefix, facts);
    }
    if (facts.contains(factory)) {
      throw new IllegalArgumentException("Factory already registered");
    }
    facts.add(factory);
    // Sort list in ascending order
    facts.sort((fact1, fact2) -> fact1.order() - fact2.order());
    factory.init(vertx);
  }

  public void unregisterVerticleFactory(VerticleFactory factory) {
    String prefix = factory.prefix();
    if (prefix == null) {
      throw new IllegalArgumentException("factory.prefix() cannot be null");
    }
    List<VerticleFactory> facts = verticleFactories.get(prefix);
    boolean removed = false;
    if (facts != null) {
      if (facts.remove(factory)) {
        removed = true;
      }
      if (facts.isEmpty()) {
        verticleFactories.remove(prefix);
      }
    }
    if (!removed) {
      throw new IllegalArgumentException("factory isn't registered");
    }
  }

  public Set<VerticleFactory> verticleFactories() {
    Set<VerticleFactory> facts = new HashSet<>();
    for (List<VerticleFactory> list: verticleFactories.values()) {
      facts.addAll(list);
    }
    return facts;
  }

  private List<VerticleFactory> resolveFactories(String identifier) {
    /*
      We resolve the verticle factory list to use as follows:
      1. We look for a prefix in the identifier.
      E.g. the identifier might be "js:app.js" <-- the prefix is "js"
      If it exists we use that to lookup the verticle factory list
      2. We look for a suffix (like a file extension),
      E.g. the identifier might be just "app.js"
      If it exists we use that to lookup the factory list
      3. If there is no prefix or suffix OR there is no match then defaults will be used
    */
    List<VerticleFactory> factoryList = null;
    int pos = identifier.indexOf(':');
    String lookup = null;
    if (pos != -1) {
      // Infer factory from prefix, e.g. "java:" or "js:"
      lookup = identifier.substring(0, pos);
    } else {
      // Try and infer name from extension
      pos = identifier.lastIndexOf('.');
      if (pos != -1) {
        lookup = getSuffix(pos, identifier);
      } else {
        // No prefix, no extension - use defaults
        factoryList = defaultFactories;
      }
    }
    if (factoryList == null) {
      factoryList = verticleFactories.get(lookup);
      if (factoryList == null) {
        factoryList = defaultFactories;
      }
    }
    return factoryList;
  }

  private ClassLoader getClassLoader(DeploymentOptions options) {
    String isolationGroup = options.getIsolationGroup();
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

  private void doDeploy(String identifier, Verticle verticle, DeploymentOptions options,
                        ContextImpl currentContext,
                        Handler<AsyncResult<String>> completionHandler,
                        ClassLoader tccl) {
    if (options.isMultiThreaded() && !options.isWorker()) {
      throw new IllegalArgumentException("If multi-threaded then must be worker too");
    }
    String deploymentID = UUID.randomUUID().toString();
    JsonObject conf = options.getConfig() == null ? new JsonObject() : options.getConfig().copy(); // Copy it
    ContextImpl context = options.isWorker() ? vertx.createWorkerContext(options.isMultiThreaded(), deploymentID, conf, tccl) :
                                               vertx.createEventLoopContext(deploymentID, conf, tccl);

    DeploymentImpl deployment = new DeploymentImpl(deploymentID, context, identifier, verticle, options);
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
            vertx.metricsSPI().verticleDeployed(verticle);
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
    private final String identifier;
    private final Verticle verticle;
    private final Set<Deployment> children = new ConcurrentHashSet<>();
    private final DeploymentOptions options;
    private boolean undeployed;
    private volatile boolean child;

    private DeploymentImpl(String id, ContextImpl context, String identifier, Verticle verticle, DeploymentOptions options) {
      this.id = id;
      this.context = context;
      this.identifier = identifier;
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
            vertx.metricsSPI().verticleUndeployed(verticle);
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
            stopFuture.fail(t);
          }
        });
      }
    }

    @Override
    public String identifier() {
      return identifier;
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
