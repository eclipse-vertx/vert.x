/*
 * Copyright (c) 2011-2013 The original author or authors
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

import io.vertx.core.json.JsonObject;

import java.util.concurrent.Executor;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WorkerContext extends ContextImpl {

  protected final Executor workerExec;

  public WorkerContext(VertxInternal vertx, Executor orderedInternalPoolExec, Executor workerExec, String deploymentID, JsonObject config) {
    super(vertx, orderedInternalPoolExec, deploymentID, config);
    this.workerExec = workerExec;
  }

  @Override
  public void doExecute(ContextTask task) {
    workerExec.execute(wrapTask(task, true));
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }

  @Override
  public boolean isMultiThreaded() {
    return false;
  }

  @Override
  protected boolean isOnCorrectContextThread(boolean expectRightThread) {
    return false;
  }

}
