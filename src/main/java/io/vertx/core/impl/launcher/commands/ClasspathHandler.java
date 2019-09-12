/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.launcher.commands;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.annotations.Description;
import io.vertx.core.cli.annotations.Option;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.launcher.DefaultCommand;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Command using the classpath option should extends this class as it manages the interaction with the
 * custom classloader.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public abstract class ClasspathHandler extends DefaultCommand {

  protected static final String PATH_SEP = System.getProperty("path.separator");

  protected final Logger log = LoggerFactory.getLogger(this.getClass());

  protected List<String> classpath;

  protected Object manager;
  private ClassLoader classloader;

  /**
   * Sets the classpath.
   *
   * @param classpath the classpath
   */
  @Option(shortName = "cp", longName = "classpath", argName = "classpath")
  @Description("Provides an extra classpath to be used for the verticle deployment.")
  public void setClasspath(String classpath) {
    if (classpath == null || classpath.isEmpty()) {
      this.classloader = ClasspathHandler.class.getClassLoader();
      this.classpath = Collections.emptyList();
    } else {
      this.classpath = Arrays.asList(classpath.split(PATH_SEP));
      this.classloader = createClassloader();
    }
  }

  /**
   * Creates a classloader respecting the classpath option.
   *
   * @return the classloader.
   */
  protected synchronized ClassLoader createClassloader() {
    URL[] urls = classpath.stream().map(path -> {
      File file = new File(path);
      try {
        return file.toURI().toURL();
      } catch (MalformedURLException e) {
        throw new IllegalStateException(e);
      }
    }).toArray(URL[]::new);
    return new URLClassLoader(urls, this.getClass().getClassLoader());
  }

  /**
   * Creates a new instance of {@link VertxIsolatedDeployer}.
   *
   * @return the new instance.
   */
  protected synchronized Object newInstance() {
    try {
      classloader = (classpath == null || classpath.isEmpty()) ?
          ClasspathHandler.class.getClassLoader() : createClassloader();
      Class<?> clazz = classloader.loadClass("io.vertx.core.impl.launcher.commands.VertxIsolatedDeployer");
      return clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      log.error("Failed to load or instantiate the isolated deployer", e);
      throw new IllegalStateException(e);
    }
  }

  /**
   * Creates a new non-clustered vert.x instance.
   *
   * @param options the options
   * @return the created instance
   */
  protected synchronized Vertx create(VertxOptions options) {
    final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classloader != null ? classloader : getClass().getClassLoader());
      return Vertx.vertx(options);
    } catch (Exception e) {
      log.error("Failed to create the vert.x instance", e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
    return null;
  }

  /**
   * Creates a new clustered vert.x instance.
   *
   * @param options       the options
   * @param resultHandler the result handler
   */
  protected synchronized void create(VertxOptions options, Handler<AsyncResult<Vertx>> resultHandler) {
    final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classloader != null ? classloader : getClass().getClassLoader());
      Vertx.clusteredVertx(options, resultHandler);
    } catch (Exception e) {
      log.error("Failed to create the vert.x instance", e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  /**
   * Deploys the given verticle using the given deployment options.
   *
   * @param verticle          the verticle
   * @param vertx             the vert.x instance
   * @param options           the deployment options
   * @param completionHandler the completion handler
   */
  public synchronized void deploy(String verticle, Vertx vertx, DeploymentOptions options,
                                  Handler<AsyncResult<String>> completionHandler) {
    if (manager == null) {
      manager = newInstance();
    }

    final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classloader);
      Method method = manager.getClass().getMethod("deploy", String.class, Vertx.class, DeploymentOptions.class,
          Handler.class);

      if (executionContext.get("Default-Verticle-Factory") != null) {
        // there is a configured default
        if (verticle.indexOf(':') == -1) {
          // and the verticle is not using a explicit factory
          verticle = executionContext.get("Default-Verticle-Factory") + ":" + verticle;
        }
      }

      method.invoke(manager, verticle, vertx, options, completionHandler);
    } catch (InvocationTargetException e) {
      log.error("Failed to deploy verticle " + verticle, e.getCause());
    } catch (Exception e) {
      log.error("Failed to deploy verticle " + verticle, e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }
}
