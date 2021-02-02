/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

/**
 * This source file is not in the usual Vert.x IDE project's classpath but instead
 * compiled (and the .class file place on the classpath) by some tests. This is
 * to more closely emulate typical use of the {@link ExecutorServiceFactory} SPI
 * interface and to allow for testing with different SPI implementations on the
 * classpath.
 */
package io.vertx.core.spi.executor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This is a base {@link ExecutorServiceFactory} used in the junit tests for the
 * SPI that delegates to a simple {@link ThreadPoolExecutor}. The delegate is
 * wrapped in a proxy that logs all the method calls the it receives. Many of
 * the tests will then inspect the log to ensure that the expected methods were
 * called on the correct {@link ExecutorService} class.
 */
public class ExternalLoadableExecutorServiceFactory implements ExecutorServiceFactory {

  /*
   * All of the test ExecutorServiceFactory implementations use the
   * ExecutorServiceFactory SPI Interface for the log records so that tests can
   * access messages without having to refer to a specific implementation class.
   */
  static final Logger log = LoggerFactory.getLogger(ExecutorServiceFactory.class);

  /**
   * Using this over-ridable method allows subclasses to easily replace the core
   * {@link ExecutorService} with their own but inherit the logging
   * 
   * @param threadFactory  used to create new threads
   * @param maxConcurrency the size of the thread pool desired
   * @return an ExecutorService that can be used to run tasks
   */
  ExecutorService getBaseExecutorService(ThreadFactory threadFactory, Integer maxConcurrency) {
    // We create a anonymous inner subclass as this makes it easy to trace the
    // source of the object in tests
    return new ThreadPoolExecutor(maxConcurrency, maxConcurrency, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
        threadFactory) {
    };
  }

  /*
   * We return a logging wrapper around the getBaseExecutorService delegate. For
   * javadoc, see interface
   */
  @Override
  public ExecutorService createExecutor(ThreadFactory threadFactory, Integer concurrency, Integer maxConcurrency) {
    // Using this.getClass allows us to distinguish subclasses in the log messages
    log.debug(this.getClass() + "::createExecutor(" + threadFactory + ", " + concurrency + ", " + maxConcurrency + ")");
    return LoggingProxy.proxy(getBaseExecutorService(threadFactory, maxConcurrency));
  }

  /**
   * toString is specified as it is a component of log messages that are searched
   * for in tests
   */
  @Override
  public String toString() {
    return this.getClass().getName() + ":" + this.hashCode();
  }

  /**
   * A simple dynamic proxy class that logs invoked methods and passes through
   * checked exceptions that are used by Vert.x
   */
  public static class LoggingProxy implements InvocationHandler {
    private final Object delegate;

    /**
     * @param coreExecutorService delegate for all {@link ExecutorService} methods
     */
    public LoggingProxy(ExecutorService coreExecutorService) {
      this.delegate = coreExecutorService;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

      /* Edits to the line below may need matching changes in junit tests */
      log.debug(delegate.getClass() + "::" + method.getName() + "(" + args + ")");
      try {
        return method.invoke(delegate, args);
      } catch (InvocationTargetException ex) {
        // We can be here for any checked exception not in the ExecutorService
        // interface.
        // For example, java.util.concurrent.RejectedExecutionException
        throw ex.getCause();
      }
    }

    /**
     * Helper method to create a proxy to the delegate that logs all method calls to
     * the {@link ExecutorServiceFactory} log
     * 
     * @param delegate the {@ExecutorService} used to run all tasks.
     * @return
     */
    public static ExecutorService proxy(ExecutorService delegate) {
      return (ExecutorService) Proxy.newProxyInstance(ExecutorService.class.getClassLoader(), new Class[] { ExecutorService.class },
          new LoggingProxy(delegate));
    }
  }
}
