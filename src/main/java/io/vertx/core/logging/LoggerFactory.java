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

package io.vertx.core.logging;

import io.vertx.core.spi.logging.LogDelegate;
import io.vertx.core.spi.logging.LogDelegateFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @deprecated see https://github.com/eclipse-vertx/vert.x/issues/2774
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LoggerFactory {

  public static final String LOGGER_DELEGATE_FACTORY_CLASS_NAME = "vertx.logger-delegate-factory-class-name";

  private static volatile LogDelegateFactory delegateFactory;

  private static final ConcurrentMap<String, Logger> loggers = new ConcurrentHashMap<>();

  static {
    initialise();
  }

  public static synchronized void initialise() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    String className;
    try {
      className = System.getProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME);
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
    String loggerName = LoggerFactory.class.getName();
    try {
      Class<?> clazz = Class.forName(shortName ? "io.vertx.core.logging." + name + "LogDelegateFactory" : name, true, loader);
      LogDelegateFactory factory = (LogDelegateFactory) clazz.newInstance();
      if (!factory.isAvailable()) {
        return false;
      }
      factory.createDelegate(loggerName).debug("Using " + factory.getClass().getName());
      delegateFactory = factory;
      return true;
    } catch (Throwable ignore) {
      return false;
    }
  }

  /**
   * @deprecated see https://github.com/eclipse-vertx/vert.x/issues/2774
   */
  @Deprecated
  public static Logger getLogger(final Class<?> clazz) {
    String name = clazz.isAnonymousClass() ?
      clazz.getEnclosingClass().getCanonicalName() :
      clazz.getCanonicalName();
    return getLogger(name);
  }

  /**
   * @deprecated see https://github.com/eclipse-vertx/vert.x/issues/2774
   */
  @Deprecated
  public static Logger getLogger(final String name) {
    Logger logger = loggers.get(name);

    if (logger == null) {
      LogDelegate delegate = delegateFactory.createDelegate(name);

      logger = new Logger(delegate);

      Logger oldLogger = loggers.putIfAbsent(name, logger);

      if (oldLogger != null) {
        logger = oldLogger;
      }
    }

    return logger;
  }

  /**
   * @deprecated see https://github.com/eclipse-vertx/vert.x/issues/2774
   */
  @Deprecated
  public static void removeLogger(String name) {
    loggers.remove(name);
  }
}
