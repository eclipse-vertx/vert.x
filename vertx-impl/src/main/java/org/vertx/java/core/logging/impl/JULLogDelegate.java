/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * Modified from original form by Tim Fox
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
