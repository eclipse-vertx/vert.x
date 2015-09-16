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

import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.annotations.*;
import io.vertx.core.impl.launcher.VertxLifecycleHooks;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.launcher.ExecutionContext;

import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.ServiceLoader;
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
  public static final String DEPLOYMENT_OPTIONS_PROP_PREFIX = "vertx.deployment.options.";
  public static final String METRICS_OPTIONS_PROP_PREFIX = "vertx.metrics.options.";

  protected Vertx vertx;
  protected int clusterPort;

  protected String clusterHost;
  protected int quorum;

  protected String haGroup;

  protected VertxOptions options;

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
    vertx = startVertx();
  }

  /**
   * Starts the vert.x instance.
   *
   * @return the created instance of vert.x
   */
  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  protected Vertx startVertx() {
    MetricsOptions metricsOptions = getMetricsOptions();
    options = new VertxOptions().setMetricsOptions(metricsOptions);
    configureFromSystemProperties(options, VERTX_OPTIONS_PROP_PREFIX);
    Vertx instance;
    if (isClustered()) {
      log.info("Starting clustering...");
      if (clusterHost == null) {
        clusterHost = getDefaultAddress();
        if (clusterHost == null) {
          log.error("Unable to find a default network interface for clustering. Please specify one using -cluster-host");
          return null;
        } else {
          log.info("No cluster-host specified so using address " + clusterHost);
        }
      }
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<AsyncResult<Vertx>> result = new AtomicReference<>();

      options.setClusterHost(clusterHost).setClusterPort(clusterPort).setClustered(true);
      if (getHA()) {
        options.setHAEnabled(true);
        if (haGroup != null) {
          options.setHAGroup(haGroup);
        }
        if (quorum != -1) {
          options.setQuorumSize(quorum);
        }
      }

      beforeStartingVertx(options);
      create(options, ar -> {
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
        log.error("Failed to form cluster");
        result.get().cause().printStackTrace();
        return null;
      }
      instance = result.get().result();
    } else {
      beforeStartingVertx(options);
      instance = create(options);
    }
    addShutdownHook();
    afterStartingVertx();
    return instance;
  }

  /**
   * Hook called after starting vert.x.
   */
  protected void afterStartingVertx() {
    Object main = executionContext.main();
    if (main instanceof VertxLifecycleHooks) {
      ((VertxLifecycleHooks) main).afterStartingVertx(vertx);
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
   * @return the metric options.
   */
  protected MetricsOptions getMetricsOptions() {
    MetricsOptions metricsOptions;
    ServiceLoader<VertxMetricsFactory> factories = ServiceLoader.load(VertxMetricsFactory.class);
    if (factories.iterator().hasNext()) {
      VertxMetricsFactory factory = factories.iterator().next();
      metricsOptions = factory.newOptions();
    } else {
      metricsOptions = new MetricsOptions();
    }
    configureFromSystemProperties(metricsOptions, METRICS_OPTIONS_PROP_PREFIX);
    return metricsOptions;
  }

  protected void configureFromSystemProperties(Object options, String prefix) {
    Properties props = System.getProperties();
    Enumeration e = props.propertyNames();
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

  private Method getSetter(String fieldName, Class<?> clazz) {
    Method[] meths = clazz.getDeclaredMethods();
    for (Method meth : meths) {
      if (("set" + fieldName).toLowerCase().equals(meth.getName().toLowerCase())) {
        return meth;
      }
    }
    return null;
  }

  /**
   * Registers a shutdown hook closing the vert.x instance when the JVM is terminating.
   */
  protected void addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        CountDownLatch latch = new CountDownLatch(1);
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
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        }
      }
    });
  }

  /**
   * @return Get default interface to use since the user hasn't specified one.
   */
  protected String getDefaultAddress() {
    Enumeration<NetworkInterface> nets;
    try {
      nets = NetworkInterface.getNetworkInterfaces();
    } catch (SocketException e) {
      return null;
    }
    NetworkInterface netinf;
    while (nets.hasMoreElements()) {
      netinf = nets.nextElement();

      Enumeration<InetAddress> addresses = netinf.getInetAddresses();

      while (addresses.hasMoreElements()) {
        InetAddress address = addresses.nextElement();
        if (!address.isAnyLocalAddress() && !address.isMulticastAddress()
            && !(address instanceof Inet6Address)) {
          return address.getHostAddress();
        }
      }
    }
    return null;
  }


  /**
   * For testing purpose only.
   *
   * @param context the context to inject for testing.
   */
  public void setExecutionContext(ExecutionContext context) {
    this.executionContext = context;
  }
}
