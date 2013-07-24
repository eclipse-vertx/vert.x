/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * Modified from original form by Tim Fox
 */

package org.vertx.java.core.logging.impl;

import org.vertx.java.core.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LoggerFactory {

  public static final String LOGGER_DELEGATE_FACTORY_CLASS_NAME = "org.vertx.logger-delegate-factory-class-name";

  private static volatile LogDelegateFactory delegateFactory;

  private static final ConcurrentMap<String, Logger> loggers = new ConcurrentHashMap<>();

  static {
    initialise();
  }

  public static synchronized void initialise() {
    LogDelegateFactory delegateFactory;

    // If a system property is specified then this overrides any delegate factory which is set
    // programmatically - this is primarily of use so we can configure the logger delegate on the client side.
    // call to System.getProperty is wrapped in a try block as it will fail if the client runs in a secured
    // environment
    String className = JULLogDelegateFactory.class.getName();
    try {
      className = System.getProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME);
    } catch (Exception e) {
    }

    if (className != null) {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      try {
        Class<?> clz = loader.loadClass(className);
        delegateFactory = (LogDelegateFactory) clz.newInstance();
      } catch (Exception e) {
        throw new IllegalArgumentException("Error instantiating transformer class \"" + className + "\"", e);
      }
    } else {
      delegateFactory = new JULLogDelegateFactory();
    }

    LoggerFactory.delegateFactory = delegateFactory;
  }

  public static Logger getLogger(final Class<?> clazz) {
    return getLogger(clazz.getCanonicalName());
  }

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

  public static void removeLogger(String name) {
    loggers.remove(name);
  }
}
