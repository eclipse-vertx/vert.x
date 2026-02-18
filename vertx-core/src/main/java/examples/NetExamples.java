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
import io.vertx.core.net.*;

import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

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

    NetServer server = vertx.createNetServer();
    server
      .listen(0, "localhost")
      .onComplete(res -> {
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

  public void serverShutdown(NetServer server) {
    server
      .shutdown()
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public void serverShutdownWithAmountOfTime(NetServer server) {
    server
      .shutdown(60, TimeUnit.SECONDS)
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public void example9(NetServer server) {

    server
      .close()
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  private static Buffer closeFrame() {
    return null;
  }

  private static Future<?> closeFrameHandler(NetSocket so) {
    return null;
  }

  public void shutdownHandler(NetSocket socket) {
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

  public void example9_1(NetSocket socket) {

    socket.closeHandler(v -> {
      System.out.println("The socket has been closed");
    });
  }

  public void example10(NetSocket socket) {

    socket
      .sendFile("myfile.dat")
      .onSuccess(v -> System.out.println("File successfully sent"))
      .onFailure(err -> System.out.println("Could not send file: " + err.getMessage()));
  }

  public void example11(Vertx vertx) {

    class MyVerticle extends VerticleBase {

      NetServer server;

      @Override
      public Future<?> start() {
        server = vertx.createNetServer();
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

    NetClient client = vertx.createNetClient();
  }

  public void example14(Vertx vertx) {

    NetClientOptions options = new NetClientOptions().setConnectTimeout(10000);
    NetClient client = vertx.createNetClient(options);
  }

  public void example15(Vertx vertx) {

    NetClientOptions options = new NetClientOptions().setConnectTimeout(10000);
    NetClient client = vertx.createNetClient(options);
    client
      .connect(4321, "localhost")
      .onComplete(res -> {
        if (res.succeeded()) {
          System.out.println("Connected!");
          NetSocket socket = res.result();
        } else {
          System.out.println("Failed to connect: " + res.cause().getMessage());
        }
      });
  }

  public void example16(Vertx vertx) {

    NetClientOptions options = new NetClientOptions().
      setReconnectAttempts(10).
      setReconnectInterval(500);

    NetClient client = vertx.createNetClient(options);
  }

  public void exampleNetworkActivityLoggingOnServer(Vertx vertx) {

    NetServerOptions options = new NetServerOptions().setLogActivity(true);

    NetServer server = vertx.createNetServer(options);
  }

  public void exampleNetworkActivityLoggingFormat(Vertx vertx) {

    NetServerOptions options = new NetServerOptions()
      .setLogActivity(true)
      .setActivityLogDataFormat(ByteBufFormat.SIMPLE);

    NetServer server = vertx.createNetServer(options);
  }

  public void exampleNetworkActivityLoggingOnClient(Vertx vertx) {

    NetClientOptions options = new NetClientOptions().setLogActivity(true);

    NetClient client = vertx.createNetClient(options);
  }

  public void sslServerConfiguration(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setKeyCertOptions(
        new JksOptions().
          setPath("/path/to/your/server-keystore.jks").
          setPassword("password-of-your-keystore")
      );

    NetServerOptions options = new NetServerOptions()
      .setSsl(true)
      .setSslOptions(sslOptions);

    NetServer server = vertx.createNetServer(options);
  }

  public void example29(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().
      setSsl(true).
      setTrustAll(true);
    NetClient client = vertx.createNetClient(options);
  }

  public void sslClientConfiguration(Vertx vertx) {
    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustOptions(new JksOptions().
        setPath("/path/to/your/truststore.jks").
        setPassword("password-of-your-truststore")
      );

    NetClientOptions options = new NetClientOptions()
      .setSsl(true)
      .setSslOptions(sslOptions);

    NetClient client = vertx.createNetClient(options);
  }

  public void sslClientSocketConfiguration(Vertx vertx, int port, String host) {
    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustOptions(new JksOptions().
        setPath("/path/to/your/truststore.jks").
        setPassword("password-of-your-truststore")
      );

    NetClientOptions options = new NetClientOptions().setSslOptions(sslOptions);

    NetClient client = vertx.createNetClient(options);

    Future<NetSocket> future = client.connect(new ConnectOptions()
      .setHost(host)
      .setPort(port)
      .setSsl(true)
    );
  }

  public void sslClientSocketConfiguration2(Vertx vertx, int port, String host) {
    NetClient client = vertx.createNetClient();

    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustOptions(new JksOptions().
        setPath("/path/to/your/truststore.jks").
        setPassword("password-of-your-truststore")
      );

    Future<NetSocket> future = client.connect(new ConnectOptions()
      .setHost(host)
      .setPort(port)
      .setSsl(true)
      .setSslOptions(sslOptions)
    );
  }

  public void upgradeToSSL(NetSocket socket, SSLOptions sslOptions) {
    // Use the default SSL options of the client or the server
    socket.upgradeToSsl()
      .onSuccess(v -> {
        // Upgrade worked
      }).onFailure(err -> {
        // Upgrade failed
      });

    // Use the specified SSL options
    socket.upgradeToSsl(sslOptions)
      .onSuccess(v -> {
        // Upgrade worked
      }).onFailure(err -> {
        // Upgrade failed
      });
  }

  public void updateSSLOptions(HttpServer server) {
    Future<Boolean> fut = server.updateSSLOptions(new ServerSSLOptions()
      .setKeyCertOptions(
        new JksOptions()
          .setPath("/path/to/your/server-keystore.jks").
          setPassword("password-of-your-keystore")));
  }

  public void exampleSSLEngine(Vertx vertx, JksOptions keyStoreOptions) {

    // Use JDK SSL engine
    NetServerOptions options = new NetServerOptions().
      setSsl(true).
      setKeyCertOptions(keyStoreOptions);

    // Use JDK SSL engine explicitly
    options = new NetServerOptions().
      setSsl(true).
      setKeyCertOptions(keyStoreOptions).
      setSslEngineOptions(new JdkSSLEngineOptions());

    // Use OpenSSL engine
    options = new NetServerOptions().
      setSsl(true).
      setKeyCertOptions(keyStoreOptions).
      setSslEngineOptions(new OpenSSLEngineOptions());
  }

  public void example46(Vertx vertx, String verificationAlgorithm, ClientSSLOptions sslOptions) {
    NetClientOptions options = new NetClientOptions().
      setSsl(true).
      setSslOptions(sslOptions).
      setHostnameVerificationAlgorithm(verificationAlgorithm);

    NetClient client = vertx.createNetClient(options);
  }

  public void example47(Vertx vertx) {
    NetClientOptions options = new NetClientOptions()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5)
        .setHost("localhost").setPort(1080)
        .setUsername("username").setPassword("secret"));
    NetClient client = vertx.createNetClient(options);
  }

  public void nonProxyHosts(Vertx vertx) {

    NetClientOptions options = new NetClientOptions()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5)
        .setHost("localhost").setPort(1080)
        .setUsername("username").setPassword("secret"))
      .addNonProxyHost("*.foo.com")
      .addNonProxyHost("localhost");
    NetClient client = vertx.createNetClient(options);
  }

  public void example49() {
    NetClientOptions clientOptions = new NetClientOptions()
      .setSsl(true)
      .setTrustAll(true);
  }

  public void example51(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().setUseProxyProtocol(true);
    NetServer server = vertx.createNetServer(options);
    server.connectHandler(so -> {
      // Print the actual client address provided by the HA proxy protocol instead of the proxy address
      System.out.println(so.remoteAddress());

      // Print the address of the proxy
      System.out.println(so.localAddress());
    });
  }

  public void configureSNIServer(Vertx vertx) {
    JksOptions keyCertOptions = new JksOptions().setPath("keystore.jks").setPassword("wibble");

    NetServer netServer = vertx.createNetServer(new NetServerOptions()
        .setKeyCertOptions(keyCertOptions)
        .setSsl(true)
        .setSni(true)
    );
  }

  public void configureSNIServerWithPems(Vertx vertx) {
    PemKeyCertOptions keyCertOptions = new PemKeyCertOptions()
        .setKeyPaths(Arrays.asList("default-key.pem", "host1-key.pem", "etc..."))
        .setCertPaths(Arrays.asList("default-cert.pem", "host2-key.pem", "etc...")
        );

    NetServer netServer = vertx.createNetServer(new NetServerOptions()
        .setKeyCertOptions(keyCertOptions)
        .setSsl(true)
        .setSni(true)
    );
  }

  public void useSNIInClient(Vertx vertx, JksOptions trustOptions) {

    NetClient client = vertx.createNetClient(new NetClientOptions()
        .setTrustOptions(trustOptions)
        .setSsl(true)
    );

    // Connect to 'localhost' and present 'server.name' server name
    client
      .connect(1234, "localhost", "server.name")
      .onComplete(res -> {
        if (res.succeeded()) {
          System.out.println("Connected!");
          NetSocket socket = res.result();
        } else {
          System.out.println("Failed to connect: " + res.cause().getMessage());
        }
      });
  }

  public void configureTrafficShapingForNetServer(Vertx vertx) {
    NetServerOptions options = new NetServerOptions()
      .setHost("localhost")
      .setPort(1234)
      .setTrafficShapingOptions(new TrafficShapingOptions()
        .setInboundGlobalBandwidth(64 * 1024)
        .setOutboundGlobalBandwidth(128 * 1024));

    NetServer server = vertx.createNetServer(options);
  }

  public void dynamicallyUpdateTrafficShapingForNetServer(Vertx vertx) {
    NetServerOptions options = new NetServerOptions()
      .setHost("localhost")
      .setPort(1234)
      .setTrafficShapingOptions(new TrafficShapingOptions()
        .setInboundGlobalBandwidth(64 * 1024)
        .setOutboundGlobalBandwidth(128 * 1024));

    NetServer server = vertx.createNetServer(options);

    TrafficShapingOptions update = new TrafficShapingOptions()
      .setInboundGlobalBandwidth(2 * 64 * 1024) // twice
      .setOutboundGlobalBandwidth(128 * 1024); // unchanged

    server
      .listen(1234, "localhost")
      // wait until traffic shaping handler is created for updates
      .onSuccess(v -> server.updateTrafficShapingOptions(update));
  }

  public void configureTrafficShapingForHttpServer(Vertx vertx) {
    HttpServerOptions options = new HttpServerOptions()
      .setHost("localhost")
      .setPort(1234)
      .setTrafficShapingOptions(new TrafficShapingOptions()
        .setInboundGlobalBandwidth(64 * 1024)
        .setOutboundGlobalBandwidth(128 * 1024));

    HttpServer server = vertx.createHttpServer(options);
  }


  public void dynamicallyUpdateTrafficShapingForHttpServer(Vertx vertx) {
    HttpServerOptions options = new HttpServerOptions()
      .setHost("localhost")
      .setPort(1234)
      .setTrafficShapingOptions(new TrafficShapingOptions()
        .setInboundGlobalBandwidth(64 * 1024)
        .setOutboundGlobalBandwidth(128 * 1024));

    HttpServer server = vertx.createHttpServer(options);
    TrafficShapingOptions update = new TrafficShapingOptions()
      .setInboundGlobalBandwidth(2 * 64 * 1024) // twice
      .setOutboundGlobalBandwidth(128 * 1024); // unchanged

    server
      .listen(1234, "localhost")
      // wait until traffic shaping handler is created for updates
      .onSuccess(v -> server.updateTrafficShapingOptions(update));
  }
}
