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

package org.vertx.java.platform.impl.cli;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.PlatformLocator;
import org.vertx.java.platform.PlatformManager;
import org.vertx.java.platform.impl.Args;
import org.vertx.java.platform.impl.ModuleClassLoader;
import org.vertx.java.platform.impl.resolver.HttpResolution;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Starter {

  private static final Logger log = LoggerFactory.getLogger(Starter.class);

  private static final String CP_SEPARATOR = System.getProperty("path.separator");

  public static void main(String[] args) {
    // Show download stats - they don't display properly in Gradle so we only have them when running
    // on the command line
    HttpResolution.suppressDownloadCounter = false;
    ModuleClassLoader.reverseLoadOrder = false;
    new Starter(args);
  }

  private final CountDownLatch stopLatch = new CountDownLatch(1);

  private Starter(String[] sargs) {
    if (sargs.length < 1) {
      displaySyntax();
    } else {
      String command = sargs[0].toLowerCase();
      Args args = new Args(sargs);
      if ("version".equals(command)) {
        log.info(getVersion());
      } else {
        if (sargs.length < 2) {
          displaySyntax();
        } else {
          String operand = sargs[1];
          switch (command) {
            case "run":
              runVerticle(false, false, operand, args);
              break;
            case "runmod":
              runVerticle(false, true, operand, args);
              break;
            case "runzip":
              runVerticle(true, true, operand, args);
              break;
            case "install":
              installModule(operand);
              break;
            case "uninstall":
              uninstallModule(operand);
              break;
            case "pulldeps":
              pullDependencies(operand);
              break;
            default:
              displaySyntax();
          }
        }
      }
    }
  }

  private void pullDependencies(String modName) {
    createPM().pullInDependencies(modName);
  }

  private void installModule(String modName) {
    createPM().installModule(modName);
  }

  private void uninstallModule(String modName) {
    createPM().uninstallModule(modName);
  }

  private PlatformManager createPM() {
    PlatformManager pm = PlatformLocator.factory.createPlatformManager();
    registerExitHandler(pm);
    return pm;
  }

  private PlatformManager createPM(int port, String host) {
    PlatformManager pm =  PlatformLocator.factory.createPlatformManager(port, host);
    registerExitHandler(pm);
    return pm;
  }

  private void registerExitHandler(PlatformManager mgr) {
    mgr.registerExitHandler(new SimpleHandler() {
      public void handle() {
        unblock();
      }
    });
  }

  private void runVerticle(boolean zip, boolean module, String main, Args args) {
    boolean clustered = args.map.get("-cluster") != null;
    PlatformManager mgr;
    if (clustered) {
      log.info("Starting clustering...");
      int clusterPort = args.getInt("-cluster-port");
      if (clusterPort == -1) {
        clusterPort = 25500;
      }
      String clusterHost = args.map.get("-cluster-host");
      if (clusterHost == null) {
        clusterHost = getDefaultAddress();
        if (clusterHost == null) {
          log.error("Unable to find a default network interface for clustering. Please specify one using -cluster-host");
          return;
        } else {
          log.info("No cluster-host specified so using address " + clusterHost);
        }
      }
      mgr = createPM(clusterPort, clusterHost);
    } else {
      mgr = createPM();
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

    Handler<String> doneHandler = new Handler<String>() {
      public void handle(String id) {
        if (id == null) {
          // Failed to deploy
          unblock();
        }
      }
    };
    if (zip) {
      mgr.deployModuleFromZip(main, conf, instances, doneHandler);
    } else if (module) {
      mgr.deployModule(main, conf, instances, doneHandler);
    } else {
      boolean worker = args.map.get("-worker") != null;

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
      String includes = args.map.get("-includes");
      if (worker) {
        mgr.deployWorkerVerticle(false, main, conf, urls, instances, includes, doneHandler);
      } else {
        mgr.deployVerticle(main, conf, urls, instances, includes, doneHandler);
      }
    }

    addShutdownHook(mgr);
    block();
  }

  private void block() {
    while (true) {
      try {
        stopLatch.await();
        break;
      } catch (InterruptedException e) {
        //Ignore
      }
    }
  }

  private void unblock() {
    stopLatch.countDown();
  }


  private void addShutdownHook(final PlatformManager mgr) {
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

  public final String getVersion() {
    String className = getClass().getSimpleName() + ".class";
    String classPath = getClass().getResource(className).toString();
    if (!classPath.startsWith("jar")) {
      // Class not from JAR
      return "<unknown> (not a jar)";
    }
    String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
    Manifest manifest;
    try (InputStream is = new URL(manifestPath).openStream()) {
      manifest = new Manifest(is);
    } catch (IOException ex) {
      return "<unknown> (" + ex.getMessage() + ")";
    }
    Attributes attr = manifest.getMainAttributes();
    return attr.getValue("Vertx-Version");
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
"        -cp <path>             specifies the path on which to search for       \n" +
"                               <main> and any referenced resources.            \n" +
"                               Defaults to '.' (current directory).            \n" +
"        -instances <instances> specifies how many instances of the verticle    \n" +
"                               will be deployed. Defaults to 1                 \n" +
"                               Default is vert-x.github.com/vertx-mods         \n" +
"        -worker                if specified then the verticle is a worker      \n" +
"                               verticle.                                       \n" +
"        -includes <mod_list>   optional comma separated list of modules        \n" +
"                               which will be added to the classpath of         \n" +
"                               the verticle.                                   \n" +
"        -cluster               if specified then the vert.x instance will form \n" +
"                               a cluster with any other vert.x instances on    \n" +
"                               the network.                                    \n" +
"        -cluster-port          port to use for cluster communication.          \n" +
"                               Default is 25500.                               \n" +
"        -cluster-host          host to bind to for cluster communication.      \n" +
"                               If this is not specified vert.x will attempt    \n" +
"                               to choose one from the available interfaces.  \n\n" +

"    vertx runmod <modname> [-options]                                          \n" +
"        runs a module called <modname> in its own instance of vert.x.          \n" +
"        If the module is not already installed, Vert.x will attempt to install \n" +
"        it from a repository before running it.                            \n\n" +
"    valid options are:                                                         \n" +
"        -conf <config_file>    Specifies configuration that should be provided \n" +
"                               to the module. <config_file> should reference   \n" +
"                               a text file containing a valid JSON object      \n" +
"                               which represents the configuration.             \n" +
"        -instances <instances> specifies how many instances of the verticle    \n" +
"                               will be deployed. Defaults to 1                 \n" +
"                               Default is vert-x.github.com/vertx-mods         \n" +
"        -cluster               if specified then the vert.x instance will form \n" +
"                               a cluster with any other vert.x instances on    \n" +
"                               the network.                                    \n" +
"        -cluster-port          port to use for cluster communication.          \n" +
"                               Default is 25500.                               \n" +
"        -cluster-host          host to bind to for cluster communication.      \n" +
"                               If this is not specified vert.x will attempt    \n" +
"                               to choose one from the available interfaces.  \n\n" +

"    vertx runzip <zipfilename> [-options]                                      \n" +
"        installs then deploys a module which is contained in the zip specified \n" +
"        by <zipfilename>. The module will be installed with a name given by    \n" +
"        <zipfilename> without the .zip extension. If a module with that name   \n" +
"        is already installed this will do nothing.                             \n" +
"        The options accepted by this command are exactly the same as those     \n" +
"        accepted by vertx runmod                                             \n\n" +

"    vertx install <modname> [-options]                                         \n" +
"        attempts to install a module from a remote repository.                 \n" +
"        Module will be installed into a local 'mods' directory or, if the      \n" +
"        module is marked as a system module, the sys-mods directory in the     \n" +
"        Vert.x installation unless the                                         \n" +
"        environment variable VERTX_MODS specifies a different location.      \n\n" +

"    vertx uninstall <modname>                                                  \n" +
"        attempts to uninstall a module from a remote repository.               \n" +
"        Module will be uninstalled from the local 'mods' directory unless the  \n" +
"        environment variable VERTX_MODS specifies a different location.      \n\n" +

"    vertx pulldeps <modname>                                                   \n" +
"        Pulls in the tree of dependencies of the module and puts them in the   \n" +
"        nested module directory (mods) of the module. This allows the module   \n" +
"        to be a completely self contained unit containing all the modules it   \n" +
"        needs to run.                                                          \n" +
"        Vert.x will consult the 'includes' and 'deploys' fields to determine   \n" +
"        which modules to pull in.                                            \n\n" +

"    vertx version                                                              \n" +
"        displays the version";

     log.info(usage);
  }

}
