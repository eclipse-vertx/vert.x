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

package org.vertx.java.core.impl;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class BaseContext implements Context {

  private static final Logger log = LoggerFactory.getLogger(BaseContext.class);

  private DeploymentContext deploymentContext;

  private VertxInternal vertx = VertxInternal.instance;

  public void setDeploymentContext(DeploymentContext deploymentContext) {
    this.deploymentContext = deploymentContext;
  }

  public DeploymentContext getDeploymentContext() {
    return deploymentContext;
  }

  public void reportException(Throwable t) {
    if (deploymentContext != null) {
      deploymentContext.reportException(t);
    } else {
      log.error("Unhandled exception", t);
    }
  }

  protected Runnable wrapTask(final Runnable task) {
    return new Runnable() {
      public void run() {
        try {
          vertx.setContext(BaseContext.this);
          task.run();
        } catch (Throwable t) {
          VertxInternal.instance.reportException(t);
        }
      }
    };
  }
}
