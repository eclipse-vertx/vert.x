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

import org.junit.Test;

/**
 * @author <a href="https://twitter.com/bartekzdanowski">Bartek Zdanowski</a>
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
}
