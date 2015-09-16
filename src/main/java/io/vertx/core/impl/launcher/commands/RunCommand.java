/*
 *  Copyright (c) 2011-2015 The original author or authors
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
package io.vertx.core.impl.launcher.commands;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.annotations.*;
import io.vertx.core.impl.launcher.VertxLifecycleHooks;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.launcher.ExecutionContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * The vert.x run command that lets you execute verticles.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
@Name("run")
@Summary("Runs a verticle called <main-verticle> in its own instance of vert.x.")
public class RunCommand extends BareCommand {

  protected DeploymentOptions deploymentOptions;

  private boolean cluster;
  private boolean ha;

  private int instances;
  private String config;
  private boolean worker;

  private String mainVerticle;

  /**
   * Enables / disables the high-availability.
   *
   * @param ha whether or not to enable the HA.
   */
  @Option(longName = "ha", acceptValue = false, flag = true)
  @Description("If specified the verticle will be deployed as a high availability (HA) deployment. This means it can " +
      "fail over to any other nodes in the cluster started with the same HA group.")
  public void setHighAvailability(boolean ha) {
    this.ha = ha;
  }

  /**
   * Enables / disables the clustering.
   *
   * @param cluster whether or not to start vert.x in clustered mode.
   */
  @Option(longName = "cluster", acceptValue = false, flag = true)
  @Description("If specified then the vert.x instance will form a cluster with any other vert.x instances on the " +
      "network.")
  public void setCluster(boolean cluster) {
    this.cluster = cluster;
  }

  /**
   * Whether or not the verticle is deployed as a worker verticle.
   *
   * @param worker {@code true} to deploy the verticle as worker, {@code false} otherwise
   */
  @Option(longName = "worker", acceptValue = false)
  @Description("If specified then the verticle is a worker verticle.")
  public void setWorker(boolean worker) {
    this.worker = worker;
  }

  /**
   * Sets the number of instance of the verticle to create.
   *
   * @param instances the number of instances
   */
  @Option(longName = "instances", argName = "instances")
  @DefaultValue("1")
  @Description("Specifies how many instances of the verticle will be deployed. Defaults to 1.")
  public void setInstances(int instances) {
    this.instances = instances;
  }

  /**
   * The main verticle configuration, it can be a json file or a json string.
   *
   * @param configuration the configuration
   */
  @Option(longName = "conf", argName = "config")
  @Description("Specifies configuration that should be provided to the verticle. <config> should reference either a " +
      "text file containing a valid JSON object which represents the configuration OR be a JSON string.")
  public void setConfig(String configuration) {
    this.config = configuration;
  }

  @Argument(index = 0, argName = "main-verticle", required = true)
  public void setMainVerticle(String verticle) {
    this.mainVerticle = verticle;
  }

  /**
   * Validates the command line parameters.
   *
   * @param context - the execution context
   * @throws CLIException - validation failed
   */
  @Override
  public void setUp(ExecutionContext context) throws CLIException {
    super.setUp(context);

    // If cluster-host and / or port is set, cluster need to have been explicitly set
    io.vertx.core.cli.Option clusterHostOption = executionContext.cli().getOption("cluster-host");
    io.vertx.core.cli.Option clusterPortOption = executionContext.cli().getOption("cluster-port");
    CommandLine commandLine = executionContext.commandLine();
    if ((!isClustered()) &&
        (commandLine.isOptionAssigned(clusterHostOption)
            || commandLine.isOptionAssigned(clusterPortOption))) {
      throw new CLIException("The option -cluster-host and -cluster-port requires -cluster to be enabled");
    }

    // If quorum and / or ha-group, ha need to have been explicitly set
    io.vertx.core.cli.Option haGroupOption = executionContext.cli().getOption("hagroup");
    io.vertx.core.cli.Option quorumOption = executionContext.cli().getOption("quorum");
    if (!ha &&
        (commandLine.isOptionAssigned(haGroupOption) || commandLine.isOptionAssigned(quorumOption))) {
      throw new CLIException("The option -hagroup and -quorum requires -ha to be enabled");
    }
  }

  /**
   * @return whether the {@code cluster} option or the {@code ha} option are enabled.
   */
  @Override
  public boolean isClustered() {
    return cluster || ha;
  }

  @Override
  public boolean getHA() {
    return ha;
  }

  /**
   * Starts vert.x and deploy the verticle.
   */
  @Override
  public void run() {
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
    deploy(mainVerticle, vertx, deploymentOptions, res -> {
      if (res.failed()) {
        res.cause().printStackTrace();
        handleDeployFailed(res.cause());
      }
    });
  }

  private void handleDeployFailed(Throwable cause) {
    if (executionContext.main() instanceof VertxLifecycleHooks) {
      ((VertxLifecycleHooks) executionContext.main()).handleDeployFailed(vertx, mainVerticle, deploymentOptions, cause);
    }
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
