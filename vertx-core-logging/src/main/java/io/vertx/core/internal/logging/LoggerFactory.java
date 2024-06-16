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

package io.vertx.core.internal.logging;

import io.vertx.core.logging.JULLogDelegateFactory;
import io.vertx.core.spi.logging.LogDelegate;
import io.vertx.core.spi.logging.LogDelegateFactory;

/**
 * <strong>For internal logging purposes only</strong>.
 *
 * @author Thomas Segismont
 */
public class LoggerFactory {

  private static volatile LogDelegateFactory delegateFactory;

  static {
    initialise();
    // Do not log before being fully initialized (a logger extension may use Vert.x classes)
    LogDelegate log = delegateFactory.createDelegate(LoggerFactory.class.getName());
    log.debug("Using " + delegateFactory.getClass().getName());
  }

  private static synchronized void initialise() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    String className;
    try {
      className = System.getProperty("vertx.logger-delegate-factory-class-name");
    } catch (Exception ignore) {
      className = null;
    }
    if (className != null && configureWith(className, false, loader)) {
      return;
    }
    if (loader.getResource("vertx-default-jul-logging.properties") == null) {
      if (configureWith("SLF4J", true, loader)
        || configureWith("Log4j2", true, loader)) {
        return;
      }
    }
    // Do not use dynamic classloading here to ensure the class is visible by AOT compilers
    delegateFactory = new JULLogDelegateFactory();
  }

  private static boolean configureWith(String name, boolean shortName, ClassLoader loader) {
    try {
      Class<?> clazz = Class.forName(shortName ? "io.vertx.core.logging." + name + "LogDelegateFactory" : name, true, loader);
      LogDelegateFactory factory = (LogDelegateFactory) clazz.getDeclaredConstructor().newInstance();
      if (!factory.isAvailable()) {
        return false;
      }
      delegateFactory = factory;
      return true;
    } catch (Throwable ignore) {
      return false;
    }
  }

  /**
   * Like {@link #getLogger(String)}, using the provided {@code clazz} name.
   */
  public static Logger getLogger(Class<?> clazz) {
    String name = clazz.isAnonymousClass() ?
      clazz.getEnclosingClass().getCanonicalName() :
      clazz.getCanonicalName();
    return getLogger(name);
  }

  /**
   * Get the logger with the specified {@code name}.
   */
  public static Logger getLogger(String name) {
    return new LoggerAdapter(delegateFactory.createDelegate(name));
  }
}
