/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.netty;

import io.netty.util.internal.logging.AbstractInternalLogger;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.MessageFormatter;
import io.vertx.tests.net.quic.QuicTestClient;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TestLoggerFactory extends InternalLoggerFactory {

  private ConcurrentMap<String, String> names = new ConcurrentHashMap<>();
  private Deque<Map.Entry<String, LogRecord>> logs = new ConcurrentLinkedDeque<>();

  public boolean hasName(String name) {
    return names.containsKey(name);
  }

  public Stream<LogRecord> logs(Class<?> cls) {
    return logs(cls.getName());
  }

  public Stream<LogRecord> logs(String name) {
    return logs
      .stream()
      .filter(entry -> entry.getKey().equals(name))
      .map(Map.Entry::getValue);
  }

  @Override
  protected InternalLogger newInstance(String name) {
    names.put(name, name);
    return new AbstractInternalLogger(name) {

      public boolean isTraceEnabled() { return true; }
      public boolean isDebugEnabled() { return true; }
      public boolean isInfoEnabled() { return true; }
      public boolean isWarnEnabled() { return true; }
      public boolean isErrorEnabled() { return true; }

      private void log(Level level, String format, Object... args) {
        String msg = MessageFormatter.arrayFormat(format, args).getMessage();
        LogRecord record = new LogRecord(level, msg);
        record.setParameters(args);
        logs.add(new AbstractMap.SimpleEntry<>(name, record));
      }

      public void trace(String msg) {
        log(Level.FINER, msg);
      }
      public void trace(String format, Object arg) {
        log(Level.FINER, format, arg);
      }
      public void trace(String format, Object argA, Object argB) {
        log(Level.FINER, format, argA, argB);
      }
      public void trace(String format, Object... arguments) {
        log(Level.FINER, format, arguments);
      }
      public void trace(String msg, Throwable t) {
        log(Level.FINER, msg);
      }
      public void debug(String msg) {
        log(Level.FINE, msg);
      }
      public void debug(String format, Object arg) {
        log(Level.FINE, format, arg);
      }
      public void debug(String format, Object argA, Object argB) {
        log(Level.FINE, format, argA, argB);
      }
      public void debug(String format, Object... arguments) {
        log(Level.FINE, format, arguments);
      }
      public void debug(String msg, Throwable t) {
        log(Level.FINE, msg);
      }
      public void info(String msg) {
        log(Level.INFO, msg);
      }
      public void info(String format, Object arg) {
        log(Level.INFO, format, arg);
      }
      public void info(String format, Object argA, Object argB) {
        log(Level.INFO, format, argA, argB);
      }
      public void info(String format, Object... arguments) {
        log(Level.INFO, format, arguments);
      }
      public void info(String msg, Throwable t) {
        log(Level.INFO, msg);
      }
      public void warn(String msg) {
        log(Level.WARNING, msg);
      }
      public void warn(String format, Object arg) {
        log(Level.WARNING, format, arg);
      }
      public void warn(String format, Object... arguments) {
        log(Level.WARNING, format, arguments);
      }
      public void warn(String format, Object argA, Object argB) {
        log(Level.WARNING, format, argA, argB);
      }
      public void warn(String msg, Throwable t) {
        log(Level.WARNING, msg);
      }
      public void error(String msg) {
        log(Level.SEVERE, msg);
      }
      public void error(String format, Object arg) {
        log(Level.SEVERE, format, arg);
      }
      public void error(String format, Object argA, Object argB) {
        log(Level.SEVERE, format, argA, argB);
      }
      public void error(String format, Object... arguments) {
        log(Level.SEVERE, format, arguments);
      }
      public void error(String msg, Throwable t) {
        log(Level.SEVERE, msg);
      }
    };
  }
}
