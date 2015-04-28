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
import java.net.URISyntaxException;
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
    ServiceLoader<VerticleFactory> factories = ServiceLoader.load(VerticleFactory.class);
    for (VerticleFactory factory: factories) {
      registerVerticleFactory(factory);
    }
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
      throw new IllegalArgumentException("Can't specify extraClasspath instances for already created verticle");
    }
    if (options.getIsolationGroup() != null) {
      throw new IllegalArgumentException("Can't specify isolationGroup instances for already created verticle");
    }
    ContextImpl currentContext = vertx.getOrCreateContext();
    doDeploy("java:" + verticle.getClass().getName(), generateDeploymentID(), options, currentContext, currentContext, completionHandler,
        getCurrentClassLoader(), null, verticle);
  }

  public void deployVerticle(String identifier,
                             DeploymentOptions options,
                             Handler<AsyncResult<String>> completionHandler) {
    ContextImpl callingContext = vertx.getOrCreateContext();
    ClassLoader cl = getClassLoader(options, callingContext);
    Redeployer redeployer = getRedeployer(options, cl, callingContext);
    doDeployVerticle(identifier, generateDeploymentID(), options, callingContext, callingContext, cl, redeployer, completionHandler);
  }

  private void doRedeployVerticle(String identifier,
                                  String deploymentID,
                                  DeploymentOptions options,
                                  ContextImpl parentContext,
                                  ContextImpl callingContext,
                                  Redeployer redeployer,
                                  Handler<AsyncResult<String>> completionHandler) {
    ClassLoader cl = getClassLoader(options, parentContext);
    doDeployVerticle(identifier, deploymentID, options, parentContext, callingContext, cl, redeployer, completionHandler);
  }

  private void doDeployVerticle(String identifier,
                                String deploymentID,
                                DeploymentOptions options,
                                ContextImpl parentContext,
                                ContextImpl callingContext,
                                ClassLoader cl,
                                Redeployer redeployer,
                                Handler<AsyncResult<String>> completionHandler) {
    List<VerticleFactory> verticleFactories = resolveFactories(identifier);
    Iterator<VerticleFactory> iter = verticleFactories.iterator();
    doDeployVerticle(iter, null, identifier, deploymentID, options, parentContext, callingContext, cl, redeployer, completionHandler);
  }

  private void doDeployVerticle(Iterator<VerticleFactory> iter,
                                Throwable prevErr,
                                String identifier,
                                String deploymentID,
                                DeploymentOptions options,
                                ContextImpl parentContext,
                                ContextImpl callingContext,
                                ClassLoader cl,
                                Redeployer redeployer,
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
            Verticle[] verticles = new Verticle[options.getInstances()];
            try {
              for (int i = 0; i < options.getInstances(); i++) {
                verticles[i] = verticleFactory.createVerticle(identifier, cl);
                if (verticles[i] == null) {
                  throw new NullPointerException("VerticleFactory::createVerticle returned null");
                }
              }
              doDeploy(identifier, deploymentID, options, parentContext, callingContext, completionHandler, cl, redeployer, verticles);
              return;
            } catch (Exception e) {
              err = e;
            }
          }
        } else {
          err = ar.cause();
        }
        doDeployVerticle(iter, err, identifier, deploymentID, options, parentContext, callingContext, cl, redeployer, completionHandler);
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

  private boolean isTopMostDeployment(ContextImpl context) {
    return context.getDeployment() == null;
  }

  private ClassLoader getClassLoader(DeploymentOptions options, ContextImpl parentContext) {
    if (shouldEnableRedployment(options, parentContext)) {
      // For redeploy we need to use a unique value of isolationGroup as we need a new classloader each time
      // to ensure classes get reloaded, so we overwrite any value of isolation group
      // Also we only do redeploy on top most deploymentIDs
      setRedeployIsolationGroup(options);
    }
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

  private void setRedeployIsolationGroup(DeploymentOptions options) {
    options.setIsolationGroup("redeploy-" + UUID.randomUUID().toString());
  }

  private boolean shouldEnableRedployment(DeploymentOptions options, ContextImpl parentContext) {
    return options.isRedeploy() && isTopMostDeployment(parentContext);
  }

  private Redeployer getRedeployer(DeploymentOptions options, ClassLoader cl, ContextImpl parentContext) {
    if (shouldEnableRedployment(options, parentContext)) {
      // We only do redeploy on top most deploymentIDs
      setRedeployIsolationGroup(options);

      URLClassLoader urlc = (URLClassLoader)cl;
      // Convert cp to files
      Set<File> filesToWatch = new HashSet<>();
      for (URL url: urlc.getURLs()) {
        try {
          filesToWatch.add(new File(url.toURI()));
        } catch (IllegalArgumentException | URISyntaxException ignore) {
          // Probably non file url
        }
      }
      return new Redeployer(filesToWatch, options.getRedeployGracePeriod());
    } else {
      return null;
    }
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
      }
    });
  }

  private void doDeploy(String identifier, String deploymentID, DeploymentOptions options,
                        ContextImpl parentContext,
                        ContextImpl callingContext,
                        Handler<AsyncResult<String>> completionHandler,
                        ClassLoader tccl, Redeployer redeployer, Verticle... verticles) {
    if (options.isMultiThreaded() && !options.isWorker()) {
      throw new IllegalArgumentException("If multi-threaded then must be worker too");
    }
    JsonObject conf = options.getConfig() == null ? new JsonObject() : options.getConfig().copy(); // Copy it

    DeploymentImpl deployment = new DeploymentImpl(deploymentID, identifier, options, redeployer, parentContext);

    Deployment parent = parentContext.getDeployment();
    if (parent != null) {
      parent.addChild(deployment);
      deployment.child = true;
    }
    AtomicInteger deployCount = new AtomicInteger();
    AtomicBoolean failureReported = new AtomicBoolean();
    for (Verticle verticle: verticles) {
      ContextImpl context = options.isWorker() ? vertx.createWorkerContext(options.isMultiThreaded(), deploymentID, conf, tccl) :
        vertx.createEventLoopContext(deploymentID, conf, tccl);
      context.setDeployment(deployment);
      deployment.addVerticle(new VerticleHolder(verticle, context));
      context.runOnContext(v -> {
        try {
          verticle.init(vertx, context);
          Future<Void> startFuture = Future.future();
          verticle.start(startFuture);
          startFuture.setHandler(ar -> {
            if (ar.succeeded()) {
              vertx.metricsSPI().verticleDeployed(verticle);
              deployments.put(deploymentID, deployment);
              if (deployCount.incrementAndGet() == verticles.length) {
                reportSuccess(deploymentID, callingContext, completionHandler);
                deployment.startRedeployTimer();
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

    private final String deploymentID;
    private final String verticleIdentifier;
    private List<VerticleHolder> verticles = new ArrayList<>();
    private final Set<Deployment> children = new ConcurrentHashSet<>();
    private final DeploymentOptions options;
    private final Redeployer redeployer;
    private final ContextImpl parentContext; // This is the context that did the deploy, not the verticle context
    private boolean undeployed;
    private boolean broken;
    private volatile boolean child;
    private long redeployTimerID = -1;

    private DeploymentImpl(String deploymentID, String verticleIdentifier, DeploymentOptions options, Redeployer redeployer,
                           ContextImpl parentContext) {
      this.deploymentID = deploymentID;
      this.verticleIdentifier = verticleIdentifier;
      this.options = options;
      this.redeployer = redeployer;
      this.parentContext = parentContext;
    }

    public void addVerticle(VerticleHolder holder) {
      verticles.add(holder);
    }

    @Override
    public void undeploy(Handler<AsyncResult<Void>> completionHandler) {
      if (redeployTimerID != -1) {
        vertx.cancelTimer(redeployTimerID);
      }
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
        AtomicInteger undeployCount = new AtomicInteger();
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
                if (ar.succeeded() && undeployCount.incrementAndGet() == verticles.size()) {
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
    public synchronized void addChild(Deployment deployment) {
      children.add(deployment);
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

    // This is run on the context of the actual verticle, not the context that did the deploy
    private void startRedeployTimer() {
      // Redeployment is disabled.
      if (redeployer != null) {
        doStartRedeployTimer();
      }
    }

    private void doStartRedeployTimer() {
      redeployTimerID = vertx.setTimer(options.getRedeployScanPeriod(), tid -> {
        vertx.executeBlockingInternal(redeployer, res -> {
          if (res.succeeded()) {
            if (res.result()) {
              doRedeploy();
            } else if (!undeployed || broken) {
              doStartRedeployTimer();
            }
          } else {
            log.error("Failure in redeployer", res.cause());
          }
        });
      });
    }

    private void doRedeploy() {
      log.trace("Redeploying!");
      log.trace("Undeploying " + this.deploymentID);
      if (!broken) {
        undeploy(res -> {
          if (res.succeeded()) {
            log.trace("Undeployed ok");
            tryRedeploy();
          } else {
            log.error("Can't find verticle to undeploy", res.cause());
          }
        });
      } else {
        tryRedeploy();
      }
    }

    private void tryRedeploy() {
      ContextImpl callingContext = vertx.getContext();
      doRedeployVerticle(verticleIdentifier, deploymentID, options, parentContext, callingContext, redeployer, res2 -> {
        if (res2.succeeded()) {
          broken = false;
          undeployed = false;
          log.trace("Redeployed ok");
        } else {
          log.trace("Failed to deploy!!");
          broken = true;
          doStartRedeployTimer();
        }
      });
    }
  }

}
