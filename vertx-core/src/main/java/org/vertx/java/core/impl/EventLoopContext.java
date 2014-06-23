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

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventLoopContext extends ContextImpl {

  private static final Logger log = LoggerFactory.getLogger(EventLoopContext.class);

  private VertxThread contextThread;

  public EventLoopContext(VertxInternal vertx, Executor bgExec) {
    super(vertx, bgExec);
  }

  public void doExecute(ContextTask task) {
    getEventLoop().execute(wrapTask(task, true));
  }

  @Override
  public boolean isEventLoopContext() {
    return true;
  }

  @Override
  protected void executeStart(Thread thread) {
    // Sanity check - make sure Netty is really delivering events on the correct thread
    if (this.contextThread == null) {
      if (thread instanceof VertxThread) {
        this.contextThread = (VertxThread)thread;
      } else {
        throw new IllegalStateException("Not a vert.x thread!");
      }
    } else if (this.contextThread != thread) {
      //log.warn("Uh oh! Event loop context executing with wrong thread! Expected " + this.thread + " got " + thread);
      throw new IllegalStateException("Uh oh! Event loop context executing with wrong thread! Expected " + this.contextThread + " got " + thread);
    }
    contextThread.executeStart();
  }

  @Override
  protected void executeEnd() {
    contextThread.executeEnd();
  }

  @Override
  protected boolean isOnCorrectContextThread(boolean expectRightThread) {
    Thread current = Thread.currentThread();
    boolean correct = current == contextThread;
    if (expectRightThread) {
      if (!(current instanceof VertxThread)) {
        log.warn("Expected to be on Vert.x thread, but actually on: " + current);
      } else if (!correct && contextThread != null) {
        log.warn("Event delivered on unexpected thread " + current + " expected: " + contextThread);
        new Exception().printStackTrace();
      }
    }
    return correct;
  }

}
