/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.netty;

import io.netty.util.internal.logging.AbstractInternalLogger;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TestLoggerFactory extends InternalLoggerFactory {

  private ConcurrentMap<String, String> names = new ConcurrentHashMap<>();

  public boolean hasName(String name) {
    return names.containsKey(name);
  }

  @Override
  protected InternalLogger newInstance(String name) {
    names.put(name, name);
    return new AbstractInternalLogger(name) {
      public boolean isTraceEnabled() { return true; }
      public void trace(String msg) {}
      public void trace(String format, Object arg) {}
      public void trace(String format, Object argA, Object argB) {}
      public void trace(String format, Object... arguments) {}
      public void trace(String msg, Throwable t) {}
      public boolean isDebugEnabled() { return false; }
      public void debug(String msg) {}
      public void debug(String format, Object arg) {}
      public void debug(String format, Object argA, Object argB) {}
      public void debug(String format, Object... arguments) {}
      public void debug(String msg, Throwable t) {}
      public boolean isInfoEnabled() { return false; }
      public void info(String msg) {}
      public void info(String format, Object arg) {}
      public void info(String format, Object argA, Object argB) {}
      public void info(String format, Object... arguments) {}
      public void info(String msg, Throwable t) {}
      public boolean isWarnEnabled() { return false; }
      public void warn(String msg) {}
      public void warn(String format, Object arg) {}
      public void warn(String format, Object... arguments) {}
      public void warn(String format, Object argA, Object argB) {}
      public void warn(String msg, Throwable t) {}
      public boolean isErrorEnabled() { return true; }
      public void error(String msg) {}
      public void error(String format, Object arg) {}
      public void error(String format, Object argA, Object argB) {}
      public void error(String format, Object... arguments) {}
      public void error(String msg, Throwable t) {}
    };
  }
}
