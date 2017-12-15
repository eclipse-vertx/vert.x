/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
 * A {@link LogDelegateFactory} which creates {@link Log4j2LogDelegate} instances.
 *
 * @author Clement Escoffier - clement@apache.org
 *
 *
 */
public class Log4j2LogDelegateFactory implements LogDelegateFactory
{
   public LogDelegate createDelegate(final String name)
   {
      return new Log4j2LogDelegate(name);
   }

}
