package org.vertx.java.core.logging;

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
