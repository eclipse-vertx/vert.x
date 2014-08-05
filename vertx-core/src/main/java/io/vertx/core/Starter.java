/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
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
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Starter {

  private static final Logger log = LoggerFactory.getLogger(Starter.class);

  public static void main(String[] args) {
    new Starter().run(args);
  }

  public void run(String[] sargs) {

    Args args = new Args(sargs);
    sargs = removeOptions(sargs);

    if (sargs.length == 0) {
      displaySyntax();
    } else {
      String command = sargs[0].toLowerCase();
      if ("version".equals(command)) {
        log.info(getVersion());
      } else {
        if (sargs.length < 2) {
          displaySyntax();
        } else {
          String main = sargs[1];
          switch (command) {
            case "run":
              runVerticle(main, args);
              break;
            default:
              displaySyntax();
          }
        }
      }
    }
  }

  private final CountDownLatch stopLatch = new CountDownLatch(1);

  private String[] removeOptions(String[] args) {
    List<String> munged = new ArrayList<>();
    for (String arg: args) {
      if (!arg.startsWith("-")) {
        munged.add(arg);
      }
    }
    return munged.toArray(new String[munged.size()]);
  }

  private static <T> AsyncResultHandler<T> createLoggingHandler(final String message, final Handler<AsyncResult<T>> completionHandler) {
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

  private Vertx startVertx(boolean clustered, Args args) {
    Vertx vertx;
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
      Vertx.vertx(VertxOptions.options().setClusterHost(clusterHost).setClusterPort(clusterPort).setClustered(true), ar -> {
        result.set(ar);
        latch.countDown();
      });
      try {
        if (!latch.await(2, TimeUnit.MINUTES)) {
          log.error("Timed out in forming cluster");
          return null;
        }
      } catch (InterruptedException e) {
        log.error("Thread interrupted in forming cluster");
        return null;
      }
      if (result.get().failed()) {
        log.error("Failed to form cluster");
        result.get().cause().printStackTrace();
        return null;
      }
      vertx = result.get().result();
    } else {
      vertx = Vertx.vertx();
    }
    return vertx;
  }

  private void runVerticle(String main, Args args) {
    boolean clustered = args.map.get("-cluster") != null;
    Vertx vertx = startVertx(clustered, args);

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

    String configFile = args.map.get("-conf");
    JsonObject conf;

    if (configFile != null) {
      try (Scanner scanner = new Scanner(new File(configFile)).useDelimiter("\\A")){
        String sconf = scanner.next();
        try {
          conf = new JsonObject(sconf);
        } catch (DecodeException e) {
          log.error("Configuration file does not contain a valid JSON object");
          return;
        }
      } catch (FileNotFoundException e) {
        log.error("Config file " + configFile + " does not exist");
        return;
      }
    } else {
      conf = null;
    }

    boolean worker = args.map.get("-worker") != null;
    String message = (worker) ? "deploying worker verticle" : "deploying verticle";
    for (int i = 0; i < instances; i++) {
      vertx.deployVerticle(main, DeploymentOptions.options().setConfig(conf).setWorker(worker), createLoggingHandler(message, res -> {
        if (res.failed()) {
          // Failed to deploy
          unblock();
        }
      }));
    }

    addShutdownHook(vertx);
    block();
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

  private void displaySyntax() {

    String usage =

      "    vertx run <main> [-options]                                                \n" +
        "        runs a verticle called <main> in its own instance of vert.x.           \n" +
        "        <main> can be a JavaScript script, a Ruby script, A Groovy script,     \n" +
        "        a Java class, a Java source file, or a Python Script.\n\n" +
        "    valid options are:\n" +
        "        -conf <config_file>    Specifies configuration that should be provided \n" +
        "                               to the verticle. <config_file> should reference \n" +
        "                               a text file containing a valid JSON object      \n" +
        "                               which represents the configuration.             \n" +
        "        -instances <instances> specifies how many instances of the verticle    \n" +
        "                               will be deployed. Defaults to 1                 \n" +
        "        -worker                if specified then the verticle is a worker      \n" +
        "                               verticle.                                       \n" +
        "        -cluster               if specified then the vert.x instance will form \n" +
        "                               a cluster with any other vert.x instances on    \n" +
        "                               the network.                                    \n" +
        "        -cluster-port          port to use for cluster communication.          \n" +
        "                               Default is 0 which means chose a spare          \n" +
        "                               random port.                                    \n" +
        "        -cluster-host          host to bind to for cluster communication.      \n" +
        "                               If this is not specified vert.x will attempt    \n" +
        "                               to choose one from the available interfaces.  \n\n" +

        "    vertx version                                                              \n" +
        "        displays the version";

    log.info(usage);
  }
}
