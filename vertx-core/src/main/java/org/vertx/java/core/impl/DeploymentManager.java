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

  private Set<VerticleDeployment> deployments = new ConcurrentHashSet<>();

  public void deployVerticle(Verticle verticle, JsonObject config,
                             Handler<AsyncResult<VerticleDeployment>> doneHandler) {
    doDeploy(new Verticle[] {verticle}, config, doneHandler);
  }

  private <T> void reportFailure(Throwable t, Handler<AsyncResult<T>> doneHandler) {
    if (doneHandler != null) {
      doneHandler.handle(new DefaultFutureResult<>(t));
    } else {
      log.error(t.getMessage(), t);
    }
  }

  public void deployVerticle(String verticleClass,
                             int instances,
                             Handler<AsyncResult<VerticleDeployment>> doneHandler) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = getClass().getClassLoader();
    }
    Class clazz;
    Verticle[] verticles = new Verticle[instances];
    try {
      clazz = cl.loadClass(verticleClass);
      for (int i = 0; i < instances; i++) {
        try {
          verticles[i] = (Verticle)clazz.newInstance();
        } catch (Exception e) {
          reportFailure(e, doneHandler);
        }
      }
    } catch (ClassNotFoundException e) {
      reportFailure(e, doneHandler);
    }
  }

  public Set<VerticleDeployment> deployments() {
    return Collections.unmodifiableSet(deployments);
  }

  private void doDeploy(Verticle[] verticles, JsonObject config, Handler<AsyncResult<VerticleDeployment>> doneHandler) {
    boolean failed = false;
    Deployment deployment = new Deployment(verticles.length, config, verticles);
    for (Verticle verticle: verticles) {
      try {
        //TODO set context
        verticle.start(deployment);
      } catch (Throwable t) {
        failed = true;
        reportFailure(t, doneHandler);
        break;
      }
    }
    deployments.add(deployment);
    if (!failed) {
      doneHandler.handle(new DefaultFutureResult<VerticleDeployment>(deployment));
    }
  }

  private class Deployment implements VerticleDeployment {

    final int instances;
    final JsonObject config;
    final Verticle[] verticles;

    private Deployment(int instances, JsonObject config, Verticle[] verticles) {
      this.instances = instances;
      this.config = config;
      this.verticles = verticles;
    }

    @Override
    public String getID() {
      return null;
    }

    @Override
    public void undeploy(Handler<AsyncResult<Void>> doneHandler) {
      boolean failed = false;
      for (Verticle verticle: verticles) {
        try {
          verticle.stop();
        } catch (Throwable t) {
          failed = true;
          reportFailure(t, doneHandler);
        }
      }
      deployments.remove(this);
      if (!failed) {
        doneHandler.handle(new DefaultFutureResult<>((Void)null));
      }
    }

    @Override
    public int instances() {
      return instances;
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
