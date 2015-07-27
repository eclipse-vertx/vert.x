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
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.cli.*;

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


@Summary("Creates a bare instance of vert.x.")
@Description("This command launches a vert.x instance but do not deploy any verticles. It will " +
    "receive a verticle if another node of the cluster dies.")
public class BareCommand extends DefaultCommand {

  public static final String VERTX_OPTIONS_PROP_PREFIX = "vertx.options.";
  public static final String DEPLOYMENT_OPTIONS_PROP_PREFIX = "vertx.deployment.options.";
  public static final String METRICS_OPTIONS_PROP_PREFIX = "vertx.metrics.options.";

  protected final Logger log = LoggerFactory.getLogger(this.getClass());

  protected Vertx vertx;

  protected int clusterPort;
  protected String clusterHost;

  protected int quorum;
  protected String haGroup;

  protected VertxOptions options;

  @Option(longName = "quorum", name = "q")
  @Description("Used in conjunction with -ha this specifies the minimum number of nodes in the cluster for any HA " +
      "deploymentIDs to be active. Defaults to 1.")
  @DefaultValue("-1")
  public void setQuorum(int quorum) {
    this.quorum = quorum;
  }

  @Option(longName = "hagroup", name = "group")
  @Description("used in conjunction with -ha this specifies the HA group this node will join. There can be multiple " +
      "HA groups in a cluster. Nodes will only failover to other nodes in the same group. Defaults to '__DEFAULT__'.")
  @DefaultValue("__DEFAULT__")
  public void setHAGroup(String group) {
    this.haGroup = group;
  }

  @Option(longName = "cluster-port", name = "port")
  @Description("Port to use for cluster communication. Default is 0 which means choose a spare random port.")
  @DefaultValue("0")
  public void setClusterPort(int port) {
    this.clusterPort = port;
  }

  @Option(longName = "cluster-host", name = "host")
  @Description("host to bind to for cluster communication. If this is not specified vert.x will attempt to choose one" +
      " from the available interfaces.")
  public void setClusterHost(String host) {
    this.clusterHost = host;
  }

  /**
   * @return the command name such as 'run'.
   */
  @Override
  public String name() {
    return "bare";
  }

  public boolean isClustered() {
    return true;
  }

  public boolean getHA() {
    return true;
  }


  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    vertx = startVertx();
  }

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
      Vertx.clusteredVertx(options, ar -> {
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
      instance = Vertx.vertx(options);
    }
    addShutdownHook();
    afterStartingVertx();
    return instance;
  }


  protected void afterStartingVertx() {
    final Object main = executionContext.main();
    if (main instanceof VertxLifecycleHooks) {
      ((VertxLifecycleHooks) main).afterStartingVertx(vertx);
    }
  }

  protected void beforeStartingVertx(VertxOptions options) {
    final Object main = executionContext.main();
    if (main instanceof VertxLifecycleHooks) {
      ((VertxLifecycleHooks) main).beforeStartingVertx(options);
    }
  }

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

  /*
  Get default interface to use since the user hasn't specified one
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


}
