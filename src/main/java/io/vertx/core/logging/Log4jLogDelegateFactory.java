/*
 * Copyright (c) 2009 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.logging;

import io.vertx.core.spi.logging.LogDelegate;
import io.vertx.core.spi.logging.LogDelegateFactory;

/**
 * A {@link io.vertx.core.spi.logging.LogDelegateFactory} which creates {@link Log4jLogDelegate} instances.
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 *
 *
 */
public class Log4jLogDelegateFactory implements LogDelegateFactory
{
   public LogDelegate createDelegate(final String name)
   {
      return new Log4jLogDelegate(name);
   }

}
