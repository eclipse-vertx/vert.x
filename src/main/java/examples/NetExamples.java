/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package examples;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.*;

/**
 * Created by tim on 19/01/15.
 */
public class NetExamples {

  public void example1(Vertx vertx) {

    NetServer server = vertx.createNetServer();
  }

  public void example2(Vertx vertx) {

    NetServerOptions options = new NetServerOptions().setPort(4321);
    NetServer server = vertx.createNetServer(options);
  }

  public void example3(Vertx vertx) {

    NetServer server = vertx.createNetServer();
    server.listen();
  }

  public void example4(Vertx vertx) {

    NetServer server = vertx.createNetServer();
    server.listen(1234, "localhost");
  }

  public void example5(Vertx vertx) {

    NetServer server = vertx.createNetServer();
    server.listen(1234, "localhost", res -> {
      if (res.succeeded()) {
        System.out.println("Server is now listening!");
      } else {
        System.out.println("Failed to bind!");
      }
    });
  }

  public void example5_1(Vertx vertx) {

    NetServer server = vertx.createNetServer();
    server.listen(0, "localhost", res -> {
      if (res.succeeded()) {
        System.out.println("Server is now listening on actual port: " + server.actualPort());
      } else {
        System.out.println("Failed to bind!");
      }
    });
  }

  public void example6(Vertx vertx) {

    NetServer server = vertx.createNetServer();
    server.connectHandler(socket -> {
      // Handle the connection in here
    });
  }

  public void example7(Vertx vertx) {

    NetServer server = vertx.createNetServer();
    server.connectHandler(socket -> {
      socket.handler(buffer -> {
        System.out.println("I received some bytes: " + buffer.length());
      });
    });
  }


  public void example8(NetSocket socket) {

    // Write a buffer
    Buffer buffer = Buffer.buffer().appendFloat(12.34f).appendInt(123);
    socket.write(buffer);

    // Write a string in UTF-8 encoding
    socket.write("some data");

    // Write a string using the specified encoding
    socket.write("some data", "UTF-16");


  }

  public void example9(NetServer server) {

    server.close(res -> {
      if (res.succeeded()) {
        System.out.println("Server is now closed");
      } else {
        System.out.println("close failed");
      }
    });
  }

  public void example9_1(NetSocket socket) {

    socket.closeHandler(v -> {
      System.out.println("The socket has been closed");
    });
  }

  public void example10(NetSocket socket) {

    socket.sendFile("myfile.dat");
  }

  public void example11(Vertx vertx) {

    // Create a few instances so we can utilise cores

    for (int i = 0; i < 10; i++) {
      NetServer server = vertx.createNetServer();
      server.connectHandler(socket -> {
        socket.handler(buffer -> {
          // Just echo back the data
          socket.write(buffer);
        });
      });
      server.listen(1234, "localhost");
    }
  }

  public void example12(Vertx vertx) {

    DeploymentOptions options = new DeploymentOptions().setInstances(10);
    vertx.deployVerticle("com.mycompany.MyVerticle", options);
  }

  public void example13(Vertx vertx) {

    NetClient client = vertx.createNetClient();
  }

  public void example14(Vertx vertx) {

    NetClientOptions options = new NetClientOptions().setConnectTimeout(10000);
    NetClient client = vertx.createNetClient(options);
  }

  public void example15(Vertx vertx) {

    NetClientOptions options = new NetClientOptions().setConnectTimeout(10000);
    NetClient client = vertx.createNetClient(options);
    client.connect(4321, "localhost", res -> {
      if (res.succeeded()) {
        System.out.println("Connected!");
        NetSocket socket = res.result();
      } else {
        System.out.println("Failed to connect: " + res.cause().getMessage());
      }
    });
  }

  public void example16(Vertx vertx) {

    NetClientOptions options = new NetClientOptions();
    options.setReconnectAttempts(10).setReconnectInterval(500);

    NetClient client = vertx.createNetClient(options);
  }

  public void example17(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().setSsl(true).setKeyStoreOptions(
        new JksOptions().
            setPath("/path/to/your/keystore.jks").
            setPassword("password-of-your-keystore")
    );
    NetServer server = vertx.createNetServer(options);
  }

  public void example18(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = readKeyStore();
    JksOptions jksOptions = new JksOptions().
        setValue(myKeyStoreAsABuffer).
        setPassword("password-of-your-keystore");
    NetServerOptions options = new NetServerOptions().
        setSsl(true).
        setKeyStoreOptions(jksOptions);
    NetServer server = vertx.createNetServer(options);
  }

  private Buffer readKeyStore() {
    return Buffer.buffer();
  }

}
