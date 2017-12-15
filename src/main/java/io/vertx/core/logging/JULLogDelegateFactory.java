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

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

/**
 * A {@link io.vertx.core.spi.logging.LogDelegateFactory} which creates {@link JULLogDelegate} instances.
 *
 * @author <a href="kenny.macleod@kizoom.com">Kenny MacLeod</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JULLogDelegateFactory implements LogDelegateFactory {

  public static void loadConfig() {
    try (InputStream is = JULLogDelegateFactory.class.getClassLoader().getResourceAsStream("vertx-default-jul-logging.properties")) {
      if (is != null) {
        LogManager.getLogManager().readConfiguration(is);
      }
    } catch (IOException ignore) {
    }
  }

  static {
    // Try and load vert.x JUL default logging config from classpath
    if (System.getProperty("java.util.logging.config.file") == null) {
      loadConfig();
    }
  }

  public LogDelegate createDelegate(final String name) {
    return new JULLogDelegate(name);
  }
}
