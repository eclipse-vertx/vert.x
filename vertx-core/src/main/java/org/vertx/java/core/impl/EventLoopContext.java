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

package org.vertx.java.core.impl;

import io.netty.channel.EventLoop;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventLoopContext extends DefaultContext {

  private static final Logger log = LoggerFactory.getLogger(EventLoopContext.class);

  public EventLoopContext(VertxInternal vertx, Executor bgExec) {
    super(vertx, bgExec);
  }

  public void execute(Runnable task) {
    getEventLoop().execute(wrapTask(task));
  }

  public boolean isOnCorrectWorker(EventLoop worker) {
    return getEventLoop() == worker;
  }

}
