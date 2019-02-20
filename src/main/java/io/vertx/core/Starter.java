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

package io.vertx.core;

import io.vertx.core.impl.Args;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.VertxMetricsFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * A {@code main()} class that can be used to create Vert.x instance and deploy a verticle, or run a bare Vert.x instance.
 * <p>
 * This class is used by the {@code vertx} command line utility to deploy verticles from the command line.
 * <p>
 * E.g.
 * <p>
 * {@code vertx run myverticle.js}
 * <p>
 * It can also be used as the main class of an executable jar so you can run verticles directly with:
 * <p>
 * {@code java -jar myapp.jar}
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @deprecated Use {@link Launcher} instead
 */
@Deprecated
public class Starter {

  public static final String VERTX_OPTIONS_PROP_PREFIX = "vertx.options.";
  public static final String DEPLOYMENT_OPTIONS_PROP_PREFIX = "vertx.deployment.options.";
  public static final String METRICS_OPTIONS_PROP_PREFIX = "vertx.metrics.options.";

  private static final String PATH_SEP = System.getProperty("path.separator");
  private static final Logger log = LoggerFactory.getLogger(Starter.class);
  public static List<String> PROCESS_ARGS;

  public static void main(String[] sargs) {
    Args args = new Args(sargs);

    String extraCP = args.map.get("-cp");
    if (extraCP != null) {
      // If an extra CP is specified (e.g. to provide cp to a jar or cluster.xml) we must create a new classloader
      // and run the starter using that so it's visible to the rest of the code
      String[] parts = extraCP.split(PATH_SEP);
      URL[] urls = new URL[parts.length];
      for (int p = 0; p < parts.length; p++) {
        String part = parts[p];
        File file = new File(part);
        try {
          URL url = file.toURI().toURL();
          urls[p] = url;
        } catch (MalformedURLException e) {
          throw new IllegalStateException(e);
        }
      }
      ClassLoader icl = new URLClassLoader(urls, Starter.class.getClassLoader());
      ClassLoader oldTCCL = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(icl);
      try {
        Class<?> clazz = icl.loadClass(Starter.class.getName());
        Object instance = clazz.newInstance();
        Method run = clazz.getMethod("run", Args.class, String[].class);
        run.invoke(instance, args, sargs);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      } finally {
        Thread.currentThread().setContextClassLoader(oldTCCL);
      }
    } else {
      // No extra CP, just invoke directly
      new Starter().run(args, sargs);
    }
  }

  public static void runCommandLine(String commandLine) {
    new Starter().run(commandLine);
  }

  protected Vertx vertx;
  protected VertxOptions options;
  protected DeploymentOptions deploymentOptions;

  protected void run(String commandLine) {
    String[] sargs = commandLine.split(" ");
    Args args = new Args(sargs);
    run(args, sargs);
  }

  protected void run(String[] sargs) {
    run(new Args(sargs), sargs);
  }

  // Note! Must be public so can be called by reflection
  public void run(Args args, String[] sargs) {

    PROCESS_ARGS = Collections.unmodifiableList(Arrays.asList(sargs));

    String main = readMainVerticleFromManifest();
    if (main != null) {
      runVerticle(main, args);
    } else {
      if (sargs.length > 0) {
        String first = sargs[0];
        if (first.equals("-version")) {
          log.info(getVersion());
          return;
        } else if (first.equals("run")) {
          if (sargs.length < 2) {
            displaySyntax();
            return;
          } else {
            main = sargs[1];
            runVerticle(main, args);
            return;
          }
        } else if (first.equals("-ha")) {
          // Create a bare instance
          runBare(args);
          return;
        }
      }
      displaySyntax();
    }
  }

  /**
   * Hook for sub classes of {@link Starter} before the vertx instance is started.
   */
  protected void beforeStartingVertx(VertxOptions options) {

  }

  /**
   * Hook for sub classes of {@link Starter} after the vertx instance is started.
   */
  protected void afterStartingVertx() {

  }

  /**
   * Hook for sub classes of {@link Starter} before the verticle is deployed.
   */
  protected void beforeDeployingVerticle(DeploymentOptions deploymentOptions) {

  }

  /**
   * A deployment failure has been encountered. You can override this method to customize the behavior.
   * By default it closes the `vertx` instance.
   */
  protected void handleDeployFailed() {
    // Default behaviour is to close Vert.x if the deploy failed
    vertx.close();
  }


