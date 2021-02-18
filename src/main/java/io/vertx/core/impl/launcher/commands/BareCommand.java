/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
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
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.annotations.*;
import io.vertx.core.eventbus.AddressHelper;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.impl.launcher.VertxLifecycleHooks;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.launcher.ExecutionContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Command to create a <em>bare</em> instance of vert.x.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
@Summary("Creates a bare instance of vert.x.")
@Description("This command launches a vert.x instance but do not deploy any verticles. It will " +
  "receive a verticle if another node of the cluster dies.")
@Name("bare")
public class BareCommand extends ClasspathHandler {

  public static final String VERTX_OPTIONS_PROP_PREFIX = "vertx.options.";
  public static final String VERTX_EVENTBUS_PROP_PREFIX = "vertx.eventBus.options.";
  public static final String DEPLOYMENT_OPTIONS_PROP_PREFIX = "vertx.deployment.options.";
  public static final String METRICS_OPTIONS_PROP_PREFIX = "vertx.metrics.options.";

  protected Vertx vertx;

  protected int clusterPort;
  protected String clusterHost;
  protected int clusterPublicPort;
  protected String clusterPublicHost;

  protected int quorum;
  protected String haGroup;

  protected String vertxOptions;
  protected VertxOptions options;

  protected Runnable finalAction;

  /**
   * Sets the quorum option.
   *
   * @param quorum the quorum, default to 1.
   */
  @Option(longName = "quorum", argName = "q")
  @Description("Used in conjunction with -ha this specifies the minimum number of nodes in the cluster for any HA " +
    "deploymentIDs to be active. Defaults to 1.")
  @DefaultValue("-1")
  public void setQuorum(int quorum) {
    this.quorum = quorum;
  }

  /**
   * Sets the HA group name.
   *
   * @param group the name of the group, default to {@code __DEFAULT__}.
   */
  @Option(longName = "hagroup", argName = "group")
  @Description("used in conjunction with -ha this specifies the HA group this node will join. There can be multiple " +
    "HA groups in a cluster. Nodes will only failover to other nodes in the same group. Defaults to '__DEFAULT__'.")
  @DefaultValue("__DEFAULT__")
  public void setHAGroup(String group) {
    this.haGroup = group;
  }

  /**
   * Sets the cluster port.
   *
   * @param port the port
   */
  @Option(longName = "cluster-port", argName = "port")
  @Description("Port to use for cluster communication. Default is 0 which means choose a spare random port.")
  @DefaultValue("0")
  public void setClusterPort(int port) {
    this.clusterPort = port;
  }

  /**
   * Sets the cluster host.
   *
   * @param host the cluster host
   */
  @Option(longName = "cluster-host", argName = "host")
  @Description("host to bind to for cluster communication. If this is not specified vert.x will attempt to choose one" +
    " from the available interfaces.")
  public void setClusterHost(String host) {
    this.clusterHost = host;
  }

  /**
   * Sets the cluster public port.
   *
   * @param port the port
   */
  @Option(longName = "cluster-public-port", argName = "public-port")
  @Description("Public port to use for cluster communication. Default is -1 which means same as cluster port.")
  @DefaultValue("-1")
  public void setClusterPublicPort(int port) {
    this.clusterPublicPort = port;
  }

  /**
   * Sets the cluster public host.
   *
   * @param host the host
   */
  @Option(longName = "cluster-public-host", argName = "public-host")
  @Description("Public host to bind to for cluster communication. If not specified, Vert.x will use the same as cluster host.")
  public void setClusterPublicHost(String host) {
    this.clusterPublicHost = host;
  }

  /**
   * The Vert.x options, it can be a json file or a json string.
   *
   * @param vertxOptions the configuration
   */
  @Option(longName = "options", argName = "options")
  @Description("Specifies the Vert.x options. It should reference either a JSON file which represents the options OR be a JSON string.")
  public void setVertxOptions(String vertxOptions) {
    if (vertxOptions != null) {
      // For inlined configuration remove first and end single and double quotes if any
      this.vertxOptions = vertxOptions.trim()
        .replaceAll("^\"|\"$", "")
        .replaceAll("^'|'$", "");
    } else {
      this.vertxOptions = null;
    }
  }

  /**
   * @return whether or not the vert.x instance should be clustered. This implementation
   * returns {@code true}.
   */
  public boolean isClustered() {
    return true;
  }

  /**
   * @return whether or not the vert.x instance should be launched in high-availability mode. This
   * implementation returns {@code true}.
   */
  public boolean getHA() {
    return true;
  }

  /**
   * Starts the vert.x instance.
   */
  @Override
  public void run() {
    this.run(null);
  }

  /**
   * Starts the vert.x instance and sets the final action (called when vert.x is closed).
   *
   * @param action the action, can be {@code null}
   */
  public void run(Runnable action) {
    this.finalAction = action;
    vertx = startVertx();
  }

