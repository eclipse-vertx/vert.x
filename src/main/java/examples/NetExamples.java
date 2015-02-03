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

  // SSL/TLS server key/cert

  public void example17(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().setSsl(true).setKeyStoreOptions(
        new JksOptions().
            setPath("/path/to/your/server-keystore.jks").
            setPassword("password-of-your-keystore")
    );
    NetServer server = vertx.createNetServer(options);
  }

  public void example18(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-keystore.jks");
    JksOptions jksOptions = new JksOptions().
        setValue(myKeyStoreAsABuffer).
        setPassword("password-of-your-keystore");
    NetServerOptions options = new NetServerOptions().
        setSsl(true).
        setKeyStoreOptions(jksOptions);
    NetServer server = vertx.createNetServer(options);
  }

  public void example19(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().setSsl(true).setPfxKeyCertOptions(
        new PfxOptions().
            setPath("/path/to/your/server-keystore.pfx").
            setPassword("password-of-your-keystore")
    );
    NetServer server = vertx.createNetServer(options);
  }

  public void example20(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-keystore.pfx");
    PfxOptions pfxOptions = new PfxOptions().
        setValue(myKeyStoreAsABuffer).
        setPassword("password-of-your-keystore");
    NetServerOptions options = new NetServerOptions().
        setSsl(true).
        setPfxKeyCertOptions(pfxOptions);
    NetServer server = vertx.createNetServer(options);
  }

  public void example21(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().setSsl(true).setPemKeyCertOptions(
        new PemKeyCertOptions().
            setKeyPath("/path/to/your/server-key.pem").
            setCertPath("/path/to/your/server-cert.pem")
    );
    NetServer server = vertx.createNetServer(options);
  }

  public void example22(Vertx vertx) {
    Buffer myKeyAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-key.pem");
    Buffer myCertAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-cert.pem");
    PemKeyCertOptions pemOptions = new PemKeyCertOptions().
        setKeyValue(myKeyAsABuffer).
        setCertValue(myCertAsABuffer);
    NetServerOptions options = new NetServerOptions().
        setSsl(true).
        setPemKeyCertOptions(pemOptions);
    NetServer server = vertx.createNetServer(options);
  }

  // SSL/TLS server trust

  public void example23(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().
        setSsl(true).
        setClientAuthRequired(true).
        setTrustStoreOptions(
            new JksOptions().
                setPath("/path/to/your/truststore.jks").
                setPassword("password-of-your-truststore")
        );
    NetServer server = vertx.createNetServer(options);
  }

  public void example24(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.jks");
    NetServerOptions options = new NetServerOptions().
        setSsl(true).
        setClientAuthRequired(true).
        setTrustStoreOptions(
            new JksOptions().
                setValue(myTrustStoreAsABuffer).
                setPassword("password-of-your-truststore")
        );
    NetServer server = vertx.createNetServer(options);
  }

  public void example25(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().
        setSsl(true).
        setClientAuthRequired(true).
        setPfxTrustOptions(
            new PfxOptions().
                setPath("/path/to/your/truststore.pfx").
                setPassword("password-of-your-truststore")
        );
    NetServer server = vertx.createNetServer(options);
  }

  public void example26(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.pfx");
    NetServerOptions options = new NetServerOptions().
        setSsl(true).
        setClientAuthRequired(true).
        setPfxTrustOptions(
            new PfxOptions().
                setValue(myTrustStoreAsABuffer).
                setPassword("password-of-your-truststore")
        );
    NetServer server = vertx.createNetServer(options);
  }

  public void example27(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().
        setSsl(true).
        setClientAuthRequired(true).
        setPemTrustOptions(
            new PemTrustOptions().
                addCertPath("/path/to/your/server-ca.pem")
        );
    NetServer server = vertx.createNetServer(options);
  }

  public void example28(Vertx vertx) {
    Buffer myCaAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-ca.pfx");
    NetServerOptions options = new NetServerOptions().
        setSsl(true).
        setClientAuthRequired(true).
        setPemTrustOptions(
            new PemTrustOptions().
                addCertValue(myCaAsABuffer)
        );
    NetServer server = vertx.createNetServer(options);
  }

  // SSL/TLS client trust all

  public void example29(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setTrustAll(true);
    NetClient client = vertx.createNetClient(options);
  }

  // SSL/TLS client trust

  public void example30(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setTrustStoreOptions(
            new JksOptions().
                setPath("/path/to/your/truststore.jks").
                setPassword("password-of-your-truststore")
        );
    NetClient client = vertx.createNetClient(options);
  }

  public void example31(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.jks");
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setTrustStoreOptions(
            new JksOptions().
                setValue(myTrustStoreAsABuffer).
                setPassword("password-of-your-truststore")
        );
    NetClient client = vertx.createNetClient(options);
  }

  public void example32(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setPfxTrustOptions(
            new PfxOptions().
                setPath("/path/to/your/truststore.pfx").
                setPassword("password-of-your-truststore")
        );
    NetClient client = vertx.createNetClient(options);
  }

  public void example33(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.pfx");
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setPfxTrustOptions(
            new PfxOptions().
                setValue(myTrustStoreAsABuffer).
                setPassword("password-of-your-truststore")
        );
    NetClient client = vertx.createNetClient(options);
  }

  public void example34(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setPemTrustOptions(
            new PemTrustOptions().
                addCertPath("/path/to/your/ca-cert.pem")
        );
    NetClient client = vertx.createNetClient(options);
  }

  public void example35(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/ca-cert.pem");
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setPemTrustOptions(
            new PemTrustOptions().
                addCertValue(myTrustStoreAsABuffer)
        );
    NetClient client = vertx.createNetClient(options);
  }

  // SSL/TLS client key/cert

  public void example36(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().setSsl(true).setKeyStoreOptions(
        new JksOptions().
            setPath("/path/to/your/client-keystore.jks").
            setPassword("password-of-your-keystore")
    );
    NetClient client = vertx.createNetClient(options);
  }

  public void example37(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-keystore.jks");
    JksOptions jksOptions = new JksOptions().
        setValue(myKeyStoreAsABuffer).
        setPassword("password-of-your-keystore");
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setKeyStoreOptions(jksOptions);
    NetClient client = vertx.createNetClient(options);
  }

  public void example38(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().setSsl(true).setPfxKeyCertOptions(
        new PfxOptions().
            setPath("/path/to/your/client-keystore.pfx").
            setPassword("password-of-your-keystore")
    );
    NetClient client = vertx.createNetClient(options);
  }

  public void example39(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-keystore.pfx");
    PfxOptions pfxOptions = new PfxOptions().
        setValue(myKeyStoreAsABuffer).
        setPassword("password-of-your-keystore");
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setPfxKeyCertOptions(pfxOptions);
    NetClient client = vertx.createNetClient(options);
  }

  public void example40(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().setSsl(true).setPemKeyCertOptions(
        new PemKeyCertOptions().
            setKeyPath("/path/to/your/client-key.pem").
            setCertPath("/path/to/your/client-cert.pem")
    );
    NetClient client = vertx.createNetClient(options);
  }

  public void example41(Vertx vertx) {
    Buffer myKeyAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-key.pem");
    Buffer myCertAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-cert.pem");
    PemKeyCertOptions pemOptions = new PemKeyCertOptions().
        setKeyValue(myKeyAsABuffer).
        setCertValue(myCertAsABuffer);
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setPemKeyCertOptions(pemOptions);
    NetClient client = vertx.createNetClient(options);
  }

  public void example42(Vertx vertx, JksOptions trustOptions) {
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setTrustStoreOptions(trustOptions).
        addCrlPath("/path/to/your/crl.pem");
    NetClient client = vertx.createNetClient(options);
  }

  public void example43(Vertx vertx, JksOptions trustOptions) {
    Buffer myCrlAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/crl.pem");
    NetClientOptions options = new NetClientOptions().
        setSsl(true).
        setTrustStoreOptions(trustOptions).
        addCrlValue(myCrlAsABuffer);
    NetClient client = vertx.createNetClient(options);
  }
}
