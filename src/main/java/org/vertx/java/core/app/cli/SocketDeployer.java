package org.vertx.java.core.app.cli;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.AppManager;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;

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
            final RecordParser parser = RecordParser.newFixed(4, null);
            Handler<Buffer> handler = new Handler<Buffer>() {
              int size = -1;
              public void handle(Buffer buff) {
                log.info("got buff, size " + size);

                log.info("buffer length is " + buff.length());
                if (size == -1) {
                  size = buff.getInt(0);
                  log.info("act size is " + size);
                  parser.fixedSizeMode(size);
                } else {

                  try {
                    VertxCommand cmd = VertxCommand.read(buff);
                    log.info("created vertxcommand " + cmd);
                    cmd.execute(appManager);
                    socket.write("OK\n");
                  } catch (Exception e) {
                    log.error("Failed to execute command", e);
                    socket.write("ERR: " + e.getMessage() + "\n");
                  }
                  parser.fixedSizeMode(4);
                  size = -1;
                }
              }
            };
            parser.setOutput(handler);
            socket.dataHandler(parser);
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
}
