/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.deploy.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.DeploymentHandle;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.Container;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.impl.groovy.GroovyVerticleFactory;
import org.vertx.java.deploy.impl.java.JavaVerticleFactory;
import org.vertx.java.deploy.impl.jruby.JRubyVerticleFactory;
import org.vertx.java.deploy.impl.rhino.RhinoVerticleFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VerticleManager {

  private static final Logger log = LoggerFactory.getLogger(VerticleManager.class);

  private final VertxInternal vertx;
  // deployment name --> deployment
  private final Map<String, Deployment> deployments = new HashMap();

  private CountDownLatch stopLatch = new CountDownLatch(1);

  public VerticleManager(VertxInternal vertx) {
    this.vertx = vertx;
  }

  public void block() {
    while (true) {
      try {
        stopLatch.await();
        break;
      } catch (InterruptedException e) {
        //Ignore
      }
    }
  }

  public void unblock() {
    stopLatch.countDown();
  }

  public JsonObject getConfig() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.config;
  }

  public String getDeploymentName() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.name;
  }

  public URL[] getDeploymentURLs() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.deployment.urls;
  }

  public Logger getLogger() {
    VerticleHolder holder = getVerticleHolder();
    return holder == null ? null : holder.logger;
  }

  public synchronized String deploy(boolean worker, String name, final String main,
                                    final JsonObject config, final URL[] urls,
                                    int instances,
                                    final Handler<Void> doneHandler)
  {
    if (deployments.containsKey(name)) {
      throw new IllegalStateException("There is already a deployment with name: " + name);
    }

    if (urls == null) {
      throw new IllegalStateException("urls cannot be null");
    }

    //Infer the main type

    VerticleType type = VerticleType.JAVA;
    if (main.endsWith(".js")) {
      type = VerticleType.JS;
    } else if (main.endsWith(".rb")) {
      type = VerticleType.RUBY;
    } else if (main.endsWith(".groovy")) {
      type = VerticleType.GROOVY;
    }

    final String deploymentName = name == null ?  "deployment-" + UUID.randomUUID().toString() : name;

    log.debug("Deploying name : " + deploymentName  + " main: " + main +
                 " instances: " + instances);

    final VerticleFactory verticleFactory;
      switch (type) {
        case JAVA:
          verticleFactory = new JavaVerticleFactory(this);
          break;
        case RUBY:
          verticleFactory = new JRubyVerticleFactory(this);
          break;
        case JS:
          verticleFactory = new RhinoVerticleFactory(this);
          break;
        case GROOVY:
          verticleFactory = new GroovyVerticleFactory(this);
          break;
        default:
          throw new IllegalArgumentException("Unsupported type: " + type);
      }

    final int instCount = instances;

    class AggHandler {
      AtomicInteger count = new AtomicInteger(0);

      // We need a context on which to execute the done Handler
      // We use the current calling context (if any) or assign a new one
      Context doneContext = vertx.getOrAssignContext();

      void started() {
        if (count.incrementAndGet() == instCount) {
          if (doneHandler != null) {
            doneContext.execute(new Runnable() {
              public void run() {
                doneHandler.handle(null);
              }
            });
          }
        }
      }
    }

    final AggHandler aggHandler = new AggHandler();

    String parentDeploymentName = getDeploymentName();
    final Deployment deployment = new Deployment(deploymentName, verticleFactory, config == null ? new JsonObject() : config.copy(), urls, parentDeploymentName);
    deployments.put(deploymentName, deployment);
    if (parentDeploymentName != null) {
      Deployment parent = deployments.get(parentDeploymentName);
      parent.childDeployments.add(deploymentName);
    }

    for (int i = 0; i < instances; i++) {

      // Launch the verticle instance

      Runnable runner = new Runnable() {
        public void run() {

          Verticle verticle;
          try {
            verticle = verticleFactory.createVerticle(main, new ParentLastURLClassLoader(urls, getClass()
                .getClassLoader()));
          } catch (Throwable t) {
            log.error("Failed to create verticle", t);
            doUndeploy(deploymentName, doneHandler);
            return;
          }

          //Inject vertx
          verticle.setVertx(vertx);
          verticle.setContainer(new Container(VerticleManager.this));

          try {
            addVerticle(deployment, verticle);
            verticle.start();
          } catch (Throwable t) {
            vertx.reportException(t);
            doUndeploy(deploymentName, doneHandler);
          }
          aggHandler.started();
        }
      };

      if (worker) {
        vertx.startInBackground(runner);
      } else {
        vertx.startOnEventLoop(runner);
      }

    }

    return deploymentName;
  }

  public synchronized void undeployAll(final Handler<Void> doneHandler) {
    final UndeployCount count = new UndeployCount();
    if (!deployments.isEmpty()) {
      // We do it this way since undeploy is itself recursive - we don't want
      // to attempt to undeploy the same verticle twice if it's a child of
      // another
      while (!deployments.isEmpty()) {
        String name = deployments.keySet().iterator().next();
        count.incRequired();
        undeploy(name, new SimpleHandler() {
          public void handle() {
            count.undeployed();
          }
        });
      }
    }
    count.setHandler(doneHandler);
  }

  public synchronized void undeploy(String name, final Handler<Void> doneHandler) {
    if (deployments.get(name) == null) {
      throw new IllegalArgumentException("There is no deployment with name " + name);
    }
    doUndeploy(name, doneHandler);
  }

  public synchronized Map<String, Integer> listInstances() {
    Map<String, Integer> map = new HashMap<>();
    for (Map.Entry<String, Deployment> entry: deployments.entrySet()) {
      map.put(entry.getKey(), entry.getValue().verticles.size());
    }
    return map;
  }

  // Must be synchronized since called directly from different thread
  private synchronized void addVerticle(Deployment deployment, Verticle verticle) {
    String loggerName = deployment.name + "-" + deployment.verticles.size();
    Logger logger = LoggerFactory.getLogger(loggerName);
    Context context = Context.getContext();
    VerticleHolder holder = new VerticleHolder(deployment, context, verticle,
                                               loggerName, logger, deployment.config);
    deployment.verticles.add(holder);
    context.setDeploymentHandle(holder);
  }

  private VerticleHolder getVerticleHolder() {
    Context context = Context.getContext();
    if (context != null) {
      VerticleHolder holder = (VerticleHolder)context.getDeploymentHandle();
      return holder;
    } else {
      return null;
    }
  }


  private void doUndeploy(String name, final Handler<Void> doneHandler) {
    UndeployCount count = new UndeployCount();
    doUndeploy(name, count);
    if (doneHandler != null) {
      count.setHandler(doneHandler);
    }
  }

  private void doUndeploy(String name, final UndeployCount count) {

    final Deployment deployment = deployments.remove(name);

    // Depth first - undeploy children first
    for (String childDeployment: deployment.childDeployments) {
      doUndeploy(childDeployment, count);
    }

    if (!deployment.verticles.isEmpty()) {

      for (final VerticleHolder holder: deployment.verticles) {
        count.incRequired();
        holder.context.execute(new Runnable() {
          public void run() {
            try {
              holder.verticle.stop();
            } catch (Throwable t) {
              vertx.reportException(t);
            }
            count.undeployed();
            LoggerFactory.removeLogger(holder.loggerName);
            holder.context.runCloseHooks();
          }
        });
      }
    }

    if (deployment.parentDeploymentName != null) {
      Deployment parent = deployments.get(deployment.parentDeploymentName);
      if (parent != null) {
        parent.childDeployments.remove(name);
      }
    }
  }

  private static class VerticleHolder implements DeploymentHandle {
    final Deployment deployment;
    final Context context;
    final Verticle verticle;
    final String loggerName;
    final Logger logger;
    //We put the config here too so it's still accessible to the verticle after it has been deployed
    //(deploy is async)
    final JsonObject config;

    private VerticleHolder(Deployment deployment, Context context, Verticle verticle, String loggerName,
                           Logger logger, JsonObject config) {
      this.deployment = deployment;
      this.context = context;
      this.verticle = verticle;
      this.loggerName = loggerName;
      this.logger = logger;
      this.config = config;
    }

    public void reportException(Throwable t) {
      deployment.factory.reportException(t);
    }
  }

  private static class Deployment {
    final String name;
    final VerticleFactory factory;
    final JsonObject config;
    final URL[] urls;
    final List<VerticleHolder> verticles = new ArrayList<>();
    final List<String> childDeployments = new ArrayList<>();
    final String parentDeploymentName;

    private Deployment(String name, VerticleFactory factory, JsonObject config, URL[] urls, String parentDeploymentName) {
      this.name = name;
      this.factory = factory;
      this.config = config;
      this.urls = urls;
      this.parentDeploymentName = parentDeploymentName;
    }
  }

  private class UndeployCount {
    int count;
    int required;
    Handler<Void> doneHandler;
    Context context = vertx.getOrAssignContext();

    synchronized void undeployed() {
      count++;
      checkDone();
    }

    synchronized void incRequired() {
      required++;
    }

    synchronized void setHandler(Handler<Void> doneHandler) {
      this.doneHandler = doneHandler;
      checkDone();
    }

    void checkDone() {
      if (doneHandler != null && count == required) {
        context.execute(new Runnable() {
          public void run() {
            doneHandler.handle(null);
          }
        });
      }
    }
  }
}
