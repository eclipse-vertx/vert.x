/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core;

import io.vertx.core.impl.Args;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Starter {

  public static final String VERTX_OPTIONS_PROP_PREFIX = "vertx.options.";
  public static final String DEPLOYMENT_OPTIONS_PROP_PREFIX = "vertx.deployment.options.";

  private static final Logger log = LoggerFactory.getLogger(Starter.class);
  public static List<String> PROCESS_ARGS;

  public static void main(String[] args) {
    new Starter().run(args);
  }

  private final CountDownLatch stopLatch = new CountDownLatch(1);
  protected Vertx vertx;
  protected VertxOptions options;
  protected DeploymentOptions deploymentOptions;


  public void run(String[] sargs) {

    PROCESS_ARGS = Collections.unmodifiableList(Arrays.asList(sargs));

    Args args = new Args(sargs);

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
          String main = sargs[1];
          runVerticle(main, args);
          return;
        }
      }
    }

    String main = readMainVerticleFromManifest();
    if (main != null) {
      runVerticle(main, args);
    } else {
      displaySyntax();
    }
  }

  public void block() {
    try {
      stopLatch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void unblock() {
    stopLatch.countDown();
  }

  private Vertx startVertx(boolean clustered, boolean ha, Args args) {
    options = new VertxOptions();
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
        options.setHAEnabled(true).setHAGroup(haGroup);
        if (quorumSize != -1) {
          options.setQuorumSize(quorumSize);
        }
      }
      Vertx.vertxAsync(options, ar -> {
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
      vertx = result.get().result();
    } else {
      vertx = Vertx.vertx(options);
    }
    return vertx;
  }

  private void runVerticle(String main, Args args) {
    boolean ha = args.map.get("-ha") != null;
    boolean clustered = args.map.get("-cluster") != null || ha;

    Vertx vertx = startVertx(clustered, ha, args);

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
    vertx.deployVerticle(main, deploymentOptions.setConfig(conf).setWorker(worker).setHa(ha).setInstances(instances), createLoggingHandler(message, res -> {
      if (res.failed()) {
        // Failed to deploy
        unblock();
      }
    }));

    addShutdownHook(vertx);
    block();
  }


  private <T> AsyncResultHandler<T> createLoggingHandler(final String message, final Handler<AsyncResult<T>> completionHandler) {
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

  private void addShutdownHook(Vertx vertx) {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        final CountDownLatch latch = new CountDownLatch(1);
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
    try (InputStream is = getClass().getClassLoader().getResourceAsStream("vertx-version.txt")) {
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
        Manifest manifest = new Manifest(resources.nextElement().openStream());
        Attributes attributes = manifest.getMainAttributes();
        if (Starter.class.getName().equals(attributes.getValue("Main-Class"))) {
          String theMainVerticle = attributes.getValue("Main-Verticle");
          if (theMainVerticle != null) {
            return theMainVerticle;
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
    return null;
  }

  private void displaySyntax() {

    // TODO
    // Update usage string for ha
    // Also for vertx version
    String usage =

        "    vertx run <main> [-options]                                                \n" +
        "        runs a verticle called <main> in its own instance of vert.x.           \n" +
        "        <main> can be a JavaScript script, a Ruby script, A Groovy script,     \n" +
        "        a Java class, a Java source file, or a Python Script.\n\n" +
        "    valid options are:\n" +
        "        -conf <config>         Specifies configuration that should be provided \n" +
        "                               to the verticle. <config> should reference      \n" +
        "                               either a text file containing a valid JSON      \n" +
        "                               object which represents the configuration OR    \n" +
        "                               be a JSON string.                               \n" +
        "        -instances <instances> specifies how many instances of the verticle    \n" +
        "                               will be deployed. Defaults to 1                 \n" +
        "        -worker                if specified then the verticle is a worker      \n" +
        "                               verticle.                                       \n" +
        "        -cluster               if specified then the vert.x instance will form \n" +
        "                               a cluster with any other vert.x instances on    \n" +
        "                               the network.                                    \n" +
        "        -cluster-port          port to use for cluster communication.          \n" +
        "                               Default is 0 which means choose a spare          \n" +
        "                               random port.                                    \n" +
        "        -cluster-host          host to bind to for cluster communication.      \n" +
        "                               If this is not specified vert.x will attempt    \n" +
        "                               to choose one from the available interfaces.  \n\n" +
        "        -ha                    if specified the verticle will be deployed as a \n" +
        "                               high availability (HA) deployment.              \n" +
        "                               This means it can fail over to any other nodes \n" +
        "                               in the cluster started with the same HA group   \n" +
        "        -quorum                used in conjunction with -ha this specifies the \n" +
        "                               minimum number of nodes in the cluster for any  \n" +
        "                               HA deployments to be active. Defaults to 0      \n" +
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
