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
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.ServiceHelper;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeploymentManager {

  private static final Logger log = LoggerFactory.getLogger(DeploymentManager.class);

  private final VertxInternal vertx;
  private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();
  private final Map<String, IsolatingClassLoader> classloaders = new HashMap<>();
  private final Map<String, List<VerticleFactory>> verticleFactories = new ConcurrentHashMap<>();
  private final List<VerticleFactory> defaultFactories = new ArrayList<>();

  public DeploymentManager(VertxInternal vertx) {
    this.vertx = vertx;
    loadVerticleFactories();
  }

  private void loadVerticleFactories() {
    Collection<VerticleFactory> factories = ServiceHelper.loadFactories(VerticleFactory.class);
    factories.forEach(this::registerVerticleFactory);
    VerticleFactory defaultFactory = new JavaVerticleFactory();
    defaultFactory.init(vertx);
    defaultFactories.add(defaultFactory);
  }

  private String generateDeploymentID() {
    return UUID.randomUUID().toString();
  }

  public Future<String> deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options) {
    if (options.getInstances() < 1) {
      throw new IllegalArgumentException("Can't specify < 1 instances to deploy");
    }
    if (options.getExtraClasspath() != null) {
      throw new IllegalArgumentException("Can't specify extraClasspath for already created verticle");
    }
    if (options.getIsolationGroup() != null) {
      throw new IllegalArgumentException("Can't specify isolationGroup for already created verticle");
    }
    if (options.getIsolatedClasses() != null) {
      throw new IllegalArgumentException("Can't specify isolatedClasses for already created verticle");
    }
    ContextInternal currentContext = vertx.getOrCreateContext();
    ClassLoader cl = getClassLoader(options);
    int nbInstances = options.getInstances();
    Set<Verticle> verticles = Collections.newSetFromMap(new IdentityHashMap<>());
    for (int i = 0; i < nbInstances; i++) {
      Verticle verticle;
      try {
        verticle = verticleSupplier.get();
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
    Verticle[] verticlesArray = verticles.toArray(new Verticle[verticles.size()]);
    String verticleClass = verticlesArray[0].getClass().getName();
    return doDeploy("java:" + verticleClass, options, currentContext, currentContext, cl, verticlesArray);
  }

  public Future<String> deployVerticle(String identifier,
                             DeploymentOptions options) {
    ContextInternal callingContext = vertx.getOrCreateContext();
    ClassLoader cl = getClassLoader(options);



    return doDeployVerticle(identifier, options, callingContext, callingContext, cl);
  }

  private Future<String> doDeployVerticle(String identifier,
                                DeploymentOptions options,
                                ContextInternal parentContext,
                                ContextInternal callingContext,
                                ClassLoader cl) {
    List<VerticleFactory> verticleFactories = resolveFactories(identifier);
    Iterator<VerticleFactory> iter = verticleFactories.iterator();
    return doDeployVerticle(iter, null, identifier, options, parentContext, callingContext, cl);
  }

  private Future<String> doDeployVerticle(Iterator<VerticleFactory> iter,
                                Throwable prevErr,
                                String identifier,
                                DeploymentOptions options,
                                ContextInternal parentContext,
                                ContextInternal callingContext,
                                ClassLoader cl) {
    if (iter.hasNext()) {
      VerticleFactory verticleFactory = iter.next();
      Promise<String> promise = callingContext.promise();
      if (verticleFactory.requiresResolve()) {
        try {
          verticleFactory.resolve(identifier, options, cl, promise);
        } catch (Exception e) {
          try {
            promise.fail(e);
          } catch (Exception ignore) {
            // Too late
          }
        }
      } else {
        promise.complete(identifier);
      }
      return promise
        .future()
        .compose(resolvedName -> {
          if (!resolvedName.equals(identifier)) {
            return deployVerticle(resolvedName, options);
          } else {
            if (verticleFactory.blockingCreate()) {
              return vertx.<Verticle[]>executeBlocking(createFut -> {
                try {
                  Verticle[] verticles = createVerticles(verticleFactory, identifier, options.getInstances(), cl);
                  createFut.complete(verticles);
                } catch (Exception e) {
                  createFut.fail(e);
                }
              }).compose(verticles -> doDeploy(identifier, options, parentContext, callingContext, cl, verticles));
            } else {
              try {
                Verticle[] verticles = createVerticles(verticleFactory, identifier, options.getInstances(), cl);
                return doDeploy(identifier, options, parentContext, callingContext, cl, verticles);
              } catch (Exception e) {
                return Future.failedFuture(e);
              }
            }
          }
        }).recover(err -> {
        // Try the next one
        return doDeployVerticle(iter, err, identifier, options, parentContext, callingContext, cl);
      });
    } else {
      if (prevErr != null) {
        // Report failure if there are no more factories to try otherwise try the next one
        return callingContext.failedFuture(prevErr);
      } else {
        // not handled or impossible ?
        throw new UnsupportedOperationException();
      }
    }
  }

  private Verticle[] createVerticles(VerticleFactory verticleFactory, String identifier, int instances, ClassLoader cl) throws Exception {
    Verticle[] verticles = new Verticle[instances];
    for (int i = 0; i < instances; i++) {
      verticles[i] = verticleFactory.createVerticle(identifier, cl);
      if (verticles[i] == null) {
        throw new NullPointerException("VerticleFactory::createVerticle returned null");
      }
    }
    return verticles;
  }

  private String getSuffix(int pos, String str) {
    if (pos + 1 >= str.length()) {
      throw new IllegalArgumentException("Invalid name: " + str);
    }
    return str.substring(pos + 1);
  }

  public Future<Void> undeployVerticle(String deploymentID) {
    Deployment deployment = deployments.get(deploymentID);
    Context currentContext = vertx.getOrCreateContext();
    if (deployment == null) {
      return ((ContextInternal) currentContext).failedFuture(new IllegalStateException("Unknown deployment"));
    } else {
      return deployment.undeploy();
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
    List<Future> completionList = new ArrayList<>();
    if (!deploymentIDs.isEmpty()) {
      for (String deploymentID : deploymentIDs) {
        Promise<Void> promise = Promise.promise();
        completionList.add(promise.future());
        undeployVerticle(deploymentID).setHandler(ar -> {
          if (ar.failed()) {
            // Log but carry on regardless
            log.error("Undeploy failed", ar.cause());
          }
          promise.handle(ar);
        });
      }
      Promise<Void> promise = vertx.getOrCreateContext().promise();
      CompositeFuture.join(completionList).<Void>mapEmpty().setHandler(promise);
      return promise.future();
    } else {
      return vertx.getOrCreateContext().succeededFuture();
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

  /**
   * <strong>IMPORTANT</strong> - Isolation groups are not supported on Java 9+ because the application classloader is not
   * an URLClassLoader anymore. Thus we can't extract the list of jars to configure the IsolatedClassLoader.
   *
   */
  private ClassLoader getClassLoader(DeploymentOptions options) {
    String isolationGroup = options.getIsolationGroup();
    ClassLoader cl;
    if (isolationGroup == null) {
      cl = getCurrentClassLoader();
    } else {
      // IMPORTANT - Isolation groups are not supported on Java 9+, because the system classloader is not an URLClassLoader
      // anymore. Thus we can't extract the paths from the classpath and isolate the loading.
      synchronized (this) {
        IsolatingClassLoader icl = classloaders.get(isolationGroup);
        if (icl == null) {
          ClassLoader current = getCurrentClassLoader();
          if (!(current instanceof URLClassLoader)) {
            throw new IllegalStateException("Current classloader must be URLClassLoader");
          }
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
          URLClassLoader urlc = (URLClassLoader)current;
          urls.addAll(Arrays.asList(urlc.getURLs()));

          // Create an isolating cl with the urls
          icl = new IsolatingClassLoader(urls.toArray(new URL[urls.size()]), getCurrentClassLoader(),
            options.getIsolatedClasses());
          classloaders.put(isolationGroup, icl);
        }
        icl.refCount += options.getInstances();
        cl = icl;
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
      reportResult(context, completionHandler, Future.failedFuture(t));
    } else {
      log.error(t.getMessage(), t);
    }
  }

  private <T> void reportResult(Context context, Handler<AsyncResult<T>> completionHandler, AsyncResult<T> result) {
    context.runOnContext(v -> {
      try {
        completionHandler.handle(result);
      } catch (Throwable t) {
        log.error("Failure in calling handler", t);
        throw t;
      }
    });
  }

  private Future<String> doDeploy(String identifier,
                        DeploymentOptions options,
                        ContextInternal parentContext,
                        ContextInternal callingContext,
                        ClassLoader tccl, Verticle... verticles) {
    Promise<String> promise = callingContext.promise();
    String poolName = options.getWorkerPoolName();

    Deployment parent = parentContext.getDeployment();
    String deploymentID = generateDeploymentID();
    DeploymentImpl deployment = new DeploymentImpl(parent, deploymentID, identifier, options);

    AtomicInteger deployCount = new AtomicInteger();
    AtomicBoolean failureReported = new AtomicBoolean();
    for (Verticle verticle: verticles) {
      WorkerExecutorInternal workerExec = poolName != null ? vertx.createSharedWorkerExecutor(poolName, options.getWorkerPoolSize(), options.getMaxWorkerExecuteTime(), options.getMaxWorkerExecuteTimeUnit()) : null;
      WorkerPool pool = workerExec != null ? workerExec.getPool() : null;
      ContextImpl context = (ContextImpl) (options.isWorker() ? vertx.createWorkerContext(deployment, pool, tccl) :
        vertx.createEventLoopContext(deployment, pool, tccl));
      if (workerExec != null) {
        context.addCloseHook(workerExec);
      }
      deployment.addVerticle(new VerticleHolder(verticle, context));
      context.runOnContext(v -> {
        try {
          verticle.init(vertx, context);
          Promise<Void> startPromise = context.promise();
          Future<Void> startFuture = startPromise.future();
          verticle.start(startPromise);
          startFuture.setHandler(ar -> {
            if (ar.succeeded()) {
              if (parent != null) {
                if (parent.addChild(deployment)) {
                  deployment.child = true;
                } else {
                  // Orphan
                  deployment.undeploy(null);
                  return;
                }
              }
              VertxMetrics metrics = vertx.metricsSPI();
              if (metrics != null) {
                metrics.verticleDeployed(verticle);
              }
              deployments.put(deploymentID, deployment);
              if (deployCount.incrementAndGet() == verticles.length) {
                promise.complete(deploymentID);
              }
            } else if (failureReported.compareAndSet(false, true)) {
              deployment.rollback(callingContext, promise, context, ar.cause());
            }
          });
        } catch (Throwable t) {
          if (failureReported.compareAndSet(false, true))
            deployment.rollback(callingContext, promise, context, t);
        }
      });
    }

    return promise.future();
  }

  static class VerticleHolder {
    final Verticle verticle;
    final ContextImpl context;

    VerticleHolder(Verticle verticle, ContextImpl context) {
      this.verticle = verticle;
      this.context = context;
    }
  }

  private class DeploymentImpl implements Deployment {

    private static final int ST_DEPLOYED = 0, ST_UNDEPLOYING = 1, ST_UNDEPLOYED = 2;

    private final Deployment parent;
    private final String deploymentID;
    private final JsonObject conf;
    private final String verticleIdentifier;
    private final List<VerticleHolder> verticles = new CopyOnWriteArrayList<>();
    private final Set<Deployment> children = new ConcurrentHashSet<>();
    private final DeploymentOptions options;
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

    private synchronized void rollback(ContextInternal callingContext, Handler<AsyncResult<String>> completionHandler, ContextImpl context, Throwable cause) {
      if (status == ST_DEPLOYED) {
        status = ST_UNDEPLOYING;
        doUndeployChildren(callingContext).setHandler(childrenResult -> {
          synchronized (DeploymentImpl.this) {
            status = ST_UNDEPLOYED;
          }
          if (childrenResult.failed()) {
            reportFailure(cause, callingContext, completionHandler);
          } else {
            context.runCloseHooks(closeHookAsyncResult -> reportFailure(cause, callingContext, completionHandler));
          }
        });
      }
    }

    @Override
    public Future<Void> undeploy() {
      ContextInternal currentContext = vertx.getOrCreateContext();
      return doUndeploy(currentContext);
    }

    private synchronized Future<Void> doUndeployChildren(ContextInternal undeployingContext) {
      if (!children.isEmpty()) {
        List<Future> childFuts = new ArrayList<>();
        for (Deployment childDeployment: new HashSet<>(children)) {
          Promise<Void> p = Promise.promise();
          childFuts.add(p.future());
          childDeployment.doUndeploy(undeployingContext, ar -> {
            children.remove(childDeployment);
            p.handle(ar);
          });
        }
        return CompositeFuture.all(childFuts).mapEmpty();
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
        List<Future> undeployFutures = new ArrayList<>();
        if (parent != null) {
          parent.removeChild(this);
        }
        for (VerticleHolder verticleHolder: verticles) {
          ContextImpl context = verticleHolder.context;
          Promise p = Promise.promise();
          undeployFutures.add(p.future());
          context.runOnContext(v -> {
            Promise<Void> stopPromise = Promise.promise();
            Future<Void> stopFuture = stopPromise.future();
            stopFuture.setHandler(ar -> {
              deployments.remove(deploymentID);
              VertxMetrics metrics = vertx.metricsSPI();
              if (metrics != null) {
                metrics.verticleUndeployed(verticleHolder.verticle);
              }
              context.runCloseHooks(ar2 -> {
                if (ar2.failed()) {
                  // Log error but we report success anyway
                  log.error("Failed to run close hook", ar2.cause());
                }
                String group = options.getIsolationGroup();
                if (group != null) {
                  synchronized (DeploymentManager.this) {
                    IsolatingClassLoader icl = classloaders.get(group);
                    if (--icl.refCount == 0) {
                      classloaders.remove(group);
                      try {
                        icl.close();
                      } catch (IOException e) {
                        log.debug("Issue when closing isolation group loader", e);
                      }
                    }
                  }
                }
                if (ar.succeeded()) {
                  p.complete();
                } else if (ar.failed()) {
                  p.fail(ar.cause());
                }
              });
            });
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
        CompositeFuture.all(undeployFutures).<Void>mapEmpty().setHandler(resolvingPromise);
        return resolvingPromise.future();
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
    public Set<Verticle> getVerticles() {
      Set<Verticle> verts = new HashSet<>();
      for (VerticleHolder holder: verticles) {
        verts.add(holder.verticle);
      }
      return verts;
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
