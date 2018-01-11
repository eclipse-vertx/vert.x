/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.vertx.core.VertxException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BlockedThreadChecker {

  private static final Logger log = LoggerFactory.getLogger(BlockedThreadChecker.class);

  private static final Object O = new Object();
  private final Map<VertxThread, Object> threads = new WeakHashMap<>();
  private final Timer timer; // Need to use our own timer - can't use event loop for this

  BlockedThreadChecker(long interval, long warningExceptionTime) {
    timer = new Timer("vertx-blocked-thread-checker", true);
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        synchronized (BlockedThreadChecker.this) {
          long now = System.nanoTime();
          for (VertxThread thread : threads.keySet()) {
            long execStart = thread.startTime();
            long dur = now - execStart;
            final long timeLimit = thread.getMaxExecTime();
            if (execStart != 0 && dur > timeLimit) {
              final String message = "Thread " + thread + " has been blocked for " + (dur / 1000000) + " ms, time limit is " + (timeLimit / 1000000);
              if (dur <= warningExceptionTime) {
                log.warn(message);
              } else {
                VertxException stackTrace = new VertxException("Thread blocked");
                stackTrace.setStackTrace(thread.getStackTrace());
                log.warn(message, stackTrace);
              }
            }
          }
        }
      }
    }, interval, interval);
  }

  public synchronized void registerThread(VertxThread thread) {
    threads.put(thread, O);
  }

  public void close() {
    timer.cancel();
  }
}