  private Vertx startVertx(boolean clustered, boolean ha, Args args) {
    MetricsOptions metricsOptions;
    ServiceLoader<VertxMetricsFactory> factories = ServiceLoader.load(VertxMetricsFactory.class);
    if (factories.iterator().hasNext()) {
      VertxMetricsFactory factory = factories.iterator().next();
      metricsOptions = factory.newOptions();
    } else {
      metricsOptions = new MetricsOptions();
    }
    configureFromSystemProperties(metricsOptions, METRICS_OPTIONS_PROP_PREFIX);
    options = new VertxOptions().setMetricsOptions(metricsOptions);
    configureFromSystemProperties(options, VERTX_OPTIONS_PROP_PREFIX);
    if (clustered) {
      log.info("Starting clustering...");
      int clusterPort = args.getInt("-cluster-port");
      if (clusterPort == -1) {
        // Default to zero - this means choose an ephemeral port
        clusterPort = 0;
      }
      String clusterHost = args.map.get("-cluster-host");
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
      if (ha) {
        String haGroup = args.map.get("-hagroup");
        int quorumSize = args.getInt("-quorum");
        options.setHAEnabled(true);
        if (haGroup != null) {
          options.setHAGroup(haGroup);
        }
        if (quorumSize != -1) {
          options.setQuorumSize(quorumSize);
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
        log.error("Failed to form cluster", result.get().cause());
        return null;
      }
      vertx = result.get().result();
    } else {
      beforeStartingVertx(options);
      vertx = Vertx.vertx(options);
    }
    addShutdownHook();
    afterStartingVertx();
    return vertx;
  }

  private void runBare(Args args) {
    // ha is necessarily true here,
    // so clustered is
    Vertx vertx = startVertx(true, true, args);
    if (vertx == null) {
      // Throwable should have been logged at this point
      return;
    }

    // As we do not deploy a verticle, other options are irrelevant (instances, worker, conf)
  }

  private void runVerticle(String main, Args args) {
    boolean ha = args.map.get("-ha") != null;
    boolean clustered = args.map.get("-cluster") != null || ha;

    Vertx vertx = startVertx(clustered, ha, args);
    if (vertx == null) {
      // Throwable should have been logged at this point
      return;
    }

    String sinstances = args.map.get("-instances");
    int instances;
    if (sinstances != null) {
      try {
        instances = Integer.parseInt(sinstances);

        if (instances != -1 && instances < 1) {
          log.error("Invalid number of instances");
          displaySyntax();
          return;
        }
      } catch (NumberFormatException e) {
        displaySyntax();
        return;
      }
    } else {
      instances = 1;
    }

    String confArg = args.map.get("-conf");
    JsonObject conf;

    if (confArg != null) {
      try (Scanner scanner = new Scanner(new File(confArg)).useDelimiter("\\A")){
        String sconf = scanner.next();
        try {
          conf = new JsonObject(sconf);
        } catch (DecodeException e) {
          log.error("Configuration file " + sconf + " does not contain a valid JSON object");
          return;
        }
      } catch (FileNotFoundException e) {
        try {
          conf = new JsonObject(confArg);
        } catch (DecodeException e2) {
          log.error("-conf option does not point to a file and is not valid JSON: " + confArg);
          return;
        }
      }
    } else {
      conf = null;
    }

    boolean worker = args.map.get("-worker") != null;
    String message = (worker) ? "deploying worker verticle" : "deploying verticle";
    deploymentOptions = new DeploymentOptions();
    configureFromSystemProperties(deploymentOptions, DEPLOYMENT_OPTIONS_PROP_PREFIX);

    deploymentOptions.setConfig(conf).setWorker(worker).setHa(ha).setInstances(instances);

    beforeDeployingVerticle(deploymentOptions);
    vertx.deployVerticle(main, deploymentOptions, createLoggingHandler(message, res -> {
      if (res.failed()) {
        // Failed to deploy
        handleDeployFailed();
      }
    }));
  }

  private <T> Handler<AsyncResult<T>> createLoggingHandler(final String message, final Handler<AsyncResult<T>> completionHandler) {
    return res -> {
      if (res.failed()) {
        Throwable cause = res.cause();
        if (cause instanceof VertxException) {
          VertxException ve = (VertxException)cause;
          log.error(ve.getMessage());
          if (ve.getCause() != null) {
            log.error(ve.getCause());
          }
        } else {
          log.error("Failed in " + message, cause);
        }
      } else {
        log.info("Succeeded in " + message);
      }
      if (completionHandler != null) {
        completionHandler.handle(res);
      }
    };
  }

  private void configureFromSystemProperties(Object options, String prefix) {
    Properties props = System.getProperties();
    Enumeration e = props.propertyNames();
    // Uhh, properties suck
    while (e.hasMoreElements()) {
      String propName = (String)e.nextElement();
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

  private Method getSetter(String fieldName, Class<?> clazz) {
    Method[] meths = clazz.getDeclaredMethods();
    for (Method meth: meths) {
      if (("set" + fieldName).toLowerCase().equals(meth.getName().toLowerCase())) {
        return meth;
      }
    }
    return null;
  }

  private void addShutdownHook() {
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
  private String getDefaultAddress() {
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

  public String getVersion() {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream("META-INF/vertx/vertx-version.txt")) {
      if (is == null) {
        throw new IllegalStateException("Cannot find vertx-version.txt on classpath");
      }
      try (Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
        return scanner.hasNext() ? scanner.next() : "";
      }
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
  }

  private String readMainVerticleFromManifest() {
    try {
      Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
      while (resources.hasMoreElements()) {
        InputStream stream = null;
        try {
          stream = resources.nextElement().openStream();
          Manifest manifest = new Manifest(stream);
          Attributes attributes = manifest.getMainAttributes();
          String mainClass = attributes.getValue("Main-Class");
          if (Starter.class.getName().equals(mainClass)) {
            String theMainVerticle = attributes.getValue("Main-Verticle");
            if (theMainVerticle != null) {
              return theMainVerticle;
            }
          }
        } finally {
          closeQuietly(stream);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
    return null;
  }

  private void closeQuietly(InputStream stream) {
    if (stream != null) {
      try {
        stream.close();
      } catch (IOException e) {
        // Ignore it.
      }
    }
  }

  private void displaySyntax() {

    String usage =

        "    vertx run <main> [-options]                                                \n" +
        "        runs a verticle called <main> in its own instance of vert.x.         \n\n" +
        "    valid options are:                                                         \n" +
        "        -conf <config>         Specifies configuration that should be provided \n" +
        "                               to the verticle. <config> should reference      \n" +
        "                               either a text file containing a valid JSON      \n" +
        "                               object which represents the configuration OR    \n" +
        "                               be a JSON string.                               \n" +
        "        -instances <instances> specifies how many instances of the verticle    \n" +
        "                               will be deployed. Defaults to 1                 \n" +
        "        -worker                if specified then the verticle is a worker      \n" +
        "                               verticle.                                       \n" +
        "        -cp <classpath>        provide an extra classpath to be used for the   \n" +
        "                               verticle deployment.                            \n" +
        "        -cluster               if specified then the vert.x instance will form \n" +
        "                               a cluster with any other vert.x instances on    \n" +
        "                               the network.                                    \n" +
        "        -cluster-port          port to use for cluster communication.          \n" +
        "                               Default is 0 which means choose a spare         \n" +
        "                               random port.                                    \n" +
        "        -cluster-host          host to bind to for cluster communication.      \n" +
        "                               If this is not specified vert.x will attempt    \n" +
        "                               to choose one from the available interfaces.    \n" +
        "        -ha                    if specified the verticle will be deployed as a \n" +
        "                               high availability (HA) deployment.              \n" +
        "                               This means it can fail over to any other nodes  \n" +
        "                               in the cluster started with the same HA group   \n" +
        "        -quorum                used in conjunction with -ha this specifies the \n" +
        "                               minimum number of nodes in the cluster for any  \n" +
        "                               HA deploymentIDs to be active. Defaults to 0    \n" +
        "        -hagroup               used in conjunction with -ha this specifies the \n" +
        "                               HA group this node will join. There can be      \n" +
        "                               multiple HA groups in a cluster. Nodes will only\n" +
        "                               failover to other nodes in the same group.      \n" +
        "                               Defaults to __DEFAULT__                       \n\n" +

        "    vertx -version                                                             \n" +
        "        displays the version";

    log.info(usage);
  }
}
