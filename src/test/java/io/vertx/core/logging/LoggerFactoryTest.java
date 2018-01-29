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

import io.vertx.core.spi.logging.LogDelegateFactory;
import org.junit.Test;

/**
 * Unit tests for {@link LoggerFactory}.
 *
 * @author <a href="https://twitter.com/bartekzdanowski">Bartek Zdanowski</a>
 * @author <a href="http://www.vorburger.ch">Michael Vorburger.ch</a>
 */
public class LoggerFactoryTest {

  @Test
  public void testProperlyLogFromAnonymousClass() {
    new Runnable() {

      @Override
      public void run() {
          LoggerFactory.getLogger(getClass()).info("I'm inside anonymous class");
      }

    }.run();
  }

  @Test
  public void testProgrammaticSetLoggerFactory() {
      LogDelegateFactory current = LoggerFactory.getLogDelegateFactory();
      // intentionally null; implementation must be robust enough
      LoggerFactory.setLogDelegateFactory(name -> null);
      LoggerFactory.setLogDelegateFactory(current);
  }
}
