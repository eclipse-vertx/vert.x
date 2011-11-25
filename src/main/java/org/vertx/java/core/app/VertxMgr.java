package org.vertx.java.core.app;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxMgr {

  private static final Logger log = Logger.getLogger(VertxMgr.class);

  public static void main(String[] args) {
    new VertxMgr(args);
  }

  private VertxMgr(String[] sargs) {
    Args args = new Args(sargs);

    int port = args.getPort();
    port = port == -1 ? SocketDeployer.DEFAULT_PORT: port;

    if (sargs.length == 0) {
      displayHelp();
    } else {

      if (sargs[0].equalsIgnoreCase("start")) {
        System.out.println("Started vert.x server");
        new AppManager(port).start();
      } else if (sargs[0].equalsIgnoreCase("stop")) {
        sendCommand(port, "stop\n", false);
        System.out.println("Stopped vert.x server");
      } else if (sargs[0].equalsIgnoreCase("deploy")) {
        /*
        Deploy syntax:

        deploy -<java|ruby|groovy|js> -name <name> -main <main> -cp <classpath> -instances <instances>

        type is mandatory
        name is optional, system will generate one if not provided
        main is mandatory
        cp is mandatory
        instances is optional, defaults to number of cores on server
         */

        String type = "java";
        String flag = args.map.get("-ruby");
        if (flag != null) {
          type = "ruby";
        }


        String name = args.map.get("-name");
        if (name == null) {
          name = "app-" + UUID.randomUUID().toString();
        }

        String main = args.map.get("-main");
        String cp = args.map.get("-cp");

        if (main == null || cp == null) {
          displayDeploySyntax();
          return;
        }

        String sinstances = args.map.get("-instances");
        int instances;
        if (sinstances != null) {
          try {
            instances = Integer.parseInt(sinstances);

            if (instances != -1 && instances < 1) {
              System.err.println("Invalid number of instances");
              displayDeploySyntax();
              return;
            }
          } catch (NumberFormatException e) {
            displayDeploySyntax();
            return;
          }
        } else {
          instances = -1;
        }

        String[] parts;
        if (cp.contains(":")) {
          parts = cp.split(":");
        } else {
          parts = new String[] { cp };
        }
        int index = 0;
        StringBuffer urls = new StringBuffer();
        for (String part: parts) {
          File f = new File(part);
          urls.append(f.getAbsolutePath());
//          try {
//            #URL url = f.toURI().toURL();
//            #urls.append(url.toString());
//
//          } catch (MalformedURLException e) {
//            System.err.println("Invalid classpath: " + cp);
//            return;
//          }
          if (index != parts.length - 1) {
            urls.append(':');
          }
          index++;
        }

        System.out.println("Deploying application name: " + name + " instances: " + instances);
        String command = "deploy " + type + " " + name + " "  + main + " " + urls.toString() + " " + instances + "\n";
        String res = sendCommand(port, command, true);
        System.out.println(res);

        System.out.println("Command:" + command);


      } else if (sargs[0].equalsIgnoreCase("undeploy")) {
        String name = args.map.get("-name");
        if (name == null) {
          displayUndeploySyntax();
          return;
        }
        System.out.println("Undeploying application: " + name);
        String command = "undeploy " + name + "\n";
        String res = sendCommand(port, command, true);
        System.out.println(res);
      } else {
        displayHelp();
      }
    }
  }

  private void displayHelp() {
    System.out.println("vertx command help");
    System.out.println("------------------");
    System.out.println("");
    displayStartSyntax();
    System.out.println("------------------");
    System.out.println("");
    displayStopSyntax();
    System.out.println("------------------");
    System.out.println("");
    displayDeploySyntax();
    System.out.println("------------------");
    System.out.println("");
    displayUndeploySyntax();
  }

  private void displayStopSyntax() {
    System.out.println("Stop a vert.x server");
    System.out.println("vertx stop -port <port>");
    System.out.println("<port> - the port to connect to the server at. Defaults to 25571");
  }

  private void displayStartSyntax() {
    System.out.println("Start a vert.x server");
    System.out.println("vertx start -port <port>");
    System.out.println("<port> - the port the server will listen at. Defaults to 25571");
  }

  private void displayDeploySyntax() {
    System.out.println("Deploy an application");
    System.out.println("vertx deploy -[java|ruby|groovy|js] -name <name> -main <main> -instances <instances> -port <port>");
    System.out.println("");
    System.out.println("-[java|ruby|groovy|js] depending on the language of the application");
    System.out.println("<name> - unique name of the application. If this ommitted the server will generate a name");
    System.out.println("<main> - main class or script for the application");
    System.out.println("<instances> - number of instances of the application to start. Must be > 0 or -1.");
    System.out.println("              if -1 then instances will be default to number of cores on the server");
    System.out.println("<port> - the port to connect to the server at. Defaults to 25571");
  }

  private void displayUndeploySyntax() {
    System.out.println("Undeploy an application");
    System.out.println("vertx undeploy -name <name> -port <port>");
    System.out.println("");
    System.out.println("<name> - unique name of the application.");
    System.out.println("<port> - the port to connect to the server at. Defaults to 25571");
  }

  private String sendCommand(final int port, final String command, final boolean block) {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<String> result = new AtomicReference<>();
    VertxInternal.instance.go(new Runnable() {
      public void run() {
        final NetClient client = new NetClient();
        client.connect(port, "localhost", new Handler<NetSocket>() {
          public void handle(NetSocket socket) {
            if (block) {
              socket.dataHandler(RecordParser.newDelimited("\n", new Handler<Buffer>() {
                public void handle(Buffer buff) {
                  result.set(buff.toString());
                  client.close();
                  latch.countDown();
                }
              }));
              socket.write(command);
            } else {
              socket.write(command, new SimpleHandler() {
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
