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
import io.vertx.core.Handler;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.annotations.*;
import io.vertx.core.impl.launcher.VertxLifecycleHooks;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.launcher.ExecutionContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The vert.x run command that lets you execute verticles.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
@Name("run")
@Summary("Runs a verticle called <main-verticle> in its own instance of vert.x.")
public class RunCommand extends BareCommand {

  protected DeploymentOptions deploymentOptions;

  protected boolean cluster;
  protected boolean ha;

  protected int instances;
  protected String config;
  protected boolean worker;

  protected String mainVerticle;
  protected List<String> redeploy;


  protected String vertxApplicationBackgroundId;
  protected String onRedeployCommand;
  protected Watcher watcher;
  private long redeployScanPeriod;
  private long redeployGracePeriod;

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

  /**
   * Sets the main verticle that is deployed.
   *
   * @param verticle the verticle
   */
  @Argument(index = 0, argName = "main-verticle", required = true)
  @Description("The main verticle to deploy, it can be a fully qualified class name or a file.")
  public void setMainVerticle(String verticle) {
    this.mainVerticle = verticle;
  }

  @Option(longName = "redeploy", argName = "includes")
  @Description("Enable automatic redeployment of the application. This option takes a set on includes as parameter " +
      "indicating which files need to be watched. Patterns are separated by a comma.")
  @ParsedAsList
  public void setRedeploy(List<String> redeploy) {
    this.redeploy = redeploy;
  }

  @Option(longName = "onRedeploy", argName = "cmd")
  @Description("Optional shell command executed when a redeployment is triggered")
  public void setOnRedeployCommand(String command) {
    this.onRedeployCommand = command;
  }

  @Option(longName = "redeploy-scan-period", argName = "period")
  @Description("When redeploy is enabled, this option configures the file system scanning period to detect file " +
      "changes. The time is given in milliseconds. 250 ms by default.")
  @DefaultValue("250")
  public void setRedeployScanPeriod(long period) {
    this.redeployScanPeriod = period;
  }

  @Option(longName = "redeploy-grace-period", argName = "period")
  @Description("When redeploy is enabled, this option configures the grace period between 2 redeployments. The time " +
      "is given in milliseconds. 1000 ms by default.")
  @DefaultValue("1000")
  public void setRedeployGracePeriod(long period) {
    this.redeployGracePeriod = period;
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
    if (redeploy == null || redeploy.isEmpty()) {
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
    } else {
      // redeploy is set, start the redeployment infrastructure (watcher).
      initializeRedeployment();
    }
  }

  /**
   * Initializes the redeployment cycle. In "redeploy mode", the application is launched as background, and is
   * restarted after every change. A {@link Watcher} instance is responsible for monitoring files and triggering the
   * redeployment.
   */
  protected synchronized void initializeRedeployment() {
    if (watcher != null) {
      throw new IllegalStateException("Redeployment already started ? The watcher already exists");
    }
    // Compute the application id. We append "-redeploy" to ease the identification in the process list.
    vertxApplicationBackgroundId = UUID.randomUUID().toString() + "-redeploy";
    watcher = new Watcher(getCwd(), redeploy,
        this::startAsBackgroundApplication,  // On deploy
        this::stopBackgroundApplication, // On undeploy
        onRedeployCommand, // In between command
        redeployGracePeriod, // The redeploy grace period
        redeployScanPeriod); // The redeploy scan period

    // Close the watcher when the JVM is terminating.
    // Notice that the vert.x finalizer is not registered when we run in redeploy mode.
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        shutdownRedeployment();
      }
    });
    // Start the watching process, it triggers the initial deployment.
    watcher.watch();
  }

  /**
   * Stop the redeployment if started.
   */
  protected synchronized void shutdownRedeployment() {
    if (watcher != null) {
      watcher.close();
      watcher = null;
    }
  }

  /**
   * On-Undeploy action invoked while redeploying. It just stops the application launched in background.
   *
   * @param onCompletion an optional on-completion handler. If set it must be invoked at the end of this method.
   */
  protected synchronized void stopBackgroundApplication(Handler<Void> onCompletion) {
    executionContext.execute("stop", vertxApplicationBackgroundId);
    if (onCompletion != null) {
      onCompletion.handle(null);
    }
  }

  /**
   * On-Deploy action invoked while redeploying. It just starts the application in background, copying all input
   * parameters. In addition, the vertx application id is set.
   *
   * @param onCompletion an optional on-completion handler. If set it must be invoked at the end of this method.
   */
  protected void startAsBackgroundApplication(Handler<Void> onCompletion) {
    // We need to copy all options and arguments.
    List<String> args = new ArrayList<>();
    // Prepend the command.
    args.add("run");
    args.add("--vertx-id=" + vertxApplicationBackgroundId);
    args.addAll(executionContext.commandLine().allArguments());
    // No need to add the main-verticle as it's part of the allArguments list.
    if (cluster) {
      args.add("--cluster");
    }
    if (clusterHost != null) {
      args.add("--cluster-host=" + clusterHost);
    }
    if (clusterPort != 0) {
      args.add("--cluster-port=" + clusterPort);
    }
    if (ha) {
      args.add("--ha");
    }
    if (haGroup != null && !haGroup.equals("__DEFAULT__")) {
      args.add("--hagroup=" + haGroup);
    }
    if (quorum != -1) {
      args.add("--quorum=" + quorum);
    }
    if (classpath != null && !classpath.isEmpty()) {
      args.add("--classpath=" + classpath.stream().collect(Collectors.joining(File.pathSeparator)));
    }
    if (config != null) {
      args.add("--config=" + config);
    }
    if (instances != 1) {
      args.add("--instances=" + instances);
    }
    if (worker) {
      args.add("--worker");
    }
    if (systemProperties != null) {
      args.addAll(systemProperties.stream().map(s -> "-D" + s).collect(Collectors.toList()));
    }

    // Enable stream redirection
    args.add("--redirect-output");

    executionContext.execute("start", args.toArray(new String[args.size()]));
    if (onCompletion != null) {
      onCompletion.handle(null);
    }
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
