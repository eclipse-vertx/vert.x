/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.deploy.impl.cli;

import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.impl.Args;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.*;
import java.util.Enumeration;
import java.util.Scanner;

/**
 * @author Oliver Nautsch
 */
class StarterArgs {

  private static final Logger log = LoggerFactory.getLogger(Starter.class);

  private static final String CP_SEPARATOR =
    System.getProperty("os.name").startsWith("Windows") ? ";" : ":";

  private boolean clustered;
  private int clusterPort;
  private String clusterHost;
  private String repo;
  private boolean worker;
  private URL[] classpathURLs;
  private JsonObject conf;
  private int instances;
  private String command;
  private String operand;

  /**
   * @param sargs the command line args
   */
  StarterArgs(String[] sargs) {
    if (sargs.length < 1) {
      throw new Problem("command missing");
    }

    command = sargs[0].toLowerCase();

    if (!"version".equals(command)) {
      initCommandWithOperand(sargs);
    }
  }

  private void initCommandWithOperand(String[] sargs) {
    if (sargs.length < 2) {
      throw new Problem("operand missing");
    }

    operand = sargs[1];

    {
      Args args = new Args(sargs);

      initClusterArgs(args);
      initRepoArgs(args);
      initWorkerArg(args);
      initClasspathArgs(args);
      initConf(args);
      initInstancesArg(args);
    }
  }

  private void initInstancesArg(Args args) {
    String sinstances = args.map.get("-instances");

    if (sinstances != null) {
      try {
        instances = Integer.parseInt(sinstances);

        if (instances != -1 && instances < 1) {
          throw new Problem("Invalid number of instances");
        }
      } catch (NumberFormatException e) {
        throw new Problem("can not parse number for instances: " + instances);
      }
    } else {
      instances = 1;
    }
  }

  private void initConf(Args args) {
    String configFile = args.map.get("-conf");

    if (configFile != null) {
      try {
        String sconf = new Scanner(new File(configFile)).useDelimiter("\\A").next();
        try {
          conf = new JsonObject(sconf);
        } catch (DecodeException e) {
          throw new Problem("Configuration file does not contain a valid JSON object");
        }
      } catch (FileNotFoundException e) {
        throw new Problem("Config file " + configFile + " does not exist");
      }
    }
  }

  private void initClasspathArgs(Args args) {
    String cp = args.map.get("-cp");
    if (cp == null) {
      cp = ".";
    }

    // Convert to URL[]

    String[] parts;

    if (cp.contains(CP_SEPARATOR)) {
      parts = cp.split(CP_SEPARATOR);
    } else {
      parts = new String[] { cp };
    }
    int index = 0;
    classpathURLs = new URL[parts.length];
    for (String part: parts) {
      try {
        URL url = new File(part).toURI().toURL();
        classpathURLs[index++] = url;
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("Invalid path " + part + " in cp " + cp) ;
      }
    }
  }

  private void initWorkerArg(Args args) {
    worker = args.map.get("-worker") != null;
  }

  private void initRepoArgs(Args args) {
    repo = args.map.get("-repo");
  }

  private void initClusterArgs(Args args) {
    clustered = args.map.get("-cluster") != null;
    if (clustered) {
      log.info("Starting clustering...");
      clusterPort = args.getInt("-cluster-port");
      if (clusterPort == -1) {
        clusterPort = 25500;
      }
      clusterHost = args.map.get("-cluster-host");
      if (clusterHost == null) {
        clusterHost = getDefaultAddress();
        if (clusterHost == null) {
          log.error("Unable to find a default network interface for clustering. Please specify one using -cluster-host");
          return;
        } else {
          log.info("No cluster-host specified so using address " + clusterHost);
        }
      }
    }
  }

  String getCommand() {
    return command;
  }

  String getOperand() {
    return operand;
  }

  boolean isClustered() {
    return clustered;
  }

  int getClusterPort() {
    return clusterPort;
  }

  String getClusterHost() {
    return clusterHost;
  }

  String getRepo() {
    return repo;
  }

