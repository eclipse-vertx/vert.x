/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package examples;

import io.vertx.core.AsyncResult;
import io.vertx.core.AsyncResultHandler;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DatagramExamples {

  public void example1(Vertx vertx) {
    DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
  }

  public void example2(Vertx vertx) {
    DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
    Buffer buffer = Buffer.buffer("content");
    // Send a Buffer
    socket.send(buffer, 1234, "10.0.0.1", asyncResult -> {
      System.out.println("Send succeeded? " + asyncResult.succeeded());
    });
    // Send a String
    socket.send("A string used as content", 1234, "10.0.0.1", asyncResult -> {
      System.out.println("Send succeeded? " + asyncResult.succeeded());
    });
  }

  public void example3(Vertx vertx) {
    DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
    socket.listen(1234, "0.0.0.0", asyncResult -> {
      if (asyncResult.succeeded()) {
        socket.handler(packet -> {
          // Do something with the packet
        });
      } else {
        System.out.println("Listen failed" + asyncResult.cause());
      }
    });
  }

  public void example4(Vertx vertx) {
    DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
    Buffer buffer = Buffer.buffer("content");
    // Send a Buffer to a multicast address
    socket.send(buffer, 1234, "230.0.0.1", asyncResult -> {
      System.out.println("Send succeeded? " + asyncResult.succeeded());
    });
  }

  public void example5(Vertx vertx) {
    DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
    socket.listen(1234, "0.0.0.0", asyncResult -> {
      if (asyncResult.succeeded()) {
        socket.handler(packet -> {
          // Do something with the packet
        });

        // join the multicast group
        socket.listenMulticastGroup("230.0.0.1", asyncResult2 -> {
            System.out.println("Listen succeeded? " + asyncResult2.succeeded());
        });
      } else {
        System.out.println("Listen failed" + asyncResult.cause());
      }
    });
  }

  public void example6(Vertx vertx) {
    DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
    socket.listen(1234, "0.0.0.0", asyncResult -> {
        if (asyncResult.succeeded()) {
          socket.handler(packet -> {
            // Do something with the packet
          });

          // join the multicast group
          socket.listenMulticastGroup("230.0.0.1", asyncResult2 -> {
              if (asyncResult2.succeeded()) {
                // will now receive packets for group

                // do some work

                socket.unlistenMulticastGroup("230.0.0.1", asyncResult3 -> {
                  System.out.println("Unlisten succeeded? " + asyncResult3.succeeded());
                });
              } else {
                System.out.println("Listen failed" + asyncResult2.cause());
              }
          });
        } else {
          System.out.println("Listen failed" + asyncResult.cause());
        }
    });
  }

  public void example7(Vertx vertx) {
    DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());

    // Some code

    // This would block packets which are send from 10.0.0.2
    socket.blockMulticastGroup("230.0.0.1", "10.0.0.2", asyncResult -> {
      System.out.println("block succeeded? " + asyncResult.succeeded());
    });
  }
}
