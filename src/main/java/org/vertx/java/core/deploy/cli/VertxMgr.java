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

package org.vertx.java.core.deploy.cli;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.deploy.Args;
import org.vertx.java.core.deploy.VerticleManager;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.spi.ClusterManager;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.ServerID;
import org.vertx.java.core.parsetools.RecordParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * TODO tidy up and simplify the validation code
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxMgr {

  private static final Logger log = LoggerFactory.getLogger(VertxMgr.class);

  public static void main(String[] args) {
    new VertxMgr(args);
  }

  private VertxMgr(String[] sargs) {
    Args args = new Args(sargs);

    if (sargs.length == 0) {
      displaySyntax();
    } else {

      int port = args.getInt("-port");
      port = port == -1 ? SocketDeployer.DEFAULT_PORT: port;

      VertxCommand cmd = null;

      if (sargs[0].equalsIgnoreCase("version")) {
        System.out.println("vert.x 1.0.0.beta.1");
      } else if (sargs[0].equalsIgnoreCase("start")) {
        startServer(args);
      } else if (sargs[0].equalsIgnoreCase("run")) {
        runApplication(sargs[1], args);
      } else if (sargs[0].equalsIgnoreCase("stop")) {
        cmd = new StopCommand();
        System.out.println("Stopped vert.x server");
      } else if (sargs[0].equalsIgnoreCase("deploy")) {
        DeployCommand dc = createDeployCommand(sargs[1], args, "deploy");
        if (dc == null) {
          return;
        }
        cmd = dc;
      } else if (sargs[0].equalsIgnoreCase("undeploy")) {
        String name = sargs[1];
        if (name == null) {
          displaySyntax();
          return;
        }
        cmd = new UndeployCommand(name);
      } else {
        displaySyntax();
      }

      if (cmd != null) {
        String res = sendCommand(port, cmd);
        System.out.println(res);
      }
    }
  }

  private void runApplication(String main, Args args) {
    if (startCluster(args)) {
      addShutdownHook();
      VerticleManager mgr = VerticleManager.instance;
      DeployCommand dc = createDeployCommand(main, args, "run");
      if (dc != null) {
        JsonObject jsonConf;
        if (dc.conf != null) {
          try {
            jsonConf = new JsonObject(dc.conf);
          } catch (DecodeException e) {
            System.err.println("Configuration file does not contain a valid JSON object");
            return;
          }
        } else {
          jsonConf = null;
        }
        mgr.deploy(dc.worker, dc.name, dc.main, jsonConf, dc.urls, dc.instances, null);
        mgr.block();
      }
    }
  }

  private void startServer(Args args) {
    if (startCluster(args)) {
      addShutdownHook();
      VerticleManager mgr = VerticleManager.instance;
      SocketDeployer sd = new SocketDeployer(mgr, args.getInt("-deploy-port"));
      sd.start();
      System.out.println("vert.x server started");
      mgr.block();
    }
  }

  private void addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        final CountDownLatch latch = new CountDownLatch(1);
        VerticleManager.instance.undeployAll(new SimpleHandler() {
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

  private DeployCommand createDeployCommand(String main, Args args, String command) {

    if (main == null) {
      displaySyntax();
      return null;
    }

    boolean worker = args.map.get("-worker") != null;

    String name = args.map.get("-name");

    String cp = args.map.get("-cp");
    if (cp == null) {
      cp = ".";
    }

    // Convert to URL[]

    String[] parts;
    if (cp.contains(":")) {
      parts = cp.split(":");
    } else {
      parts = new String[] { cp };
    }
    int index = 0;
    final URL[] urls = new URL[parts.length];
    for (String part: parts) {
      File file = new File(part);
      part = file.getAbsolutePath();
      if (!part.endsWith(".jar") && !part.endsWith(".zip") && !part.endsWith("/")) {
        //It's a directory - need to add trailing slash
        part += "/";
      }
      URL url;
      try {
        url = new URL("file://" + part);
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("Invalid path: " + cp) ;
      }
      urls[index++] = url;
    }

    if (main == null) {
      displaySyntax();
      return null;
    }

    String sinstances = args.map.get("-instances");
    int instances;
    if (sinstances != null) {
      try {
        instances = Integer.parseInt(sinstances);

        if (instances != -1 && instances < 1) {
          System.err.println("Invalid number of instances");
          displaySyntax();
          return null;
        }
      } catch (NumberFormatException e) {
        displaySyntax();
        return null;
      }
    } else {
      instances = 1;
    }

    String configFile = args.map.get("-conf");

    String conf;
    if (configFile != null) {
      try {
        conf = new Scanner(new File(configFile)).useDelimiter("\\A").next();
      } catch (FileNotFoundException e) {
        System.err.println("Config file " + configFile + " does not exist");
        return null;
      }
    } else {
      conf = null;
    }

    return new DeployCommand(worker, name, main, conf, urls, instances);
  }


  private boolean startCluster(Args args) {
    final CountDownLatch latch = new CountDownLatch(1);
    boolean clustered = args.map.get("-cluster") != null;
    if (clustered) {
      System.out.print("Starting clustering...");
      int clusterPort = args.getInt("-cluster-port");
      if (clusterPort == -1) {
        clusterPort = 25500;
      }
      String clusterHost = args.map.get("-cluster-host");
      if (clusterHost == null) {
        clusterHost = "0.0.0.0";
      }
      String clusterProviderClass = args.map.get("-cluster-provider");
      if (clusterProviderClass == null) {
        clusterProviderClass = "org.vertx.java.core.eventbus.spi.hazelcast.HazelcastClusterManager";
      }
      final Class clusterProvider;
      try {
        clusterProvider= Class.forName(clusterProviderClass);
      } catch (ClassNotFoundException e) {
        System.err.println("Cannot find class " + clusterProviderClass);
        return false;
      }
      final ServerID clusterServerID = new ServerID(clusterPort, clusterHost);
      VertxInternal.instance.startOnEventLoop(new Runnable() {
        public void run() {
          ClusterManager mgr;
          try {
            mgr = (ClusterManager) clusterProvider.newInstance();
          } catch (Exception e) {
            e.printStackTrace(System.err);
            System.err.println("Failed to instantiate eventbus provider");
            return;
          }
          EventBus bus = new EventBus(clusterServerID, mgr) {
          };
          EventBus.initialize(bus);
          latch.countDown();
        }
      });
    } else {
      // This is ugly - tidy it up!

      VertxInternal.instance.startOnEventLoop(new Runnable() {
        public void run() {
          // Start non clustered event bus
          EventBus bus = new EventBus() {
          };
          EventBus.initialize(bus);
          latch.countDown();
        }
      });
    }
    while (true) {
      try {
        if (!latch.await(10000, TimeUnit.SECONDS)) {
          log.warn("Timed out waiting for clustering to start");
        }
        break;
      } catch (InterruptedException ignore) {
        //OK - spurious wakeup
      }
    }
    if (clustered) {
      System.out.println("Started");
    }
    return true;
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

"    vertx deploy <main> [-options]\n" +
"        deploys a verticle called <main> to a standalone local vert.x server.\n" +
"        <main> can be a JavaScript script, a Ruby script, A Groovy script,\n" +
"        or a Java class.\n\n" +
"    valid options are:\n" +
"        -conf <config_file>    Specifies configuration that should be provided \n" +
"                               to the verticle. <config_file> should reference \n" +
"                               a text file containing a valid JSON object.\n" +
"        -cp <path>             specifies the path on which to search for <main>\n" +
"                               and any referenced resources.\n" +
"                               Defaults to '.' (current directory).\n" +
"        -name <name>           specifies a unique name to give the deployment.\n" +
"                               Used later if you want to undeploy. A name will\n" +
"                               be auto-generated if it is not specified.\n" +
"        -instances <instances> specifies how many instances of the verticle will\n" +
"                               be deployed. Default is 1.\n" +
"        -worker                if specified then the verticle is a worker\n" +
"                               verticle.\n" +
"        -port                  if specified then use the specified port for\n" +
"                               connecting to the server for deployment.\n" +
"                               Default is 25571.\n\n\n" +

"    vertx undeploy <name>\n" +
"        undeploys verticles from a standalone local vert.x server.\n" +
"        <name> is the unique name of the deployment specified when deploying.\n\n" +
"    valid options are:\n" +
"        -port                  if specified then use the specified port for\n" +
"                               connecting to the server for undeployment.\n" +
"                               Default is 25571.\n" +

"    vertx start\n" +
"        starts a standalone local vert.x server.\n\n" +
"    valid options are:\n" +
"        -port                  if specified then use the specified port for\n" +
"                               listening for deployments. Default is 25571.\n" +
"        -cluster               if specified then the vert.x instance will form a\n" +
"                               cluster with any other vert.x instances on the\n" +
"                               network.\n" +
"        -cluster-port          port to use for cluster communication.\n" +
"                               Default is 25500.\n" +
"        -cluster-host          host to bind to for cluster communication.\n" +
"                               Default is 0.0.0.0 (all interfaces).\n\n\n" +

"    vertx stop\n" +
"        stops a standalone local vert.x server.\n\n" +
"    valid options are:\n" +
"        -port                  if specified then connect to the server at the\n" +
"                               specified port to stop it. Default is 25571.\n\n" +

"    vertx version\n" +
"        displays the version";

     System.out.println(usage);
  }

  private String sendCommand(final int port, final VertxCommand command) {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<String> result = new AtomicReference<>();
    VertxInternal.instance.startOnEventLoop(new Runnable() {
      public void run() {
        final NetClient client = new NetClient();
        client.connect(port, "localhost", new Handler<NetSocket>() {
          public void handle(NetSocket socket) {
            if (command.isBlock()) {
              socket.dataHandler(RecordParser.newDelimited("\n", new Handler<Buffer>() {
                public void handle(Buffer buff) {
                  result.set(buff.toString());
                  client.close();
                  latch.countDown();
                }
              }));
              command.write(socket, null);
            } else {
              command.write(socket, new SimpleHandler() {
                public void handle() {
                  client.close();
                  latch.countDown();
                }
              });
            }
          }
        });
      }
    });
    while (true) {
      try {
        if (!latch.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out while sending command");
        }
        break;
      } catch (InterruptedException e) {
        //Ignore
      }
    }

    return result.get();
  }

}