  boolean isWorker() {
    return worker;
  }

  URL[] getClasspathURLs() {
    return classpathURLs;
  }

  JsonObject getConf() {
    return conf;
  }

  int getInstances() {
    return instances;
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

  static void displaySyntax() {

    String usage =

      "    vertx run <main> [-options]\n" +
        "        runs a verticle called <main> in its own instance of vert.x.\n" +
        "        <main> can be a JavaScript script, a Ruby script, A Groovy script, or a\n" +
        "        Java class.\n\n" +
        "    valid options are:\n" +
        "        -conf <config_file>    Specifies configuration that should be provided \n" +
        "                               to the verticle. <config_file> should reference \n" +
        "                               a text file containing a valid JSON object\n" +
        "                               which represents the configuration.\n" +
        "        -cp <path>             specifies the path on which to search for <main>\n" +
        "                               and any referenced resources.\n" +
        "                               Defaults to '.' (current directory).\n" +
        "        -instances <instances> specifies how many instances of the verticle will\n" +       // 80 chars at will
        "                               be deployed. Defaults to 1\n" +
        "        -repo <repo_host>      specifies the repository to use to install\n" +
        "                               any modules.\n" +
        "                               Default is vert-x.github.com/vertx-mods\n" +
        "        -worker                if specified then the verticle is a worker\n" +
        "                               verticle.\n" +
        "        -cluster               if specified then the vert.x instance will form a\n" +
        "                               cluster with any other vert.x instances on the\n" +
        "                               network.\n" +
        "        -cluster-port          port to use for cluster communication.\n" +
        "                               Default is 25500.\n" +
        "        -cluster-host          host to bind to for cluster communication.\n" +
        "                               If this is not specified vert.x will attempt\n" +
        "                               to choose one from the available interfaces.\n\n" +

        "    vertx runmod <modname> [-options]\n" +
        "        runs a module called <modname> in its own instance of vert.x.\n" +
        "        If the module is not already installed, Vert.x will attempt to install it\n" +
        "        Java class.\n\n" +
        "    valid options are:\n" +
        "        -conf <config_file>    Specifies configuration that should be provided \n" +
        "                               to the module. <config_file> should reference \n" +
        "                               a text file containing a valid JSON object\n" +
        "                               which represents the configuration.\n" +
        "        -instances <instances> specifies how many instances of the verticle will\n" +       // 80 chars at will
        "                               be deployed. Defaults to 1\n" +
        "        -repo <repo_host>      specifies the repository to use to get the module\n" +
        "                               from if it is not already installed.\n" +
        "                               Default is vert-x.github.com/vertx-mods\n" +
        "        -cluster               if specified then the vert.x instance will form a\n" +
        "                               cluster with any other vert.x instances on the\n" +
        "                               network.\n" +
        "        -cluster-port          port to use for cluster communication.\n" +
        "                               Default is 25500.\n" +
        "        -cluster-host          host to bind to for cluster communication.\n" +
        "                               If this is not specified vert.x will attempt\n" +
        "                               to choose one from the available interfaces.\n\n" +

        "    vertx install <modname> [-options]\n" +
        "        attempts to install a module from a remote repository.\n" +
        "        Module will be installed into a local 'mods' directory unless the\n" +
        "        environment variable VERTX_MODS specifies a different location.\n\n" +
        "    valid options are:\n" +
        "        -repo <repo_host>      specifies the repository to use to get the module\n" +
        "                               from if it is not already installed.\n" +
        "                               Default is vert-x.github.com/vertx-mods\n\n" +

        "    vertx uninstall <modname>\n" +
        "        attempts to uninstall a module from a remote repository.\n" +
        "        Module will be uninstalled from the local 'mods' directory unless the\n" +
        "        environment variable VERTX_MODS specifies a different location.\n\n" +

        "    vertx version\n" +
        "        displays the version";

    log.info(usage);
  }

  /**
   * Problem found in parsing args and configuring StarterArgs.
   */
  class Problem extends RuntimeException {

    public Problem(String message) {
      super(message);
    }

    public Problem(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
