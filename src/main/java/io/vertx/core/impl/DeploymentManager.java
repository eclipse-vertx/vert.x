/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.ServiceHelper;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
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
  private final Map<String, ClassLoader> classloaders = new WeakHashMap<>();
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

  public void deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
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
        if (completionHandler != null) {
          completionHandler.handle(Future.failedFuture(e));
        }
        return;
      }
      if (verticle == null) {
        if (completionHandler != null) {
          completionHandler.handle(Future.failedFuture("Supplied verticle is null"));
        }
        return;
      }
      verticles.add(verticle);
    }
    if (verticles.size() != nbInstances) {
      if (completionHandler != null) {
        completionHandler.handle(Future.failedFuture("Same verticle supplied more than once"));
      }
      return;
    }
    Verticle[] verticlesArray = verticles.toArray(new Verticle[verticles.size()]);
    String verticleClass = verticlesArray[0].getClass().getName();
    doDeploy("java:" + verticleClass, options, currentContext, currentContext, completionHandler, cl, verticlesArray);
  }

  public void deployVerticle(String identifier,
                             DeploymentOptions options,
                             Handler<AsyncResult<String>> completionHandler) {
    ContextInternal callingContext = vertx.getOrCreateContext();
    ClassLoader cl = getClassLoader(options);



    doDeployVerticle(identifier, options, callingContext, callingContext, cl, completionHandler);
  }

  private void doDeployVerticle(String identifier,
                                DeploymentOptions options,
                                ContextInternal parentContext,
                                ContextInternal callingContext,
                                ClassLoader cl,
                                Handler<AsyncResult<String>> completionHandler) {
    List<VerticleFactory> verticleFactories = resolveFactories(identifier);
    Iterator<VerticleFactory> iter = verticleFactories.iterator();
    doDeployVerticle(iter, null, identifier, options, parentContext, callingContext, cl, completionHandler);
  }

  private void doDeployVerticle(Iterator<VerticleFactory> iter,
                                Throwable prevErr,
                                String identifier,
                                DeploymentOptions options,
                                ContextInternal parentContext,
                                ContextInternal callingContext,
                                ClassLoader cl,
                                Handler<AsyncResult<String>> completionHandler) {
    if (iter.hasNext()) {
      VerticleFactory verticleFactory = iter.next();
      Future<String> fut = Future.future();
      if (verticleFactory.requiresResolve()) {
        try {
          verticleFactory.resolve(identifier, options, cl, fut);
        } catch (Exception e) {
          try {
            fut.fail(e);
          } catch (Exception ignore) {
            // Too late
          }
        }
      } else {
        fut.complete(identifier);
      }
      fut.setHandler(ar -> {
        Throwable err;
        if (ar.succeeded()) {
          String resolvedName = ar.result();
          if (!resolvedName.equals(identifier)) {
            try {
              deployVerticle(resolvedName, options, completionHandler);
            } catch (Exception e) {
              if (completionHandler != null) {
                completionHandler.handle(Future.failedFuture(e));
              }
            }
            return;
          } else {
            if (verticleFactory.blockingCreate()) {
              vertx.<Verticle[]>executeBlocking(createFut -> {
                try {
                  Verticle[] verticles = createVerticles(verticleFactory, identifier, options.getInstances(), cl);
                  createFut.complete(verticles);
                } catch (Exception e) {
                  createFut.fail(e);
                }
              }, res -> {
                if (res.succeeded()) {
                  doDeploy(identifier, options, parentContext, callingContext, completionHandler, cl, res.result());
                } else {
                  // Try the next one
                  doDeployVerticle(iter, res.cause(), identifier, options, parentContext, callingContext, cl, completionHandler);
                }
              });
              return;
            } else {
              try {
                Verticle[] verticles = createVerticles(verticleFactory, identifier, options.getInstances(), cl);
                doDeploy(identifier, options, parentContext, callingContext, completionHandler, cl, verticles);
                return;
              } catch (Exception e) {
                err = e;
              }
            }
          }
        } else {
          err = ar.cause();
        }
        // Try the next one
        doDeployVerticle(iter, err, identifier, options, parentContext, callingContext, cl, completionHandler);
      });
    } else {
      if (prevErr != null) {
        // Report failure if there are no more factories to try otherwise try the next one
        reportFailure(prevErr, callingContext, completionHandler);
      } else {
        // not handled or impossible ?
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
            completionHandler.handle(Future.succeededFuture());
          }
        });
      }
    } else {
      Context context = vertx.getOrCreateContext();
      context.runOnContext(v -> completionHandler.handle(Future.succeededFuture()));
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
        cl = classloaders.get(isolationGroup);
        if (cl == null) {
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
          cl = new IsolatingClassLoader(urls.toArray(new URL[urls.size()]), getCurrentClassLoader(),
                                        options.getIsolatedClasses());
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
      reportResult(context, completionHandler, Future.failedFuture(t));
    } else {
      log.error(t.getMessage(), t);
    }
  }

  private <T> void reportSuccess(T result, Context context, Handler<AsyncResult<T>> completionHandler) {
    if (completionHandler != null) {
      reportResult(context, completionHandler, Future.succeededFuture(result));
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

  private void doDeploy(String identifier,
                        DeploymentOptions options,
                        ContextInternal parentContext,
                        ContextInternal callingContext,
                        Handler<AsyncResult<String>> completionHandler,
                        ClassLoader tccl, Verticle... verticles) {
    String poolName = options.getWorkerPoolName();

    Deployment parent = parentContext.getDeployment();
    String deploymentID = generateDeploymentID();
    DeploymentImpl deployment = new DeploymentImpl(parent, deploymentID, identifier, options);

    AtomicInteger deployCount = new AtomicInteger();
    AtomicBoolean failureReported = new AtomicBoolean();
    for (Verticle verticle: verticles) {
      WorkerExecutorInternal workerExec = poolName != null ? vertx.createSharedWorkerExecutor(poolName, options.getWorkerPoolSize(), options.getMaxWorkerExecuteTime(), options.getMaxWorkerExecuteTimeUnit()) : null;
      WorkerPool pool = workerExec != null ? workerExec.getPool() : null;
      ContextImpl context = options.isWorker() ? (ContextImpl) vertx.createWorkerContext(deployment, pool, tccl) :
        vertx.createEventLoopContext(deployment, pool, tccl);
      if (workerExec != null) {
        context.addCloseHook(workerExec);
      }
      deployment.addVerticle(new VerticleHolder(verticle, context));
      context.runOnContext(v -> {
        try {
          verticle.init(vertx, context);
          Future<Void> startFuture = Future.future();
          verticle.start(startFuture);
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
                reportSuccess(deploymentID, callingContext, completionHandler);
              }
            } else if (failureReported.compareAndSet(false, true)) {
              deployment.rollback(callingContext, completionHandler, context, ar.cause());
            }
          });
        } catch (Throwable t) {
          if (failureReported.compareAndSet(false, true))
            deployment.rollback(callingContext, completionHandler, context, t);
        }
      });
    }
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
        doUndeployChildren(callingContext, childrenResult -> {
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
    public void undeploy(Handler<AsyncResult<Void>> completionHandler) {
      ContextInternal currentContext = vertx.getOrCreateContext();
      doUndeploy(currentContext, completionHandler);
    }

    private synchronized void doUndeployChildren(ContextInternal undeployingContext, Handler<AsyncResult<Void>> completionHandler) {
      if (!children.isEmpty()) {
        final int size = children.size();
        AtomicInteger childCount = new AtomicInteger();
        boolean undeployedSome = false;
        for (Deployment childDeployment: new HashSet<>(children)) {
          undeployedSome = true;
          childDeployment.doUndeploy(undeployingContext, ar -> {
            children.remove(childDeployment);
            if (ar.failed()) {
              reportFailure(ar.cause(), undeployingContext, completionHandler);
            } else if (childCount.incrementAndGet() == size) {
              // All children undeployed
              completionHandler.handle(Future.succeededFuture());
            }
          });
        }
        if (!undeployedSome) {
          // It's possible that children became empty before iterating
          completionHandler.handle(Future.succeededFuture());
        }
      } else {
        completionHandler.handle(Future.succeededFuture());
      }
    }

    public synchronized void doUndeploy(ContextInternal undeployingContext, Handler<AsyncResult<Void>> completionHandler) {
      if (status == ST_UNDEPLOYED) {
        reportFailure(new IllegalStateException("Already undeployed"), undeployingContext, completionHandler);
        return;
      }
      if (!children.isEmpty()) {
        status = ST_UNDEPLOYING;
        doUndeployChildren(undeployingContext, ar -> {
          if (ar.failed()) {
            reportFailure(ar.cause(), undeployingContext, completionHandler);
          } else {
            doUndeploy(undeployingContext, completionHandler);
          }
        });
      } else {
        status = ST_UNDEPLOYED;
        AtomicInteger undeployCount = new AtomicInteger();
        int numToUndeploy = verticles.size();
        for (VerticleHolder verticleHolder: verticles) {
          ContextImpl context = verticleHolder.context;
          context.runOnContext(v -> {
            Future<Void> stopFuture = Future.future();
            AtomicBoolean failureReported = new AtomicBoolean();
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
                if (ar.succeeded() && undeployCount.incrementAndGet() == numToUndeploy) {
                  reportSuccess(null, undeployingContext, completionHandler);
                } else if (ar.failed() && !failureReported.get()) {
                  failureReported.set(true);
                  reportFailure(ar.cause(), undeployingContext, completionHandler);
                }
              });
            });
            try {
              verticleHolder.verticle.stop(stopFuture);
            } catch (Throwable t) {
              stopFuture.fail(t);
            } finally {
              // Remove the deployment from any parents
              if (parent != null) {
                parent.removeChild(this);
              }
            }
          });
        }
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
