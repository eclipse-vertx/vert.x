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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventLoopContext extends ContextImpl {

  private static final Logger log = LoggerFactory.getLogger(EventLoopContext.class);

  public EventLoopContext(VertxInternal vertx, Executor bgExec, String deploymentID, JsonObject config) {
    super(vertx, bgExec, deploymentID, config);
  }

  public void doExecute(ContextTask task) {
    getEventLoop().execute(wrapTask(task, true));
  }

  @Override
  public boolean isEventLoopContext() {
    return true;
  }

  @Override
  public boolean isMultiThreaded() {
    return false;
  }

  @Override
  protected boolean isOnCorrectContextThread(boolean expectRightThread) {
    Thread current = Thread.currentThread();
    boolean correct = current == contextThread;
    if (expectRightThread) {
      if (!(current instanceof VertxThread)) {
        throw new IllegalStateException("Expected to be on Vert.x thread, but actually on: " + current);
      } else if (!correct && contextThread != null) {
        throw new IllegalStateException("Event delivered on unexpected thread " + current + " expected: " + contextThread);
      }
    }
    return correct;
  }

}
