/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.logging;

/**
 * <strong>For internal logging purposes only</strong>.
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author Thomas Segismont
 */
public interface Logger {

  boolean isTraceEnabled();

  void trace(Object message);

  void trace(Object message, Throwable t);

  boolean isDebugEnabled();

  void debug(Object message);

  void debug(Object message, Throwable t);

  boolean isInfoEnabled();

  void info(Object message);

  void info(Object message, Throwable t);

  boolean isWarnEnabled();

  void warn(Object message);

  void warn(Object message, Throwable t);

  void error(Object message);

  void error(Object message, Throwable t);
}
