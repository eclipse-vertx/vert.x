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
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.Pipe;
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

  public void pipe01(Vertx vertx) {
    NetServer server = vertx.createNetServer(
      new NetServerOptions().setPort(1234).setHost("localhost")
    );
    server.connectHandler(sock -> {
      sock.pipeTo(sock);
    }).listen();
  }

  public void pipe02(NetServer server) {
    server.connectHandler(sock -> {

      // Pipe the socket providing an handler to be notified of the result
      sock.pipeTo(sock, ar -> {
        if (ar.succeeded()) {
          System.out.println("Pipe succeeded");
        } else {
          System.out.println("Pipe failed");
        }
      });
    }).listen();
  }

  public void pipe03(NetServer server, FileSystem fs) {
    server.connectHandler(sock -> {

      // Create a pipe that to use asynchronously
      Pipe<Buffer> pipe = sock.pipe();

      // Open a destination file
      fs.open("/path/to/file", new OpenOptions(), ar -> {
        if (ar.succeeded()) {
          AsyncFile file = ar.result();

          // Pipe the socket to the file and close the file at the end
          pipe.to(file);
        } else {
          sock.close();
        }
      });
    }).listen();
  }

  public void pipe04(Vertx vertx, FileSystem fs) {
    vertx.createHttpServer()
      .requestHandler(request -> {

        // Create a pipe that to use asynchronously
        Pipe<Buffer> pipe = request.pipe();

        // Open a destination file
        fs.open("/path/to/file", new OpenOptions(), ar -> {
          if (ar.succeeded()) {
            AsyncFile file = ar.result();

            // Pipe the socket to the file and close the file at the end
            pipe.to(file);
          } else {
            // Close the pipe and resume the request, the body buffers will be discarded
            pipe.close();

            // Send an error response
            request.response().setStatusCode(500).end();
          }
        });
      }).listen(8080);
  }
}