  /**
   * Starts the vert.x instance.
   *
   * @return the created instance of vert.x
   */
  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  protected Vertx startVertx() {
    JsonObject optionsJson = getJsonFromFileOrString(vertxOptions, "options");

    EventBusOptions eventBusOptions;
    VertxBuilder builder;
    if (optionsJson == null) {
      eventBusOptions = getEventBusOptions();
      builder = new VertxBuilder();
    } else {
      eventBusOptions = getEventBusOptions(optionsJson.getJsonObject("eventBusOptions"));
      builder = new VertxBuilder(optionsJson);
    }
    options = builder.options();
    options.setEventBusOptions(eventBusOptions);

    beforeStartingVertx(options);
    builder.init();

    configureFromSystemProperties.set(log);
    try {
      configureFromSystemProperties(options, VERTX_OPTIONS_PROP_PREFIX);
      if (options.getMetricsOptions() != null) {
        configureFromSystemProperties(options.getMetricsOptions(), METRICS_OPTIONS_PROP_PREFIX);
      }
    } finally {
      configureFromSystemProperties.set(null);
    }

    Vertx instance;
    if (isClustered()) {
      log.info("Starting clustering...");
      eventBusOptions = options.getEventBusOptions();
      if (!Objects.equals(eventBusOptions.getHost(), EventBusOptions.DEFAULT_CLUSTER_HOST)) {
        clusterHost = eventBusOptions.getHost();
      }
      if (eventBusOptions.getPort() != EventBusOptions.DEFAULT_CLUSTER_PORT) {
        clusterPort = eventBusOptions.getPort();
      }
      if (!Objects.equals(eventBusOptions.getClusterPublicHost(), EventBusOptions.DEFAULT_CLUSTER_PUBLIC_HOST)) {
        clusterPublicHost = eventBusOptions.getClusterPublicHost();
      }
      if (eventBusOptions.getClusterPublicPort() != EventBusOptions.DEFAULT_CLUSTER_PUBLIC_PORT) {
        clusterPublicPort = eventBusOptions.getClusterPublicPort();
      }

      eventBusOptions.setHost(clusterHost)
        .setPort(clusterPort)
        .setClusterPublicHost(clusterPublicHost);
      if (clusterPublicPort != -1) {
        eventBusOptions.setClusterPublicPort(clusterPublicPort);
      }
      if (getHA()) {
        options.setHAEnabled(true);
        if (haGroup != null) {
          options.setHAGroup(haGroup);
        }
        if (quorum != -1) {
          options.setQuorumSize(quorum);
        }
      }

      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<AsyncResult<Vertx>> result = new AtomicReference<>();
      create(builder, ar -> {
        result.set(ar);
        latch.countDown();
      });
      try {
        if (!latch.await(2, TimeUnit.MINUTES)) {
          log.error("Timed out in starting clustered Vert.x");
          return null;
        }
      } catch (InterruptedException e) {
        log.error("Thread interrupted in startup");
        Thread.currentThread().interrupt();
        return null;
      }
      if (result.get().failed()) {
        log.error("Failed to form cluster", result.get().cause());
        return null;
      }
      instance = result.get().result();
    } else {
      instance = create(builder);
    }
    addShutdownHook(instance, log, finalAction);
    afterStartingVertx(instance);
    return instance;
  }

  protected JsonObject getJsonFromFileOrString(String jsonFileOrString, String argName) {
    JsonObject conf;
    if (jsonFileOrString != null) {
      try (Scanner scanner = new Scanner(new File(jsonFileOrString), "UTF-8").useDelimiter("\\A")) {
        String sconf = scanner.next();
        try {
          conf = new JsonObject(sconf);
        } catch (DecodeException e) {
          log.error("Configuration file " + sconf + " does not contain a valid JSON object");
          return null;
        }
      } catch (FileNotFoundException e) {
        try {
          conf = new JsonObject(jsonFileOrString);
        } catch (DecodeException e2) {
          // The configuration is not printed for security purpose, it can contain sensitive data.
          log.error("The -" + argName + " argument does not point to an existing file or is not a valid JSON object", e2);
          return null;
        }
      }
    } else {
      conf = null;
    }
    return conf;
  }

  /**
   * Hook called after starting vert.x.
   *
   * @param instance the created vert.x instance.
   */
  protected void afterStartingVertx(Vertx instance) {
    Object main = executionContext.main();
    if (main instanceof VertxLifecycleHooks) {
      ((VertxLifecycleHooks) main).afterStartingVertx(instance);
    }
  }

  /**
   * Hook called before starting vert.x.
   *
   * @param options the deployment options
   */
  protected void beforeStartingVertx(VertxOptions options) {
    Object main = executionContext.main();
    if (main instanceof VertxLifecycleHooks) {
      ((VertxLifecycleHooks) main).beforeStartingVertx(options);
    }
  }

