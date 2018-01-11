/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package examples;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.Pump;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class StreamsExamples {

  public void pump1(Vertx vertx) {
    NetServer server = vertx.createNetServer(
        new NetServerOptions().setPort(1234).setHost("localhost")
    );
    server.connectHandler(sock -> {
      sock.handler(buffer -> {
        // Write the data straight back
        sock.write(buffer);
      });
    }).listen();
  }

  public void pump2(Vertx vertx) {
    NetServer server = vertx.createNetServer(
        new NetServerOptions().setPort(1234).setHost("localhost")
    );
    server.connectHandler(sock -> {
      sock.handler(buffer -> {
        if (!sock.writeQueueFull()) {
          sock.write(buffer);
        }
      });

    }).listen();
  }

  public void pump3(Vertx vertx) {
    NetServer server = vertx.createNetServer(
        new NetServerOptions().setPort(1234).setHost("localhost")
    );
    server.connectHandler(sock -> {
      sock.handler(buffer -> {
        sock.write(buffer);
        if (sock.writeQueueFull()) {
          sock.pause();
        }
      });
    }).listen();
  }

  public void pump4(Vertx vertx) {
    NetServer server = vertx.createNetServer(
        new NetServerOptions().setPort(1234).setHost("localhost")
    );
    server.connectHandler(sock -> {
      sock.handler(buffer -> {
        sock.write(buffer);
        if (sock.writeQueueFull()) {
          sock.pause();
          sock.drainHandler(done -> {
            sock.resume();
          });
        }
      });
    }).listen();
  }

  public void pump5(Vertx vertx) {
    NetServer server = vertx.createNetServer(
        new NetServerOptions().setPort(1234).setHost("localhost")
    );
    server.connectHandler(sock -> {
      Pump.pump(sock, sock).start();
    }).listen();
  }
}
