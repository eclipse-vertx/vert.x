/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.util.List;
import java.util.concurrent.ThreadFactory;

/**
 * Utils dependent on the JDK implementation.
 *
 * Pre Java 21 implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JdkDependent {

  private static final Logger log = LoggerFactory.getLogger(JdkDependent.class);

  public static final ThreadFactory VIRTUAL_THREAD_FACTORY;
  public static final boolean VIRTUAL_THREAD_AVAILABLE;

  static {
    VIRTUAL_THREAD_FACTORY = null;
    VIRTUAL_THREAD_AVAILABLE = false;
  }

  /**
   * @return whether the {@code thread} is virtual
   */
  public static boolean isVirtual(Thread thread) {
    return false;
  }

  public static boolean isPqcAvailable() {
    return false;
  }

  public static void applyNamedGroups(SSLEngine engine, List<String> groups) {
    if (log.isDebugEnabled()) {
      log.warn("Cannot apply key exchange groups " + groups + " on JDK SSL engine: requires JDK 20+");
    }
  }
}
