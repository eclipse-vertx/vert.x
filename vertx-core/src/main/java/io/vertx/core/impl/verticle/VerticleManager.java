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

import io.vertx.core.*;
import io.vertx.core.impl.deployment.DefaultDeployment;
import io.vertx.core.internal.deployment.Deployment;
import io.vertx.core.impl.ServiceHelper;
import io.vertx.core.impl.*;
import io.vertx.core.internal.deployment.DeploymentContext;
import io.vertx.core.internal.deployment.DeploymentManager;
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

  /**
   * Deploy a verticle given a deployment {@code identifier} and its specific {@code options}.
   *
   * @param identifier the verticle identifier
   * @param options the deployment options
   * @return a furur result of the deployment context
   */
  public Future<DeploymentContext> deployVerticle(String identifier, DeploymentOptions options) {
    ContextInternal callingContext = vertx.getOrCreateContext();
    ClassLoader loader = options.getClassLoader();
    if (loader == null) {
      loader = getCurrentClassLoader();
    }
    return deployVerticle(identifier, options, callingContext, callingContext, loader);
  }

  private Future<DeploymentContext> deployVerticle(String identifier,
                                                   DeploymentOptions options,
                                                   ContextInternal parentContext,
                                                   ContextInternal callingContext,
                                                   ClassLoader cl) {
    List<VerticleFactory> verticleFactories = resolveFactories(identifier);
    Iterator<VerticleFactory> iter = verticleFactories.iterator();
    return createDeployment(iter, null, identifier, options, parentContext, callingContext, cl)
      .compose(deployment -> deploymentManager.deploy(parentContext.deployment(), callingContext, deployment));
  }

  private Future<Deployment> createDeployment(Iterator<VerticleFactory> iter,
                                              Throwable prevErr,
                                              String identifier,
                                              DeploymentOptions options,
                                              ContextInternal parentContext,
                                              ContextInternal callingContext,
                                              ClassLoader cl) {
    if (iter.hasNext()) {
      VerticleFactory verticleFactory = iter.next();
      return createDeployment(verticleFactory, identifier, options, callingContext, cl)
      .recover(err -> {
        // Try the next one
        return createDeployment(iter, err, identifier, options, parentContext, callingContext, cl);
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

  private Future<Deployment> createDeployment(VerticleFactory verticleFactory,
                                              String identifier,
                                              DeploymentOptions options,
                                              ContextInternal callingContext,
                                              ClassLoader cl) {
    Promise<Callable<? extends Deployable>> p = callingContext.promise();
    try {
      verticleFactory.createVerticle2(identifier, cl, p);
    } catch (Exception e) {
      return callingContext.failedFuture(e);
    }
    return p.future()
      .compose(callable -> createDeployment(options, v -> identifier, callingContext, cl, callable));
  }

  private Future<Deployment> createDeployment(DeploymentOptions options,
                                              Function<Deployable, String> identifierMapper,
                                              ContextInternal callingContext,
                                              ClassLoader tccl,
                                              Callable<? extends Deployable> verticleSupplier) {
    try {
      return Future.succeededFuture(DefaultDeployment.deployment(vertx, log, options, identifierMapper, tccl, verticleSupplier));
    } catch (Exception e) {
      return callingContext.failedFuture(e);
    }
  }

  private static ClassLoader getCurrentClassLoader() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = VerticleManager.class.getClassLoader();
    }
    return cl;
  }
}
