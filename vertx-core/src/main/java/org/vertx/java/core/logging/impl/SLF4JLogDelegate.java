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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SLF4JLogDelegate implements LogDelegate{

  private final Logger logger;

  SLF4JLogDelegate(final String name) {
    logger = LoggerFactory.getLogger(name);
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
    logger.error(message.toString());
  }

  public void fatal(final Object message, final Throwable t) {
    logger.error(message.toString(), t);
  }

  public void error(final Object message) {
    logger.error(message.toString());
  }

  public void error(final Object message, final Throwable t) {
    logger.error(message.toString(), t);
  }

  public void warn(final Object message) {
    logger.warn(message.toString());
  }

  public void warn(final Object message, final Throwable t) {
    logger.warn(message.toString(), t);
  }

  public void info(final Object message) {
    logger.info(message.toString());
  }

  public void info(final Object message, final Throwable t) {
    logger.info(message.toString(), t);
  }

  public void debug(final Object message) {
    logger.debug(message.toString());
  }

  public void debug(final Object message, final Throwable t) {
    logger.debug(message.toString(), t);
  }

  public void trace(final Object message) {
    logger.trace(message.toString());
  }

  public void trace(final Object message, final Throwable t) {
    logger.trace(message.toString(), t);
  }
}
