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

import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.impl.Args;
import org.vertx.java.deploy.impl.VerticleManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Starter {

  private static final Logger log = LoggerFactory.getLogger(Starter.class);

  private static final String CP_SEPARATOR =
      System.getProperty("os.name").startsWith("Windows") ? ";" : ":";

  public static void main(String[] args) {
    new Starter(args);
  }

  private VertxInternal vertx = new DefaultVertx();
  private VerticleManager mgr;

  private Starter(String[] sargs) {
    if (sargs.length == 0) {
      displaySyntax();
    } else {
      String command = sargs[0].toLowerCase();
      if (command.equals("version")) {
        System.out.println("vert.x 1.1.0.final");
      } else if (command.equals("run")) {
        runVerticle(sargs);
      } else {
        displaySyntax();
      }
    }
  }

  private void runVerticle(String[] sargs) {

    Args args = new Args(sargs);

    boolean clustered = args.map.get("-cluster") != null;
    if (clustered) {
      System.out.print("Starting clustering...");
      int clusterPort = args.getInt("-cluster-port");
      if (clusterPort == -1) {
        clusterPort = 25500;
      }
      String clusterHost = args.map.get("-cluster-host");
      if (clusterHost == null) {
        clusterHost = getDefaultAddress();
        if (clusterHost == null) {
          System.err.println("Unable to find a default network interface for clustering. Please specify one using -cluster-host");
          return;
        } else {
          System.out.println("No cluster-host specified so using address " + clusterHost);
        }
      }
      vertx = new DefaultVertx(clusterPort, clusterHost);
    }
    mgr = new VerticleManager(vertx);

    addShutdownHook();
    if (sargs.length < 2) {
      displaySyntax();
    }
    String main = sargs[1];

    if (main == null) {
      displaySyntax();
      return;
    }

    boolean worker = args.map.get("-worker") != null;

    String name = args.map.get("-name");

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
    final URL[] urls = new URL[parts.length];
    for (String part: parts) {
      try {
        URL url = new File(part).toURI().toURL();
        urls[index++] = url;
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("Invalid path " + part + " in cp " + cp) ;
      }
    }

    String sinstances = args.map.get("-instances");
    int instances;
    if (sinstances != null) {
      try {
        instances = Integer.parseInt(sinstances);

        if (instances != -1 && instances < 1) {
          System.err.println("Invalid number of instances");
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
      try {
        String sconf = new Scanner(new File(configFile)).useDelimiter("\\A").next();
        try {
          conf = new JsonObject(sconf);
        } catch (DecodeException e) {
          System.err.println("Configuration file does not contain a valid JSON object");
          return;
        }
      } catch (FileNotFoundException e) {
        System.err.println("Config file " + configFile + " does not exist");
        return;
      }
    } else {
      conf = null;
    }

    mgr.deploy(worker, name, main, conf, urls, instances, null, new SimpleHandler() {
      public void handle() {
        System.out.println("Started");
      }
    });
    mgr.block();
  }


  private void addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        final CountDownLatch latch = new CountDownLatch(1);
        mgr.undeployAll(new SimpleHandler() {
          public void handle() {
            latch.countDown();
          }
        });
        while (true) {
          try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
              log.error("Timed out waiting to undeploy");
            }
            break;
          } catch (InterruptedException e) {
            //OK - can get spurious interupts
          }
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

  private void displaySyntax() {

    String usage =
"Usage: vertx [run|deploy|undeploy|start|stop] [main] [-options]\n\n" +

"    vertx run <main> [-options]\n" +
"        runs a verticle called <main> in its own instance of vert.x.\n" +
"        <main> can be a JavaScript script, a Ruby script, A Groovy script, or a\n" +
"        Java class.\n\n" +
"    valid options are:\n" +
"        -conf <config_file>    Specifies configuration that should be provided \n" +
"                               to the verticle. <config_file> should reference \n" +
"                               a text file containing a valid JSON object.\n" +
"        -cp <path>             specifies the path on which to search for <main>\n" +
"                               and any referenced resources.\n" +
"                               Defaults to '.' (current directory).\n" +
"        -instances <instances> specifies how many instances of the verticle will\n" +       // 80 chars at will
"                               be deployed. Defaults to the number of available\n" +
"                               cores on the system.\n" +
"        -worker                if specified then the verticle is a worker\n" +
"                               verticle.\n" +
"        -cluster               if specified then the vert.x instance will form a\n" +
"                               cluster with any other vert.x instances on the\n" +
"                               network.\n" +
"        -cluster-port          port to use for cluster communication.\n" +
"                               Default is 25500.\n" +
"        -cluster-host          host to bind to for cluster communication.\n" +
"                               Default is 0.0.0.0 (all interfaces).\n\n\n" +

"    vertx version\n" +
"        displays the version";

     System.out.println(usage);
  }

}
