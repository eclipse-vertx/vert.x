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

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.java.deploy.impl.VerticleManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SocketDeployer {

  private static final Logger log = LoggerFactory.getLogger(SocketDeployer.class);

  public static final int DEFAULT_PORT = 25571;

  private volatile NetServer server;
  private final Vertx vertx;
  private final VerticleManager appManager;
  private final int port;

  public SocketDeployer(Vertx vertx, VerticleManager appManager, int port) {
    this.vertx = vertx;
    this.appManager = appManager;
    this.port = port == -1 ? DEFAULT_PORT: port;
  }

  public void start() {
    server = vertx.createNetServer().connectHandler(new Handler<NetSocket>() {
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

  public void stop(final Handler<Void> doneHandler) {
    if (doneHandler != null) {
      server.close(doneHandler);
    } else {
      server.close();
    }
    server = null;
  }
}
