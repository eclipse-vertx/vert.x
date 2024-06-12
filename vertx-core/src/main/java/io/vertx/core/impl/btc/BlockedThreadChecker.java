/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.btc;

import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BlockedThreadChecker {

  public static final String LOGGER_NAME = "io.vertx.core.impl.BlockedThreadChecker";
  private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

  private final Map<Thread, ThreadInfo> threads = new WeakHashMap<>();
  private final Timer timer; // Need to use our own timer - can't use event loop for this

  private Handler<BlockedThreadEvent> blockedThreadHandler;

  public BlockedThreadChecker(long interval, TimeUnit intervalUnit, long warningExceptionTime, TimeUnit warningExceptionTimeUnit) {
    timer = new Timer("vertx-blocked-thread-checker", true);
    blockedThreadHandler = BlockedThreadChecker::defaultBlockedThreadHandler;
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        List<BlockedThreadEvent> events = new ArrayList<>();
        Handler<BlockedThreadEvent> handler;
        synchronized (BlockedThreadChecker.this) {
          handler = blockedThreadHandler;
          long now = System.nanoTime();
          for (Map.Entry<Thread, ThreadInfo> entry : threads.entrySet()) {
            ThreadInfo task = entry.getValue();
            long execStart = task.startTime;
            long dur = now - execStart;
            final long timeLimit = task.maxExecTime;
            TimeUnit maxExecTimeUnit = task.maxExecTimeUnit;
            long maxExecTimeInNanos = TimeUnit.NANOSECONDS.convert(timeLimit, maxExecTimeUnit);
            long warningExceptionTimeInNanos = TimeUnit.NANOSECONDS.convert(warningExceptionTime, warningExceptionTimeUnit);
            if (execStart != 0 && dur >= maxExecTimeInNanos) {
              events.add(new BlockedThreadEvent(entry.getKey(), dur, maxExecTimeInNanos, warningExceptionTimeInNanos));
            }
          }
        }
        events.forEach(handler::handle);
      }
    }, intervalUnit.toMillis(interval), intervalUnit.toMillis(interval));
  }

  /**
   * Specify the handler to run when it is determined a thread has been blocked for longer than allowed.
   * Note that the handler will be called on the blocked thread checker thread, not an event loop thread.
   *
   * @param handler The handler to run
   */
  public synchronized void setThreadBlockedHandler(Handler<BlockedThreadEvent> handler) {
    this.blockedThreadHandler = handler == null ? BlockedThreadChecker::defaultBlockedThreadHandler : handler;
  }

  public synchronized void registerThread(Thread thread, ThreadInfo checked) {
    threads.put(thread, checked);
  }

  public void close() {
    timer.cancel();
    synchronized (this) {
      //Not strictly necessary, but it helps GC to break it all down
      //when Vert.x is embedded and restarted multiple times
      threads.clear();
    }
  }

  private static void defaultBlockedThreadHandler(BlockedThreadEvent bte) {
    final String message = "Thread " + bte.thread().getName() + " has been blocked for " + (bte.duration() / 1_000_000) + " ms, time limit is " + (bte.maxExecTime() / 1_000_000) + " ms";
    if (bte.duration() <= bte.warningExceptionTime()) {
      log.warn(message);
    } else {
      VertxException stackTrace = new VertxException("Thread blocked");
      stackTrace.setStackTrace(bte.thread().getStackTrace());
      log.warn(message, stackTrace);
    }
  }
}
