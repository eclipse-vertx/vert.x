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
package io.vertx.core.impl.verticle;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.ServiceHelper;
import io.vertx.core.Verticle;
import io.vertx.core.impl.*;
import io.vertx.core.impl.deployment.Deployable;
import io.vertx.core.impl.deployment.Deployment;
import io.vertx.core.impl.deployment.DeploymentManager;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.spi.VerticleFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VerticleManager {

  private final Logger log;
  private final VertxImpl vertx;
  private final DeploymentManager deploymentManager;
  private final Map<String, List<VerticleFactory>> verticleFactories = new ConcurrentHashMap<>();
  private final List<VerticleFactory> defaultFactories = new ArrayList<>();

  public VerticleManager(VertxInternal vertx, Logger log, DeploymentManager deploymentManager) {
    this.vertx = (VertxImpl) vertx;
    this.deploymentManager = deploymentManager;
    this.log = log;
    loadVerticleFactories();
  }

  private void loadVerticleFactories() {
    Collection<VerticleFactory> factories = ServiceHelper.loadFactories(VerticleFactory.class);
    factories.forEach(this::registerVerticleFactory);
    VerticleFactory defaultFactory = new JavaVerticleFactory();
    defaultFactory.init(vertx);
    defaultFactories.add(defaultFactory);
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

  private static String getSuffix(int pos, String str) {
    if (pos + 1 >= str.length()) {
      throw new IllegalArgumentException("Invalid name: " + str);
    }
    return str.substring(pos + 1);
  }

  public Future<Deployment> deployVerticle(String identifier,
                                           DeploymentOptions options) {
    ContextInternal callingContext = vertx.getOrCreateContext();
    ClassLoader loader = options.getClassLoader();
    if (loader == null) {
      loader = getCurrentClassLoader();
    }
    return doDeployVerticle(identifier, options, callingContext, callingContext, loader);
  }

  private Future<Deployment> doDeployVerticle(String identifier,
                                          DeploymentOptions options,
                                          ContextInternal parentContext,
                                          ContextInternal callingContext,
                                          ClassLoader cl) {
    List<VerticleFactory> verticleFactories = resolveFactories(identifier);
    Iterator<VerticleFactory> iter = verticleFactories.iterator();
    return doDeployVerticle(iter, null, identifier, options, parentContext, callingContext, cl);
  }


  private Future<Deployment> doDeployVerticle(Iterator<VerticleFactory> iter,
                                              Throwable prevErr,
                                              String identifier,
                                              DeploymentOptions options,
                                              ContextInternal parentContext,
                                              ContextInternal callingContext,
                                              ClassLoader cl) {
    if (iter.hasNext()) {
      VerticleFactory verticleFactory = iter.next();
      return doDeployVerticle(verticleFactory, identifier, options, parentContext, callingContext, cl)
      .recover(err -> {
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

  private Future<Deployment> doDeployVerticle(VerticleFactory verticleFactory,
                                              String identifier,
                                              DeploymentOptions options,
                                              ContextInternal parentContext,
                                              ContextInternal callingContext,
                                              ClassLoader cl) {
    Promise<Callable<Verticle>> p = callingContext.promise();
    try {
      verticleFactory.createVerticle(identifier, cl, p);
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
    return p.future()
      .compose(callable -> deployVerticle(options, v -> identifier, parentContext, callingContext, cl, callable));
  }

  static ClassLoader getCurrentClassLoader() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = VerticleManager.class.getClassLoader();
    }
    return cl;
  }

  public Future<String> deployVerticle2(ContextInternal parentContext, Callable<Verticle> verticleSupplier, DeploymentOptions options) {
    if (options.getInstances() < 1) {
      throw new IllegalArgumentException("Can't specify < 1 instances to deploy");
    }
    options.checkIsolationNotDefined();
    ClassLoader cl = options.getClassLoader();
    if (cl == null) {
      cl = Thread.currentThread().getContextClassLoader();
      if (cl == null) {
        cl = getClass().getClassLoader();
      }
    }
    return deployVerticle(
      options,
      v -> "java:" + v.getClass().getName(),
      parentContext,
      parentContext,
      cl,
      verticleSupplier)
      .map(Deployment::deploymentID);
  }
  public Future<Deployment> deployVerticle(DeploymentOptions options,
                                           Function<Verticle, String> identifierProvider,
                                           ContextInternal parentContext,
                                           ContextInternal callingContext,
                                           ClassLoader tccl,
                                           Callable<Verticle> verticleSupplier) {


    Deployable verticleDeployable;
    try {
      verticleDeployable = VerticleDeployable.deployable(vertx, log, options, identifierProvider, tccl, verticleSupplier);
    } catch (Exception e) {
      return callingContext.failedFuture(e);
    }

    return deploymentManager.deploy(options, parentContext, callingContext, verticleDeployable);
  }
}
