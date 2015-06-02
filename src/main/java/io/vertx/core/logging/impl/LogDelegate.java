/*
 * Copyright (c) 2009 Red Hat, Inc. ------------------------------------- All rights reserved. This program and the
 * accompanying materials are made available under the terms of the Eclipse Public License v1.0 and Apache License v2.0
 * which accompanies this distribution. The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php You may elect to redistribute this code under either of these
 * licenses.
 */

package io.vertx.core.logging.impl;

/**
 * I represent operations that are delegated to underlying logging frameworks.
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 * @author Patrick Sauts
 */
public interface LogDelegate {
  boolean isInfoEnabled();

  boolean isDebugEnabled();

  boolean isTraceEnabled();

  void fatal(Object message);

  void fatal(String format, Object arg);

  void fatal(String format, Object arg1, Object arg2);

  void fatal(String format, Object... arguments);

  void error(Object message);

  void error(String format, Object arg);

  void error(String format, Object arg1, Object arg2);

  void error(String format, Object... arguments);

  void warn(Object message);

  void warn(String format, Object arg);

  void warn(String format, Object arg1, Object arg2);

  void warn(String format, Object... arguments);

  void info(Object message);

  void info(String format, Object arg);

  void info(String format, Object arg1, Object arg2);

  void info(String format, Object... arguments);

  void debug(Object message);

  void debug(String format, Object arg);

  void debug(String format, Object arg1, Object arg2);

  void debug(String format, Object... arguments);

  void trace(Object message);

  void trace(String format, Object arg);

  void trace(String format, Object arg1, Object arg2);

  void trace(String format, Object... arguments);

}