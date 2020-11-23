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

package io.vertx.it;

import io.vertx.core.logging.JULLogDelegate;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.core.spi.logging.LogDelegate;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Check the behavior when SLF4J is present on the classpath but without an implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@SuppressWarnings("deprecation")
public class SLF4JNoImplTest {

  private static Object getClass(String name) {
    try {
      return Thread.currentThread().getContextClassLoader().loadClass(name);
    } catch (Throwable e) {
      return null;
    }
  }

  @Test
  public void testImplementation() {
    assertNotNull(SLF4JLogDelegateFactory.class);
    assertNull(getClass("org.apache.logging.log4j.message.Message"));
    Logger logger = LoggerFactory.getLogger("my-slf4j-logger");
    LogDelegate delegate = logger.getDelegate();
    assertTrue(delegate instanceof JULLogDelegate);
  }
}
