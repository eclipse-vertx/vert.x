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

package org.vertx.java.core.impl;

import org.vertx.java.core.*;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeploymentManager {

  private static final Logger log = LoggerFactory.getLogger(DeploymentManager.class);

  private final VertxInternal vertx;
  private final Set<VerticleDeployment> deployments = new ConcurrentHashSet<>();

  public DeploymentManager(VertxInternal vertx) {
    this.vertx = vertx;
  }

  public void deployVerticle(Verticle verticle, JsonObject config, boolean worker,
                             Handler<AsyncResult<VerticleDeployment>> doneHandler) {
    Context currentContext = vertx.getOrCreateContext();
    doDeploy(verticle, config, worker, currentContext, doneHandler);
  }

  private <T> void reportFailure(Throwable t, Context context, Handler<AsyncResult<T>> doneHandler) {
    if (doneHandler != null) {
      reportResult(context, doneHandler, new DefaultFutureResult<>(t));
    } else {
      log.error(t.getMessage(), t);
    }
  }

  private <T> void reportSuccess(T result, Context context, Handler<AsyncResult<T>> doneHandler) {
    if (doneHandler != null) {
      reportResult(context, doneHandler, new DefaultFutureResult<>(result));
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

  public void deployVerticle(String verticleClass,
                             JsonObject config,
                             boolean worker,
                             Handler<AsyncResult<VerticleDeployment>> doneHandler) {
    Context currentContext = vertx.getOrCreateContext();
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = getClass().getClassLoader();
    }
    Class clazz;
    Verticle verticle;
    try {
      clazz = cl.loadClass(verticleClass);
      try {
        verticle = (Verticle)clazz.newInstance();
        doDeploy(verticle, config, worker, currentContext, doneHandler);
      } catch (Exception e) {
        reportFailure(e, currentContext, doneHandler);
      }
    } catch (ClassNotFoundException e) {
      reportFailure(e, currentContext, doneHandler);
    }
  }

  public Set<VerticleDeployment> deployments() {
    return Collections.unmodifiableSet(deployments);
  }

  private void doDeploy(Verticle verticle, JsonObject config, boolean worker,
                        Context currentContext,
                        Handler<AsyncResult<VerticleDeployment>> doneHandler) {
    Context context = worker ? vertx.createWorkerContext(false) : vertx.createEventLoopContext();
    VerticleDeployment deployment = new Deployment(config, context, verticle);

    context.runOnContext(v -> {
      try {
        verticle.start(deployment);
        deployments.add(deployment);
        reportSuccess(deployment, currentContext, doneHandler);
      } catch (Throwable t) {
        reportFailure(t, currentContext, doneHandler);
      }
    });
  }

  private class Deployment implements VerticleDeployment {

    final JsonObject config;
    final Context context;
    final Verticle verticle;
    boolean undeployed;

    private Deployment(JsonObject config, Context context, Verticle verticle) {
      this.config = config;
      this.context = context;
      this.verticle = verticle;
    }

    @Override
    public String getID() {
      return null;
    }

    @Override
    public void undeploy(Handler<AsyncResult<Void>> doneHandler) {
      Context currentContext = vertx.getOrCreateContext();
      if (!undeployed) {
        undeployed = true;
        deployments.remove(Deployment.this);
        context.runOnContext(v -> {
          try {
            verticle.stop();
            reportSuccess(null, currentContext, doneHandler);
          } catch (Throwable t) {
            reportFailure(t, currentContext, doneHandler);
          }
        });
      } else {
        reportFailure(new IllegalStateException("Already undeployed"), currentContext, doneHandler);
      }
    }

    @Override
    public JsonObject config() {
      return config;
    }

    // TODO - do we really need this?
    @Override
    public Map<String, String> env() {
      return System.getenv();
    }

    @Override
    public void setAsyncStart() {

    }

    @Override
    public void setAsyncStop() {

    }

    @Override
    public void startComplete() {

    }

    @Override
    public void stopComplete() {

    }

    @Override
    public void setFailure(Throwable t) {

    }

    @Override
    public void setFailure(String message) {

    }
  }
}
