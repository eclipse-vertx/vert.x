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