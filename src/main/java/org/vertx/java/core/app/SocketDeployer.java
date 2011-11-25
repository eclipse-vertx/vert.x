package org.vertx.java.core.app;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SocketDeployer {

  private static final Logger log = Logger.getLogger(SocketDeployer.class);

  public static final int DEFAULT_PORT = 25571;

  private long serverContextID;
  private volatile NetServer server;
  private final AppManager appManager;
  private final int port;

  public SocketDeployer(AppManager appManager, int port) {
    this.appManager = appManager;
    this.port = port == -1 ? DEFAULT_PORT: port;
  }

  public void start() {
    VertxInternal.instance.go(new Runnable() {
      public void run() {
        serverContextID = Vertx.instance.getContextID();
        server = new NetServer().connectHandler(new Handler<NetSocket>() {
          public void handle(final NetSocket socket) {
            socket.dataHandler(RecordParser.newDelimited("\n", new Handler<Buffer>() {
              public void handle(Buffer buff) {
                String line = buff.toString();
                if (line.startsWith("deploy")) {
                  parseDeploy(socket, line);
                } else if (line.startsWith("undeploy")) {
                  parseUndeploy(socket, line);
                } else if (line.startsWith("stop")) {
                  appManager.stop();
                  log.info("Stopped vert.x server");
                } else {
                  sendError("Unrecognised command: " + line, socket);
                }
              }
            }));
          }
        }).listen(port, "localhost");
      }
    });
  }

  public void stop() {
    VertxInternal.instance.executeOnContext(serverContextID, new Runnable() {
      public void run() {
        VertxInternal.instance.setContextID(serverContextID);
        server.close();
        server = null;
      }
    });
  }

  private void sendError(String error, NetSocket socket) {
    socket.write("ERR: " + error + "\n");
  }

  private void parseDeploy(NetSocket socket, String line) {
    String[] parts = line.trim().split(" ");
    if (parts.length == 6) {
      String type = parts[1];
      String name = parts[2];
      String mainClass = parts[3];
      String urlString = parts[4];
      String sinstances = parts[5];
      try {
        int instances = Integer.parseInt(sinstances);
        String[] urlParts;
        log.info("urlstring is:" + urlString);
        if (urlString.contains(":")) {
          urlParts = urlString.split(":");
        } else {
          urlParts = new String[] { urlString };
        }
        URL[] urls = new URL[urlParts.length];
        int index = 0;
        log.info("There are " + urlParts.length + " parts");

        int c = 0;
        for (String urlPart: urlParts) {
          log.info("part " + c + ":" + urlPart);
        }


        for (String urlPart: urlParts) {
          try {
            log.info("url part is " + urlPart);
            if (!urlPart.endsWith(".jar") && !urlPart.endsWith(".zip") && !urlPart.endsWith("/")) {
              //It's a directory - need to add trailing slash
              urlPart += "/";
            }
            URL url = new URL("file://" + urlPart);
            urls[index++] = url;
          } catch (MalformedURLException e) {
            sendError("Malformed URL", socket);
            return;
          }
        }
        AppType appType;
        switch (type) {
          case "java":
            appType = AppType.JAVA;
            break;
          case "ruby":
            appType = AppType.RUBY;
            break;
          case "js":
            appType = AppType.JS;
            break;
          case "groovy":
            appType = AppType.GROOVY;
            break;
          default:
            sendError("Invalid type " + type, socket);
            return;
        }
        String error = appManager.deploy(name, appType, urls, mainClass, instances);
        if (error != null) {
          log.error(error);
          sendError(error, socket);
        } else {
          socket.write("OK\n");
        }
      } catch (NumberFormatException e) {
        log.error("Invalid number of instances: " + sinstances);
      }

    } else {
      sendError("Invalid syntax:" + line, socket);
    }
  }

  private void parseUndeploy(NetSocket socket, String line) {
    String[] parts = line.trim().split(" ");
    if (parts.length == 2) {
      String name = parts[1];
      String error = appManager.undeploy(name);
      if (error != null) {
        log.error(error);
        sendError(error, socket);
      } else {
        socket.write("OK\n");
      }
    } else {
      sendError("Invalid syntax:" + line, socket);
    }
  }
}
