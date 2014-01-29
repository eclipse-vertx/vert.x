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

package org.vertx.java.core.logging.impl;

/**
 * A {@link LogDelegate} which delegates to Apache Log4j
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 */
public class Log4jLogDelegate implements LogDelegate {
  private final org.apache.log4j.Logger logger;

  Log4jLogDelegate(final String name) {
    logger = org.apache.log4j.Logger.getLogger(name);
  }

  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  public boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }

  public void fatal(final Object message) {
    logger.fatal(message);
  }

  public void fatal(final Object message, final Throwable t) {
    logger.fatal(message, t);
  }

  public void error(final Object message) {
    logger.error(message);
  }

  public void error(final Object message, final Throwable t) {
    logger.error(message, t);
  }

  public void warn(final Object message) {
    logger.warn(message);
  }

  public void warn(final Object message, final Throwable t) {
    logger.warn(message, t);
  }

  public void info(final Object message) {
    logger.info(message);
  }

  public void info(final Object message, final Throwable t) {
    logger.info(message, t);
  }

  public void debug(final Object message) {
    logger.debug(message);
  }

  public void debug(final Object message, final Throwable t) {
    logger.debug(message, t);
  }

  public void trace(final Object message) {
    logger.trace(message);
  }

  public void trace(final Object message, final Throwable t) {
    logger.trace(message, t);
  }

}
