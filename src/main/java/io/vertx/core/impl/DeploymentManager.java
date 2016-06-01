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

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.VerticleFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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
    Collection<VerticleFactory> factories = ServiceHelper.loadFactories(VerticleFactory.class);
    factories.forEach(this::registerVerticleFactory);
    VerticleFactory defaultFactory = new JavaVerticleFactory();
    defaultFactory.init(vertx);
    defaultFactories.add(defaultFactory);
  }

  private String generateDeploymentID() {
    return UUID.randomUUID().toString();
  }

  public void deployVerticle(Verticle verticle, DeploymentOptions options,
                             Handler<AsyncResult<String>> completionHandler) {
    if (options.getInstances() != 1) {
      throw new IllegalArgumentException("Can't specify > 1 instances for already created verticle");
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
    ContextImpl currentContext = vertx.getOrCreateContext();
    doDeploy("java:" + verticle.getClass().getName(), generateDeploymentID(), options, currentContext, currentContext, completionHandler,
        getCurrentClassLoader(), verticle);
  }

  public void deployVerticle(String identifier,
                             DeploymentOptions options,
                             Handler<AsyncResult<String>> completionHandler) {
    ContextImpl callingContext = vertx.getOrCreateContext();
    ClassLoader cl = getClassLoader(options, callingContext);
    doDeployVerticle(identifier, generateDeploymentID(), options, callingContext, callingContext, cl, completionHandler);
  }

  private void doDeployVerticle(String identifier,
                                String deploymentID,
                                DeploymentOptions options,
                                ContextImpl parentContext,
                                ContextImpl callingContext,
                                ClassLoader cl,
                                Handler<AsyncResult<String>> completionHandler) {
    List<VerticleFactory> verticleFactories = resolveFactories(identifier);
    Iterator<VerticleFactory> iter = verticleFactories.iterator();
    doDeployVerticle(iter, null, identifier, deploymentID, options, parentContext, callingContext, cl, completionHandler);
  }

  private void doDeployVerticle(Iterator<VerticleFactory> iter,
                                Throwable prevErr,
                                String identifier,
                                String deploymentID,
                                DeploymentOptions options,
                                ContextImpl parentContext,
                                ContextImpl callingContext,
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
            deployVerticle(resolvedName, options, completionHandler);
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
                  doDeploy(identifier, deploymentID, options, parentContext, callingContext, completionHandler, cl, res.result());
                } else {
                  // Try the next one
                  doDeployVerticle(iter, res.cause(), identifier, deploymentID, options, parentContext, callingContext, cl, completionHandler);
                }
              });
              return;
            } else {
              try {
                Verticle[] verticles = createVerticles(verticleFactory, identifier, options.getInstances(), cl);
                doDeploy(identifier, deploymentID, options, parentContext, callingContext, completionHandler, cl, verticles);
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
        doDeployVerticle(iter, err, identifier, deploymentID, options, parentContext, callingContext, cl, completionHandler);
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

  private ClassLoader getClassLoader(DeploymentOptions options, ContextImpl parentContext) {
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

  private void doDeploy(String identifier, String deploymentID, DeploymentOptions options,
                        ContextImpl parentContext,
                        ContextImpl callingContext,
                        Handler<AsyncResult<String>> completionHandler,
                        ClassLoader tccl, Verticle... verticles) {
    if (options.isMultiThreaded() && !options.isWorker()) {
      throw new IllegalArgumentException("If multi-threaded then must be worker too");
    }
    JsonObject conf = options.getConfig() == null ? new JsonObject() : options.getConfig().copy(); // Copy it
    String poolName = options.getWorkerPoolName();

    Deployment parent = parentContext.getDeployment();
    DeploymentImpl deployment = new DeploymentImpl(parent, deploymentID, identifier, options);
    
    AtomicInteger deployCount = new AtomicInteger();
    AtomicBoolean failureReported = new AtomicBoolean();
    for (Verticle verticle: verticles) {
      WorkerExecutorImpl workerExec = poolName != null ? vertx.createSharedWorkerExecutor(poolName, options.getWorkerPoolSize()) : null;
      WorkerPool pool = workerExec != null ? workerExec.getPool() : null;
      ContextImpl context = options.isWorker() ? vertx.createWorkerContext(options.isMultiThreaded(), deploymentID, pool, conf, tccl) :
        vertx.createEventLoopContext(deploymentID, pool, conf, tccl);
      if (workerExec != null) {
        context.addCloseHook(workerExec);
      }
      context.setDeployment(deployment);
      deployment.addVerticle(new VerticleHolder(verticle, context));
      context.runOnContext(v -> {
        try {
          verticle.init(vertx, context);
          Future<Void> startFuture = Future.future();
          verticle.start(startFuture);
          startFuture.setHandler(ar -> {
            if (ar.succeeded()) {
              if (parent != null) {
                parent.addChild(deployment);
                deployment.child = true;
              }
              vertx.metricsSPI().verticleDeployed(verticle);
              deployments.put(deploymentID, deployment);
              if (deployCount.incrementAndGet() == verticles.length) {
                reportSuccess(deploymentID, callingContext, completionHandler);
              }
            } else if (!failureReported.get()) {
              reportFailure(ar.cause(), callingContext, completionHandler);
            }
          });
        } catch (Throwable t) {
          reportFailure(t, callingContext, completionHandler);
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

    private final Deployment parent;
    private final String deploymentID;
    private final String verticleIdentifier;
    private final List<VerticleHolder> verticles = new CopyOnWriteArrayList<>();
    private final Set<Deployment> children = new ConcurrentHashSet<>();
    private final DeploymentOptions options;
    private boolean undeployed;
    private volatile boolean child;

    private DeploymentImpl(Deployment parent, String deploymentID, String verticleIdentifier, DeploymentOptions options) {
      this.parent = parent;
      this.deploymentID = deploymentID;
      this.verticleIdentifier = verticleIdentifier;
      this.options = options;
    }

    public void addVerticle(VerticleHolder holder) {
      verticles.add(holder);
    }

    @Override
    public void undeploy(Handler<AsyncResult<Void>> completionHandler) {
      ContextImpl currentContext = vertx.getOrCreateContext();
      doUndeploy(currentContext, completionHandler);
    }

    public synchronized void doUndeploy(ContextImpl undeployingContext, Handler<AsyncResult<Void>> completionHandler) {
      if (undeployed) {
        reportFailure(new IllegalStateException("Already undeployed"), undeployingContext, completionHandler);
        return;
      }
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
              doUndeploy(undeployingContext, completionHandler);
            }
          });
        }
        if (!undeployedSome) {
          // It's possible that children became empty before iterating
          doUndeploy(undeployingContext, completionHandler);
        }
      } else {
        undeployed = true;
        AtomicInteger undeployCount = new AtomicInteger();
        int numToUndeploy = verticles.size();
        for (VerticleHolder verticleHolder: verticles) {
          ContextImpl context = verticleHolder.context;
          context.runOnContext(v -> {
            Future<Void> stopFuture = Future.future();
            AtomicBoolean failureReported = new AtomicBoolean();
            stopFuture.setHandler(ar -> {
              deployments.remove(deploymentID);
              vertx.metricsSPI().verticleUndeployed(verticleHolder.verticle);
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
    public void addChild(Deployment deployment) {
      children.add(deployment);
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
