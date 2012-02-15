package org.vertx.java.core.app.cli;

import org.vertx.java.core.Context;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.app.VerticleManager;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SocketDeployer {

  private static final Logger log = LoggerFactory.getLogger(SocketDeployer.class);

  public static final int DEFAULT_PORT = 25571;

  private Context serverContext;
  private volatile NetServer server;
  private final VerticleManager appManager;
  private final int port;

  public SocketDeployer(VerticleManager appManager, int port) {
    this.appManager = appManager;
    this.port = port == -1 ? DEFAULT_PORT: port;
  }

  public void start() {
    VertxInternal.instance.startOnEventLoop(new Runnable() {
      public void run() {
        serverContext= VertxInternal.instance.getContext();
        server = new NetServer().connectHandler(new Handler<NetSocket>() {
          public void handle(final NetSocket socket) {
            final RecordParser parser = RecordParser.newFixed(4, null);
            Handler<Buffer> handler = new Handler<Buffer>() {
              int size = -1;

              public void handle(Buffer buff) {
                if (size == -1) {
                  size = buff.getInt(0);
                  parser.fixedSizeMode(size);
                } else {
                  try {
                    VertxCommand cmd = VertxCommand.read(buff);
                    String res = cmd.execute(appManager);
                    socket.write("OK: " + res + "\n");
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

  public void stop(final Handler<Void> doneHandler) {
    serverContext.execute(new Runnable() {
      public void run() {
        if (doneHandler != null) {
          server.close(doneHandler);
        } else {
          server.close();
        }
        server = null;
      }
    });
  }
}
