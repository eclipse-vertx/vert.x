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

import io.netty.handler.logging.ByteBufFormat;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyStoreOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.TcpClient;
import io.vertx.core.net.NetworkLogging;
import io.vertx.core.net.TcpSocket;
import io.vertx.core.net.TcpServer;
import io.vertx.core.net.TcpClient;
import io.vertx.core.net.TcpServerConfig;
import io.vertx.core.net.TcpClientConfig;
import io.vertx.core.net.TrafficShapingOptions;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Created by tim on 19/01/15.
 */
public class TcpExamples {

  public void example1(Vertx vertx) {

    TcpServer server = vertx.createTcpServer();
  }

  public void example2(Vertx vertx) {

    TcpServerConfig config = new TcpServerConfig().setPort(4321);
    TcpServer server = vertx.createTcpServer(config);
  }

  public void example3(Vertx vertx) {

    TcpServer server = vertx.createTcpServer();
    server.listen();
  }

  public void example4(Vertx vertx) {

    TcpServer server = vertx.createTcpServer();
    server.listen(1234, "localhost");
  }

  public void example5(Vertx vertx) {

    TcpServer server = vertx.createTcpServer();
    server
      .listen(1234, "localhost")
      .onComplete(res -> {
        if (res.succeeded()) {
          System.out.println("Server is now listening!");
        } else {
          System.out.println("Failed to bind!");
        }
      });
  }

  public void example5_1(Vertx vertx) {

    TcpServer server = vertx.createTcpServer();
    server
      .listen(0, "localhost")
      .onComplete(res -> {
        if (res.succeeded()) {
          System.out.println("Server is now listening on actual port: " + server.bindAddress().port());
        } else {
          System.out.println("Failed to bind!");
        }
      });
  }

  public void example6(Vertx vertx) {

    TcpServer server = vertx.createTcpServer();
    server.connectHandler(socket -> {
      // Handle the connection in here
    });
  }

  public void example7(Vertx vertx) {

    TcpServer server = vertx.createTcpServer();
    server.connectHandler(socket -> {
      socket.handler(buffer -> {
        System.out.println("I received some bytes: " + buffer.length());
      });
    });
  }


  public void example8(TcpSocket socket) {

    // Write a buffer
    Buffer buffer = Buffer.buffer().appendFloat(12.34f).appendInt(123);
    socket.write(buffer);

    // Write a string in UTF-8 encoding
    socket.write("some data");

    // Write a string using the specified encoding
    socket.write("some data", "UTF-16");


  }

