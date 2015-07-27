/*
 *  Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.impl.cli.commands;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cli.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * The vert.x run command that let you execute verticles or start a bare instance.
 */
@Summary("Runs a verticle called <main-verticle> in its own instance of vert.x.")
public class RunCommand extends BareCommand {

  private static final String PATH_SEP = System.getProperty("path.separator");

  protected DeploymentOptions deploymentOptions;

  private List<String> classpath;

  private boolean cluster;
  private boolean ha;

  private int instances;
  private String config;
  private boolean worker;

  private String mainVerticle;


  @Option(shortName = "cp", longName = "classpath", name = "classpath")
  @DefaultValue(".")
  @Description("Provides an extra classpath to be used for the verticle deployment.")
  public void setClasspath(String classpath) {
    this.classpath = Arrays.asList(classpath.split(PATH_SEP));
  }

  @Option(longName = "ha", acceptValue = false)
  @Description("If specified the verticle will be deployed as a high availability (HA) deployment. This means it can " +
      "fail over to any other nodes in the cluster started with the same HA group.")
  public void setHighAvailability(boolean ha) {
    this.ha = ha;
  }

  @Option(longName = "cluster", acceptValue = false)
  @Description("If specified then the vert.x instance will form a cluster with any other vert.x instances on the " +
      "network.")
  public void setCluster(boolean cluster) {
    this.cluster = cluster;
  }

  @Option(longName = "worker", acceptValue = false)
  @Description("If specified then the verticle is a worker verticle.")
  public void setWorker(boolean worker) {
    this.worker = worker;
  }

  @Option(longName = "instances", name = "instances")
  @DefaultValue("1")
  @Description("Specifies how many instances of the verticle will be deployed. Defaults to 1.")
  public void setInstances(int instances) {
    this.instances = instances;
  }

  @Option(longName = "conf", name = "config")
  @Description("Specifies configuration that should be provided to the verticle. <config> should reference either a " +
      "text file containing a valid JSON object which represents the configuration OR be a JSON string.")
  public void setConfig(String configuration) {
    this.config = configuration;
  }

  @Argument(index = 0, name = "main-verticle", required = true)
  public void setMainVerticle(String verticle) {
    this.mainVerticle = verticle;
  }

  /**
   * Validates the command line parameters.
   *
   * @throws CommandLineException - validation failed
   */
  @Override
  public void setup() throws CommandLineException {
    super.setup();

    // If cluster-host and / or port is set, cluster need to have been explicitly set
    if ((!isClustered()) &&
        (executionContext.getCommandLine().hasBeenSet("cluster-host") || executionContext.getCommandLine().hasBeenSet("cluster-port"))) {
      throw new CommandLineException("The option -cluster-host and -cluster-port requires -cluster to be enabled");
    }

    // If quorum and / or ha-group, ha need to have been explicitly set
    if (!ha &&
        (executionContext.getCommandLine().hasBeenSet("hagroup") || executionContext.getCommandLine().hasBeenSet("quorum"))) {
      throw new CommandLineException("The option -hagroup and -quorum requires -ha to be enabled");
    }
  }

  @Override
  public boolean isClustered() {
    return cluster || ha;
  }

  @Override
  public boolean getHA() {
    return ha;
  }

  @Override
  public String name() {
    return "run";
  }

  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    super.run();
    if (vertx == null) {
      return; // Already logged.
    }

    JsonObject conf = getConfiguration();
    deploymentOptions = new DeploymentOptions();
    configureFromSystemProperties(deploymentOptions, DEPLOYMENT_OPTIONS_PROP_PREFIX);
    deploymentOptions.setConfig(conf).setWorker(worker).setHa(ha).setInstances(instances);
    beforeDeployingVerticle(deploymentOptions);

    deploy();
  }

  protected void deploy() {

    final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      ClassLoader classloader = createClassloader();
      Thread.currentThread().setContextClassLoader(classloader);
      Class clazz = classloader.loadClass("io.vertx.core.impl.cli.commands.VertxIsolatedDeployer");
      Object instance = clazz.newInstance();
      Method method = clazz.getMethod("deploy", String.class, Vertx.class, DeploymentOptions.class, Handler.class);
      method.invoke(instance, mainVerticle, vertx, deploymentOptions, (Handler<AsyncResult<String>>) res -> {
        if (res.failed()) {
          handleDeployFailed(res.cause());
        }
      });
    } catch (Exception e) {
      log.error("Failed to create the isolated deployer", e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  private void handleDeployFailed(Throwable cause) {
    if (executionContext.main() instanceof VertxLifecycleHooks) {
      ((VertxLifecycleHooks) executionContext.main()).handleDeployFailed(vertx, mainVerticle, deploymentOptions, cause);
    }
  }

  protected ClassLoader createClassloader() {
    URL[] urls = classpath.stream().map(path -> {
      File file = new File(getCwd(), path);
      try {
        return file.toURI().toURL();
      } catch (MalformedURLException e) {
        throw new IllegalStateException(e);
      }
    }).toArray(URL[]::new);

    return new URLClassLoader(urls, this.getClass().getClassLoader());
  }


  protected JsonObject getConfiguration() {
    JsonObject conf;
    if (config != null) {
      try (Scanner scanner = new Scanner(new File(config)).useDelimiter("\\A")) {
        String sconf = scanner.next();
        try {
          conf = new JsonObject(sconf);
        } catch (DecodeException e) {
          log.error("Configuration file " + sconf + " does not contain a valid JSON object");
          return null;
        }
      } catch (FileNotFoundException e) {
        try {
          conf = new JsonObject(config);
        } catch (DecodeException e2) {
          log.error("-conf option does not point to a file and is not valid JSON: " + config);
          return null;
        }
      }
    } else {
      conf = null;
    }
    return conf;
  }


  protected void beforeDeployingVerticle(DeploymentOptions deploymentOptions) {
    final Object main = executionContext.main();
    if (main instanceof VertxLifecycleHooks) {
      ((VertxLifecycleHooks) main).beforeDeployingVerticle(deploymentOptions);
    }
  }
}
