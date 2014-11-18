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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

/**
 *
 * NOTE - this file is deliberately supposed to be broken!
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedeploySourceVerticle extends AbstractVerticle {

  @Override
  public void st art() throws Exception {
    vertx.eventBus().publish("vertstarted", context.deploymentID());
  }

  @Override
  public void st op() throws Exception {
    vertx.eventBus().publish("vertstopped", context.deploymentID());
  }
}