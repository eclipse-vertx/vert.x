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
