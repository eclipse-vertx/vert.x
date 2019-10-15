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
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BlockedThreadChecker {

  private final ThreadMXBean mbean = ManagementFactory.getThreadMXBean();

  /**
   * A checked task.
   */
  public interface Task {
    long startTime();
    long maxExecTime();
    TimeUnit maxExecTimeUnit();
  }

  private static final Logger log = LoggerFactory.getLogger(BlockedThreadChecker.class);

  private final Map<Thread, Task> threads = new WeakHashMap<>();
  private final Timer timer; // Need to use our own timer - can't use event loop for this

  BlockedThreadChecker(long interval, TimeUnit intervalUnit, long warningExceptionTime, TimeUnit warningExceptionTimeUnit) {
    timer = new Timer("vertx-blocked-thread-checker", true);
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        synchronized (BlockedThreadChecker.this) {
          long now = System.nanoTime();
          for (Map.Entry<Thread, Task> entry : threads.entrySet()) {
            long execStart = entry.getValue().startTime();
            long dur = now - execStart;
            final long timeLimit = entry.getValue().maxExecTime();
            TimeUnit maxExecTimeUnit = entry.getValue().maxExecTimeUnit();
            long val = maxExecTimeUnit.convert(dur, TimeUnit.NANOSECONDS);
            if (execStart != 0 && val >= timeLimit) {
              final String message = "Thread " + entry.getKey() + " has been blocked for " + (dur / 1_000_000) + " ms, time limit is " + TimeUnit.MILLISECONDS.convert(timeLimit, maxExecTimeUnit) + " ms";
              if (warningExceptionTimeUnit.convert(dur, TimeUnit.NANOSECONDS) <= warningExceptionTime) {
                log.warn(message);
              } else {

                // Check for deadlocks
                long[] deadlockedThreadIds = mbean.findDeadlockedThreads();
                if (deadlockedThreadIds != null) {
                  ThreadInfo[] threadInfos = mbean.getThreadInfo(deadlockedThreadIds);
                  handleDeadlock(threadInfos);
                }

                VertxException stackTrace = new VertxException("Thread blocked");
                stackTrace.setStackTrace(entry.getKey().getStackTrace());
                log.warn(message, stackTrace);
              }
            }
          }
        }
      }
    }, intervalUnit.toMillis(interval), intervalUnit.toMillis(interval));
  }

  private void handleDeadlock(final ThreadInfo[] deadlockedThreads) {
    System.err.println("Deadlock detected!");
    Map<Thread, StackTraceElement[]> stackTraceMap = Thread.getAllStackTraces();
    for (ThreadInfo threadInfo : deadlockedThreads) {
      if (threadInfo != null) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
          if (thread.getId() == threadInfo.getThreadId()) {
            System.err.println(threadInfo.toString().trim());
            for (StackTraceElement ste : thread.getStackTrace()) {
              System.err.println("\t" + ste.toString().trim());
            }
          }
        }
      }
    }
  }


  synchronized void registerThread(Thread thread, Task checked) {
    threads.put(thread, checked);
  }

  public void close() {
    timer.cancel();
  }
}
