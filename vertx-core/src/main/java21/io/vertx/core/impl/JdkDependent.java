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

import java.util.concurrent.ThreadFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.util.List;

/**
 * Utils dependent on the JDK implementation.
 *
 * Java 21 and greater implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JdkDependent {

  public static final ThreadFactory VIRTUAL_THREAD_FACTORY;
  public static final boolean VIRTUAL_THREAD_AVAILABLE;

  static {
    VIRTUAL_THREAD_FACTORY = Thread.ofVirtual().name("vert.x-virtual-thread-", 0).factory();
    VIRTUAL_THREAD_AVAILABLE = true;
  }

  /**
   * @return whether the {@code thread} is virtual
   */
  public static boolean isVirtual(Thread thread) {
    return thread.isVirtual();
  }

  public static void applyNamedGroups(SSLEngine engine, List<String> groups) {
    SSLParameters params = engine.getSSLParameters();
    params.setNamedGroups(groups.toArray(new String[0]));
    engine.setSSLParameters(params);
  }
}
