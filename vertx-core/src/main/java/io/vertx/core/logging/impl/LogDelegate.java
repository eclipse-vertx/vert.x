/*
 * Copyright (c) 2009 Red Hat, Inc.
 * -------------------------------------
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

package io.vertx.core.logging.impl;

/**
 * I represent operations that are delegated to underlying logging frameworks.
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 */
public interface LogDelegate {
  boolean isInfoEnabled();

  boolean isDebugEnabled();

  boolean isTraceEnabled();

  void fatal(Object message);

  void fatal(Object message, Throwable t);

  void error(Object message);

  void error(Object message, Throwable t);

  void warn(Object message);

  void warn(Object message, Throwable t);

  void info(Object message);

  void info(Object message, Throwable t);

  void debug(Object message);

  void debug(Object message, Throwable t);

  void trace(Object message);

  void trace(Object message, Throwable t);
}