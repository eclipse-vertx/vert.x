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
 */

package org.nodex.java.core.logging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>This class allows us to isolate all our logging dependencies in one place. It also allows us to have zero runtime
 * 3rd party logging jar dependencies, since we default to JUL by default.</p>
 *
 * <p>By default logging will occur using JUL (Java-Util-Logging). The logging configuration file (logging.properties)
 * used by JUL will taken from the default logging.properties in the JDK installation if no {@code  java.util.logging.config.file} system
 * property is set. The {@code nodex-java / nodex-ruby / etc} scripts set {@code  java.util.logging.config.file} to point at the logging.properties
 * in the nodex distro install directory. This in turn configures nodex to log to a file in a directory called
 * nodex-logs in the users home directory.</p>
 *
 * <p>If you would prefer to use Log4J or SLF4J instead of JUL then you can set a system property called
 * {@code org.nodex.logger-delegate-factory-class-name} to the class name of the delegate for your logging system.
 * For Log4J the value is {@code org.nodex.java.core.logging.Log4JLogDelegateFactory}, for SLF4J the value
 * is {@code org.nodex.java.core.logging.SLF4JLogDelegateFactory}. You will need to ensure whatever jar files
 * required by your favourite log framework are on your classpath.</p>
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 */
public class Logger {
  public static final String LOGGER_DELEGATE_FACTORY_CLASS_NAME = "org.nodex.logger-delegate-factory-class-name";

  private static volatile LogDelegateFactory delegateFactory;

  private static final ConcurrentMap<Class<?>, Logger> loggers = new ConcurrentHashMap<>();

  static {
    Logger.initialise();
  }

  public static synchronized void initialise() {
    LogDelegateFactory delegateFactory;

    // If a system property is specified then this overrides any delegate factory which is set
    // programmatically - this is primarily of use so we can configure the logger delegate on the client side.
    // call to System.getProperty is wrapped in a try block as it will fail if the client runs in a secured
    // environment
    String className = JULLogDelegateFactory.class.getName();
    try {
      className = System.getProperty(Logger.LOGGER_DELEGATE_FACTORY_CLASS_NAME);
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

    Logger.delegateFactory = delegateFactory;
  }

  public static Logger getLogger(final Class<?> clazz) {
    Logger logger = Logger.loggers.get(clazz);

    if (logger == null) {
      LogDelegate delegate = Logger.delegateFactory.createDelegate(clazz);

      logger = new Logger(delegate);

      Logger oldLogger = Logger.loggers.putIfAbsent(clazz, logger);

      if (oldLogger != null) {
        logger = oldLogger;
      }
    }

    return logger;
  }

  private final LogDelegate delegate;

  Logger(final LogDelegate delegate) {
    this.delegate = delegate;
  }

  public LogDelegate getDelegate() {
    return delegate;
  }

  public boolean isInfoEnabled() {
    return delegate.isInfoEnabled();
  }

  public boolean isDebugEnabled() {
    return delegate.isDebugEnabled();
  }

  public boolean isTraceEnabled() {
    return delegate.isTraceEnabled();
  }

  public void fatal(final Object message) {
    delegate.fatal(message);
  }

  public void fatal(final Object message, final Throwable t) {
    delegate.fatal(message, t);
  }

  public void error(final Object message) {
    delegate.error(message);
  }

  public void error(final Object message, final Throwable t) {
    delegate.error(message, t);
  }

  public void warn(final Object message) {
    delegate.warn(message);
  }

  public void warn(final Object message, final Throwable t) {
    delegate.warn(message, t);
  }

  public void info(final Object message) {
    delegate.info(message);
  }

  public void info(final Object message, final Throwable t) {
    delegate.info(message, t);
  }

  public void debug(final Object message) {
    delegate.debug(message);
  }

  public void debug(final Object message, final Throwable t) {
    delegate.debug(message, t);
  }

  public void trace(final Object message) {
    delegate.trace(message);
  }

  public void trace(final Object message, final Throwable t) {
    delegate.trace(message, t);
  }

}
