/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
    socket
      .send(buffer, 1234, "10.0.0.1")
      .onComplete(asyncResult -> System.out.println("Send succeeded? " + asyncResult.succeeded()));
    // Send a String
    socket
      .send("A string used as content", 1234, "10.0.0.1")
      .onComplete(asyncResult -> System.out.println("Send succeeded? " + asyncResult.succeeded()));
  }

  public void example3(Vertx vertx) {
    DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
    socket
      .handler(packet -> {
        // Do something with the packet
      })
      .listen(1234, "0.0.0.0")
      .onComplete(asyncResult -> System.out.println("Send succeeded? " + asyncResult.succeeded()));
    ;
  }

  public void example4(Vertx vertx) {
    DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
    Buffer buffer = Buffer.buffer("content");
    // Send a Buffer to a multicast address
    socket
      .send(buffer, 1234, "230.0.0.1")
      .onComplete(asyncResult -> System.out.println("Send succeeded? " + asyncResult.succeeded()));
  }

  public void example5(Vertx vertx) {
    DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
    socket
      .handler(packet -> {
        // Do something with the packet
      })
      .listen(1234, "0.0.0.0")
      .compose(v -> socket.listenMulticastGroup("230.0.0.1")) // join the multicast group
      .onComplete(asyncResult -> System.out.println("Listen succeeded? " + asyncResult.succeeded()));
  }

  public void example6(Vertx vertx) {
    DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
    socket
      .handler(packet -> {
        // Do something with the packet
      })
      .listen(1234, "0.0.0.0")
      .compose(v -> socket.listenMulticastGroup("230.0.0.1")) // join the multicast group
      .onComplete(asyncResult -> {
        if (asyncResult.succeeded()) {
          // will now receive packets for group

          // do some work

          socket.unlistenMulticastGroup("230.0.0.1").onComplete(asyncResult2 -> {
            System.out.println("Unlisten succeeded? " + asyncResult2.succeeded());
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
    socket
      .blockMulticastGroup("230.0.0.1", "10.0.0.2")
      .onComplete(asyncResult -> System.out.println("block succeeded? " + asyncResult.succeeded()));
  }
}
