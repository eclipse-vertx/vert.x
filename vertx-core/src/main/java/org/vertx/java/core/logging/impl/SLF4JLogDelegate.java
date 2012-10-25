/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified from original form by Tim Fox
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
