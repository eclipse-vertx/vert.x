/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
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