  /**
   * @return the event bus options.
   */
  protected EventBusOptions getEventBusOptions() {
    return getEventBusOptions(null);
  }

  /**
   * @return the event bus options.
   */
  protected EventBusOptions getEventBusOptions(JsonObject jsonObject) {
    EventBusOptions eventBusOptions = jsonObject == null ? new EventBusOptions() : new EventBusOptions(jsonObject);
    configureFromSystemProperties.set(log);
    try {
      configureFromSystemProperties(eventBusOptions, VERTX_EVENTBUS_PROP_PREFIX);
    } finally {
      configureFromSystemProperties.set(null);
    }
    return eventBusOptions;
  }

  private static final ThreadLocal<Logger> configureFromSystemProperties = new ThreadLocal<>();

  /**
   * This is used as workaround to retain the existing behavior of the Vert.x CLI and won't be executed
   * in other situation.
   */
  public static void configureFromSystemProperties(Object options, String prefix) {
    Logger log = configureFromSystemProperties.get();
    if (log == null) {
      return;
    }
    Properties props = System.getProperties();
    Enumeration<?> e = props.propertyNames();
    // Uhh, properties suck
    while (e.hasMoreElements()) {
      String propName = (String) e.nextElement();
      String propVal = props.getProperty(propName);
      if (propName.startsWith(prefix)) {
        String fieldName = propName.substring(prefix.length());
        Method setter = getSetter(fieldName, options.getClass());
        if (setter == null) {
          log.warn("No such property to configure on options: " + options.getClass().getName() + "." + fieldName);
          continue;
        }
        Class<?> argType = setter.getParameterTypes()[0];
        Object arg;
        try {
          if (argType.equals(String.class)) {
            arg = propVal;
          } else if (argType.equals(int.class)) {
            arg = Integer.valueOf(propVal);
          } else if (argType.equals(long.class)) {
            arg = Long.valueOf(propVal);
          } else if (argType.equals(boolean.class)) {
            arg = Boolean.valueOf(propVal);
          } else if (argType.isEnum()){
            arg = Enum.valueOf((Class<? extends Enum>)argType, propVal);
          } else {
            log.warn("Invalid type for setter: " + argType);
            continue;
          }
        } catch (IllegalArgumentException e2) {
          log.warn("Invalid argtype:" + argType + " on options: " + options.getClass().getName() + "." + fieldName);
          continue;
        }
        try {
          setter.invoke(options, arg);
        } catch (Exception ex) {
          throw new VertxException("Failed to invoke setter: " + setter, ex);
        }
      }
    }
  }

  private static Method getSetter(String fieldName, Class<?> clazz) {
    Method[] meths = clazz.getDeclaredMethods();
    for (Method meth : meths) {
      if (("set" + fieldName).equalsIgnoreCase(meth.getName())) {
        return meth;
      }
    }

    // This set contains the overridden methods
    meths = clazz.getMethods();
    for (Method meth : meths) {
      if (("set" + fieldName).equalsIgnoreCase(meth.getName())) {
        return meth;
      }
    }

    return null;
  }

  /**
   * Registers a shutdown hook closing the given vert.x instance when the JVM is terminating.
   * Optionally, an action can be executed after the termination of the {@link Vertx} instance.
   *
   * @param vertx  the vert.x instance, must not be {@code null}
   * @param log    the log, must not be {@code null}
   * @param action the action, may be {@code null}
   */
  protected static void addShutdownHook(Vertx vertx, Logger log, Runnable action) {
    Runtime.getRuntime().addShutdownHook(new Thread(getTerminationRunnable(vertx, log, action)));
  }

  /**
   * Gets the termination runnable used to close the Vert.x instance.
   *
   * @param vertx  the vert.x instance, must not be {@code null}
   * @param log    the log, must not be {@code null}
   * @param action the action, may be {@code null}
   */
  public static Runnable getTerminationRunnable(Vertx vertx, Logger log, Runnable action) {
    return () -> {
      CountDownLatch latch = new CountDownLatch(1);
      if (vertx != null) {
        vertx.close(ar -> {
          if (!ar.succeeded()) {
            log.error("Failure in stopping Vert.x", ar.cause());
          }
          latch.countDown();
        });
        try {
          if (!latch.await(2, TimeUnit.MINUTES)) {
            log.error("Timed out waiting to undeploy all");
          }
          if (action != null) {
            action.run();
          }
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        }
      }
    };
  }

  /**
   * @return Get default interface to use since the user hasn't specified one.
   * @deprecated as of 4.0, this method is no longer used.
   */
  @Deprecated
  protected String getDefaultAddress() {
    return AddressHelper.defaultAddress();
  }


  /**
   * For testing purpose only.
   *
   * @param context the context to inject for testing.
   */
  public void setExecutionContext(ExecutionContext context) {
    this.executionContext = context;
  }

  /**
   * @return the vert.x instance if created.
   */
  public synchronized Vertx vertx() {
    return vertx;
  }
}
