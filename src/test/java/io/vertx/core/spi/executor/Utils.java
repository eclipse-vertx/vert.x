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

package io.vertx.core.spi.executor;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.test.core.LogContainsRule;

public class Utils {

  private static final Logger log = LoggerFactory.getLogger(ExecutorServiceFactory.class);

  static final String SERVICES_SRC_DIR = "src/test/classpath/servicehelper/META-INF/services/";

  static final String TEST_CLASSPATH = "target/test-classes/";
  static final String SERVICE_LOADER_SUFFIX = "META-INF/services/";
  static final String SERVICE = "io.vertx.core.spi.executor.ExecutorServiceFactory";
  static final String SERVICE_CFG_FILE = TEST_CLASSPATH + SERVICE_LOADER_SUFFIX + SERVICE;

  static final String TESTS_PACKAGE = Utils.class.getPackage().getName(); // TODO replace with clazz.getPackage()


  /*
   * Set up some typesafe tokens for the testcases to refer to ExecutorService
   * methods
   */
  static Method EXECUTE = null;
  static Method SHUTDOWN_NOW = null;
  static {
    try {
      EXECUTE = ExecutorService.class.getMethod("execute", Runnable.class);
      SHUTDOWN_NOW = ExecutorService.class.getMethod("shutdownNow");
    } catch (NoSuchMethodException | SecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Checks that a particular {@link ExecutorService} implementation's method
   * makes an appearance in the test logs
   * 
   * @param executor the class name of the {@link ExecutorService}
   * @param method   the name of the expected method
   * @return a JUnit test rule that checks the Vert.x log files
   */
  static LogContainsRule executorMethodLogged(String executor, String method) {
    return new LogContainsRule(ExecutorServiceFactory.class, Level.ALL, Utils.TESTS_PACKAGE + "." + executor + "::" + method);
  }

  /**
   * A helper method that checks a set of {@link ExecutorService} methods are in
   * the log file
   * 
   * @param factory  the {@link ExecutorServiceFactory} expected
   * @param executor the {@link ExecutorService} expected
   * @param meth     a Varargs list of expected {@link ExecutorService} methods
   * @return
   */
  public static TestRule setupAndCheckSpiImpl(Class factory, String executor, Method... meth) {

    RuleChain chain = RuleChain.emptyRuleChain();

    for (Method method : meth) {
      LogContainsRule rule = executorMethodLogged(executor, method.getName());
      chain = chain.around(rule);
    }
    chain = chain.around(new SpiPopulator(factory));
    return chain;
  }

  /**
   * A JUnit {@ExternalResource} manager that sets up and tears down a particular
   * {@link ExecutorServiceFactory} provider
   */
  static class SpiPopulator extends ExternalResource {
    private final Class factory;

    /**
     * Create a Junit external resource manager for {@link ExecutorServiceFactory}
     * SPI providers
     * 
     * @param factory the {@link ExecutorServiceFactory} class
     * 
     */
    SpiPopulator(Class factory) {
      this.factory = factory;
    }

    @Override
    protected void before() throws Throwable {
      // Set up the Java ServiceLoader config file
      File source = new File(SERVICES_SRC_DIR + factory.getSimpleName());
      File out = new File(SERVICE_CFG_FILE);
      out.getParentFile().mkdirs();
      Files.deleteIfExists(out.toPath());
      Files.copy(source.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
      log.debug("Placed " + factory + " as service loadable for " + SERVICE);
    }

    @Override
    protected void after() {
      new File(SERVICE_CFG_FILE).delete();
    }
  }

}
