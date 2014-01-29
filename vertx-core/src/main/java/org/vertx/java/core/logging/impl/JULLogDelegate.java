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

import java.util.logging.Level;

/**
 * A {@link LogDelegate} which delegates to java.util.logging
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 */
public class JULLogDelegate implements LogDelegate {
  private final java.util.logging.Logger logger;

  JULLogDelegate(final String name) {
    logger = java.util.logging.Logger.getLogger(name);
  }

  public boolean isInfoEnabled() {
    return logger.isLoggable(Level.INFO);
  }

  public boolean isDebugEnabled() {
    return logger.isLoggable(Level.FINE);
  }

  public boolean isTraceEnabled() {
    return logger.isLoggable(Level.FINEST);
  }

  public void fatal(final Object message) {
    logger.log(Level.SEVERE, message == null ? "NULL" : message.toString());
  }

  public void fatal(final Object message, final Throwable t) {
    logger.log(Level.SEVERE, message == null ? "NULL" : message.toString(), t);
  }

  public void error(final Object message) {
    logger.log(Level.SEVERE, message == null ? "NULL" : message.toString());
  }

  public void error(final Object message, final Throwable t) {
    logger.log(Level.SEVERE, message == null ? "NULL" : message.toString(), t);

    logger.log(Level.SEVERE, message == null ? "NULL" : message.toString(), t);
  }

  public void warn(final Object message) {
    logger.log(Level.WARNING, message == null ? "NULL" : message.toString());
  }

  public void warn(final Object message, final Throwable t) {
    logger.log(Level.WARNING, message == null ? "NULL" : message.toString(), t);
  }

  public void info(final Object message) {
    logger.log(Level.INFO, message == null ? "NULL" : message.toString());
  }

  public void info(final Object message, final Throwable t) {
    logger.log(Level.INFO, message == null ? "NULL" : message.toString(), t);
  }

  public void debug(final Object message) {
    logger.log(Level.FINE, message == null ? "NULL" : message.toString());
  }

  public void debug(final Object message, final Throwable t) {
    logger.log(Level.FINE, message == null ? "NULL" : message.toString(), t);
  }

  public void trace(final Object message) {
    logger.log(Level.FINEST, message == null ? "NULL" : message.toString());
  }

  public void trace(final Object message, final Throwable t) {
    logger.log(Level.FINEST, message == null ? "NULL" : message.toString(), t);
  }

}