  public void serverShutdown(TcpServer server) {
    server
      .shutdown()
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public void serverShutdownWithAmountOfTime(TcpServer server) {
    server
      .shutdown(Duration.ofSeconds(60))
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public void example9(TcpServer server) {

    server
      .close()
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  private static Buffer closeFrame() {
    return null;
  }

  private static Future<?> closeFrameHandler(TcpSocket so) {
    return null;
  }

  public void shutdownHandler(TcpSocket socket) {
    socket.shutdownHandler(v -> {
      socket
        // Write close frame
        .write(closeFrame())
        // Wait until we receive the remote close frame
        .compose(success -> closeFrameHandler(socket))
        // Close the socket
        .eventually(() -> socket.close());
    });
  }

  public void example9_1(TcpSocket socket) {

    socket.closeHandler(v -> {
      System.out.println("The socket has been closed");
    });
  }

  public void example10(TcpSocket socket) {

    socket.sendFile("myfile.dat");
  }

  public void example11(Vertx vertx) {

    class MyVerticle extends VerticleBase {

      TcpServer server;

      @Override
      public Future<?> start() {
        server = vertx.createTcpServer();
        server.connectHandler(socket -> {
          socket.handler(buffer -> {
            // Just echo back the data
            socket.write(buffer);
          });
        });
        return server.listen(1234, "localhost");
      }
    }

    // Create a few instances so we can utilise cores
    vertx.deployVerticle(MyVerticle.class, new DeploymentOptions().setInstances(10));
  }

  public void example13(Vertx vertx) {

    TcpClient client = vertx.createTcpClient();
  }

  public void example14(Vertx vertx) {

    TcpClientConfig config = new TcpClientConfig().setConnectTimeout(Duration.ofSeconds(10));
    TcpClient client = vertx.createTcpClient(config);
  }

  public void example15(Vertx vertx) {

    TcpClientConfig config = new TcpClientConfig().setConnectTimeout(Duration.ofSeconds(10));
    TcpClient client = vertx.createTcpClient(config);
    client
      .connect(4321, "localhost")
      .onComplete(res -> {
        if (res.succeeded()) {
          System.out.println("Connected!");
          TcpSocket socket = res.result();
        } else {
          System.out.println("Failed to connect: " + res.cause().getMessage());
        }
      });
  }

  public void example16(Vertx vertx) {

    TcpClientConfig config = new TcpClientConfig().
      setReconnectAttempts(10).
      setReconnectInterval(Duration.ofMillis(500));

    TcpClient client = vertx.createTcpClient(config);
  }

  public void exampleNetworkActivityLoggingOnServer(Vertx vertx) {

    TcpServerConfig config = new TcpServerConfig().setNetworkLogging(new NetworkLogging());

    TcpServer server = vertx.createTcpServer(config);
  }

  public void exampleNetworkActivityLoggingFormat(Vertx vertx) {

    TcpServerConfig config = new TcpServerConfig()
      .setNetworkLogging(new NetworkLogging()
        .setDataFormat(ByteBufFormat.SIMPLE));

    TcpServer server = vertx.createTcpServer(config);
  }

  public void exampleNetworkActivityLoggingOnClient(Vertx vertx) {

    TcpClientConfig config = new TcpClientConfig().setNetworkLogging(new NetworkLogging());

    TcpClient client = vertx.createTcpClient(config);
  }

  // SSL/TLS server key/cert

  public void example17(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions().setKeyCertOptions(
      new JksOptions().
        setPath("/path/to/your/server-keystore.jks").
        setPassword("password-of-your-keystore")
    );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void example18(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-keystore.jks");
    ServerSSLOptions sslOptions = new ServerSSLOptions().setKeyCertOptions(
      new JksOptions().
        setValue(myKeyStoreAsABuffer).
        setPassword("password-of-your-keystore")
    );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void example19(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions().setKeyCertOptions(
      new PfxOptions().
        setPath("/path/to/your/server-keystore.pfx").
        setPassword("password-of-your-keystore")
    );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void example20(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-keystore.pfx");
    ServerSSLOptions sslOptions = new ServerSSLOptions().setKeyCertOptions(
      new PfxOptions().
        setValue(myKeyStoreAsABuffer).
        setPassword("password-of-your-keystore")
    );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void example21(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions().setKeyCertOptions(
      new PemKeyCertOptions().
        setKeyPath("/path/to/your/server-key.pem").
        setCertPath("/path/to/your/server-cert.pem")
    );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void example22(Vertx vertx) {
    Buffer myKeyAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-key.pem");
    Buffer myCertAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-cert.pem");
    ServerSSLOptions sslOptions = new ServerSSLOptions().setKeyCertOptions(
      new PemKeyCertOptions().
        setKeyValue(myKeyAsABuffer).
        setCertValue(myCertAsABuffer)
    );
  }

  public void exampleBKS(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions().setKeyCertOptions(
      new KeyStoreOptions().
        setType("BKS").
        setPath("/path/to/your/server-keystore.bks").
        setPassword("password-of-your-keystore")
    );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  // SSL/TLS server trust

  public void example23(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setClientAuth(ClientAuth.REQUIRED)
      .setTrustOptions(new JksOptions().
        setPath("/path/to/your/truststore.jks").
        setPassword("password-of-your-truststore")
      );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void example24(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.jks");
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setClientAuth(ClientAuth.REQUIRED)
      .setTrustOptions(new JksOptions().
        setValue(myTrustStoreAsABuffer).
        setPassword("password-of-your-truststore")
      );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void example25(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setClientAuth(ClientAuth.REQUIRED)
      .setTrustOptions(new PfxOptions().
        setPath("/path/to/your/truststore.pfx").
        setPassword("password-of-your-truststore")
      );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void example26(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.pfx");
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setClientAuth(ClientAuth.REQUIRED)
      .setTrustOptions(new PfxOptions().
        setValue(myTrustStoreAsABuffer).
        setPassword("password-of-your-truststore")
      );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void example27(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setClientAuth(ClientAuth.REQUIRED)
      .setTrustOptions(new PemTrustOptions().
        addCertPath("/path/to/your/server-ca.pem")
      );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void example28(Vertx vertx) {
    Buffer myCaAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-ca.pfx");
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setClientAuth(ClientAuth.REQUIRED)
      .setTrustOptions(new PemTrustOptions().
        addCertValue(myCaAsABuffer)
      );
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  // SSL/TLS client trust all

  public void example29(Vertx vertx) {
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setTrustAll(true);
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  // SSL/TLS client trust

  public void example30(Vertx vertx) {
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setTrustOptions(
        new JksOptions().
          setPath("/path/to/your/truststore.jks").
          setPassword("password-of-your-truststore")
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example31(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.jks");
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setTrustOptions(
        new JksOptions().
          setValue(myTrustStoreAsABuffer).
          setPassword("password-of-your-truststore")
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example32(Vertx vertx) {
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setTrustOptions(
        new PfxOptions().
          setPath("/path/to/your/truststore.pfx").
          setPassword("password-of-your-truststore")
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example33(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/truststore.pfx");
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setTrustOptions(
        new PfxOptions().
          setValue(myTrustStoreAsABuffer).
          setPassword("password-of-your-truststore")
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example34(Vertx vertx) {
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setTrustOptions(
        new PemTrustOptions().
          addCertPath("/path/to/your/ca-cert.pem")
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example35(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/ca-cert.pem");
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setTrustOptions(
        new PemTrustOptions().
          addCertValue(myTrustStoreAsABuffer)
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  // SSL/TLS client key/cert

  public void example36(Vertx vertx) {
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setKeyCertOptions(new JksOptions().
        setPath("/path/to/your/client-keystore.jks").
        setPassword("password-of-your-keystore")
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example37(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-keystore.jks");
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setKeyCertOptions(new JksOptions().
        setValue(myKeyStoreAsABuffer).
        setPassword("password-of-your-keystore")
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example38(Vertx vertx) {
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setKeyCertOptions(new PfxOptions().
        setPath("/path/to/your/client-keystore.pfx").
        setPassword("password-of-your-keystore")
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example39(Vertx vertx) {
    Buffer myKeyStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-keystore.pfx");
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setKeyCertOptions(new PfxOptions().
        setValue(myKeyStoreAsABuffer).
        setPassword("password-of-your-keystore")
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example40(Vertx vertx) {
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setKeyCertOptions(new PemKeyCertOptions().
        setKeyPath("/path/to/your/client-key.pem").
        setCertPath("/path/to/your/client-cert.pem")
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example41(Vertx vertx) {
    Buffer myKeyAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-key.pem");
    Buffer myCertAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/client-cert.pem");
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setKeyCertOptions(new PemKeyCertOptions().
        setKeyValue(myKeyAsABuffer).
        setCertValue(myCertAsABuffer)
      );
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void updateSSLOptions(TcpServer server) {
    Future<Boolean> fut = server.updateSSLOptions(new ServerSSLOptions()
      .setKeyCertOptions(
        new JksOptions()
          .setPath("/path/to/your/server-keystore.jks").
          setPassword("password-of-your-keystore")));
  }

  public void example42(Vertx vertx, JksOptions trustOptions) {
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setTrustOptions(trustOptions).
      addCrlPath("/path/to/your/crl.pem");
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example43(Vertx vertx, JksOptions trustOptions) {
    Buffer myCrlAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/crl.pem");
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setTrustOptions(trustOptions).
      addCrlValue(myCrlAsABuffer);
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example44(Vertx vertx, JksOptions keyStoreOptions) {
    ServerSSLOptions sslOptions = new ServerSSLOptions().
      setKeyCertOptions(keyStoreOptions).
      addEnabledCipherSuite("ECDHE-RSA-AES128-GCM-SHA256").
      addEnabledCipherSuite("ECDHE-ECDSA-AES128-GCM-SHA256").
      addEnabledCipherSuite("ECDHE-RSA-AES256-GCM-SHA384").
      addEnabledCipherSuite("CDHE-ECDSA-AES256-GCM-SHA384");
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void addEnabledTLSPrococol(Vertx vertx, JksOptions keyStoreOptions) {
    ServerSSLOptions sslOptions = new ServerSSLOptions().
      setKeyCertOptions(keyStoreOptions).
      addEnabledSecureTransportProtocol("TLSv1.1");
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void removeEnabledTLSPrococol(Vertx vertx, JksOptions keyStoreOptions) {
    ServerSSLOptions sslOptions = new ServerSSLOptions().
      setKeyCertOptions(keyStoreOptions).
      removeEnabledSecureTransportProtocol("TLSv1.1");
    TcpServer server = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void exampleSSLEngine(Vertx vertx, JksOptions keyStoreOptions) {

    // Use JDK SSL engine
    TcpServerConfig config = new TcpServerConfig().
      setSsl(true);

    // Use JDK SSL engine explicitly
    config = new TcpServerConfig().
      setSsl(true).
      setSslEngineOptions(new JdkSSLEngineOptions());

    // Use OpenSSL engine
    config = new TcpServerConfig().
      setSsl(true).
      setSslEngineOptions(new OpenSSLEngineOptions());
  }

  public void example46(Vertx vertx, String verificationAlgorithm) {
    ClientSSLOptions sslOptions = new ClientSSLOptions().
      setHostnameVerificationAlgorithm(verificationAlgorithm);
    TcpClient client = vertx.createTcpClient(new TcpClientConfig().setSsl(true), sslOptions);
  }

  public void example47(Vertx vertx) {
    TcpClientConfig config = new TcpClientConfig()
      .setProxyOptions(new ProxyOptions()
        .setType(ProxyType.SOCKS5)
        .setHost("localhost")
        .setPort(1080)
        .setUsername("username")
        .setPassword("secret")
      );
    TcpClient client = vertx.createTcpClient(config);
  }

  public void nonProxyHosts(Vertx vertx) {
    TcpClientConfig config = new TcpClientConfig()
      .setProxyOptions(new ProxyOptions()
        .setType(ProxyType.SOCKS5)
        .setHost("localhost")
        .setPort(1080)
        .setUsername("username")
        .setPassword("secret"))
      .addNonProxyHost("*.foo.com")
      .addNonProxyHost("localhost");
    TcpClient client = vertx.createTcpClient(config);
  }

  public void example49() {
    ClientSSLOptions clientOptions = new ClientSSLOptions()
      .setTrustAll(true);
  }

  public void example51(Vertx vertx) {
    TcpServerConfig options = new TcpServerConfig().setUseProxyProtocol(true);
    TcpServer server = vertx.createTcpServer(options);
    server.connectHandler(so -> {
      // Print the actual client address provided by the HA proxy protocol instead of the proxy address
      System.out.println(so.remoteAddress());

      // Print the address of the proxy
      System.out.println(so.localAddress());
    });
  }

  public void configureSNIServer(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setKeyCertOptions(new JksOptions()
        .setPath("keystore.jks")
        .setPassword("wibble")
      ).setSni(true);
    TcpServer TcpServer = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void configureSNIServerWithPems(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setKeyCertOptions(new PemKeyCertOptions()
        .setKeyPaths(List.of("default-key.pem", "host1-key.pem", "etc..."))
        .setCertPaths(List.of("default-cert.pem", "host2-key.pem", "etc...")))
      .setSni(true);
    TcpServer TcpServer = vertx.createTcpServer(new TcpServerConfig().setSsl(true), sslOptions);
  }

  public void useSNIInClient(Vertx vertx, JksOptions trustOptions) {

    TcpClient client = vertx.createTcpClient(
      new TcpClientConfig().setSsl(true),
      new ClientSSLOptions().setTrustOptions(trustOptions)
    );

    // Connect to 'localhost' and present 'server.name' server name
    client
      .connect(1234, "localhost", "server.name")
      .onComplete(res -> {
        if (res.succeeded()) {
          System.out.println("Connected!");
          TcpSocket socket = res.result();
        } else {
          System.out.println("Failed to connect: " + res.cause().getMessage());
        }
      });
  }

  public void configureTrafficShapingForTcpServer(Vertx vertx) {
    TcpServerConfig config = new TcpServerConfig()
      .setHost("localhost")
      .setPort(1234)
      .setTrafficShapingOptions(new TrafficShapingOptions()
        .setInboundGlobalBandwidth(64 * 1024)
        .setOutboundGlobalBandwidth(128 * 1024));

    TcpServer server = vertx.createTcpServer(config);
  }

  public void dynamicallyUpdateTrafficShapingForTcpServer(Vertx vertx) {
    TcpServerConfig config = new TcpServerConfig()
      .setHost("localhost")
      .setPort(1234)
      .setTrafficShapingOptions(new TrafficShapingOptions()
        .setInboundGlobalBandwidth(64 * 1024)
        .setOutboundGlobalBandwidth(128 * 1024));

    TcpServer server = vertx.createTcpServer(config);

    TrafficShapingOptions update = new TrafficShapingOptions()
      .setInboundGlobalBandwidth(2 * 64 * 1024) // twice
      .setOutboundGlobalBandwidth(128 * 1024); // unchanged
    server
      .listen(1234, "localhost")
      // wait until traffic shaping handler is created for updates
      .onSuccess(v -> server.updateTrafficShapingOptions(update));
  }
}
