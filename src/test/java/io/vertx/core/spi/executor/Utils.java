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
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import io.vertx.core.impl.ExecutorServiceResolver;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.test.core.LogContainsRule;

public class Utils {

  private static final Logger log = LoggerFactory.getLogger(ExecutorServiceFactory.class);

  static final String EXTERNALS = "src/test/externals/";
  static final String SERVICES = "META-INF/services/";

  static final String PACKAGE = "io.vertx.core.externals.";
  static final String CLASSPATH_LOCATION = "target/test-classes";
  static final String PROVIDER_CONFIG_DIR = "target/test-classes/META-INF/services/";

  static final String ES_FACTORY = "io.vertx.core.spi.executor.ExecutorServiceFactory";
  static final String BASE_FACTORY = "ExternalLoadableExecutorServiceFactory";
  static final String BASE_TEST_EXECUTOR = "ExternalLoadableExecutorServiceFactory";

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
   * This will take the indicated .java file and compile it to the classpath along
   * with the required standard test superclasses and set up Java ServiceLoader
   * configuration to load it into Vert.x
   * 
   * @param impl the {@link ExecutorServiceFactory} desired
   * @throws IOException
   */
  static void putExecutorServiceFactoryOnClasspath(String impl) throws IOException {
    Utils.compileToClassPath(Utils.BASE_FACTORY);
    Utils.compileToClassPath(Utils.BASE_TEST_EXECUTOR);
    compileToClassPath(impl);
    configureServiceLoaded(ES_FACTORY, impl);
  }

  /**
   * A utility function to set the Java {@link ServiceLoader}
   * provider-configuration file for a SPI test.
   * 
   * @param service the name of the service being supplied
   * @param impl    the name of the implementation
   * @throws IOException
   */
  static void configureServiceLoaded(String service, String impl) throws IOException {
    File source = new File(EXTERNALS + SERVICES + impl);
    File out = new File(PROVIDER_CONFIG_DIR + service);
    out.getParentFile().mkdirs();
    Files.deleteIfExists(out.toPath());
    Files.copy(source.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    log.debug("Placed " + impl + " as service loadable for " + service);
  }

  /**
   * Compiles a particular .java file, in the conventional test source directory
   * src/test/externals to the classpath of a test. If we have SPI implementation
   * files and {@link ServiceLoader} provider-configuration files in the normal
   * IDE classpath they all interfere with each other's tests.
   * 
   * @param impl the non package qualified class name
   * @throws IOException
   */
  static void compileToClassPath(String impl) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
    File output = new File(CLASSPATH_LOCATION);
    output.mkdirs();
    fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(output));

    List<File> classesToCompile = new ArrayList<>();
    classesToCompile.add(new File(EXTERNALS + impl + ".java"));

    Iterable<? extends JavaFileObject> compilationUnits1 = fileManager.getJavaFileObjectsFromFiles(classesToCompile);
    compiler.getTask(null, fileManager, null, null, null, compilationUnits1).call();
  }

  /**
   * Checks that a particular {@link ExecutorServiceFactory} makes an appearance
   * in the test logs
   * 
   * @param factory
   * @return a JUnit test rule that checks the Vert.x log files
   */
  static LogContainsRule checkFactoryLoaded(String factory) {
    return new LogContainsRule(ExecutorServiceResolver.class, factory);
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
    return new LogContainsRule(ExecutorServiceFactory.class, Utils.PACKAGE + executor + "::" + method);
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
  public static TestRule setupAndCheckSpiImpl(String factory, String executor, Method... meth) {

    RuleChain chain = RuleChain.outerRule(checkFactoryLoaded(factory));

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
    private final String factory;

    /**
     * Create a Junit external resource manager for {@link ExecutorServiceFactory}
     * SPI providers
     * 
     * @param factory the {@link ExecutorServiceFactory} name expected, the .java
     *                file should exist in the conventional directory
     *                (src/test/externals)
     */
    SpiPopulator(String factory) {
      this.factory = factory;
    }

    @Override
    protected void before() throws Throwable {
      putExecutorServiceFactoryOnClasspath(factory);
    }

    @Override
    protected void after() {
      new File(PROVIDER_CONFIG_DIR).delete();
    }
  }

}
