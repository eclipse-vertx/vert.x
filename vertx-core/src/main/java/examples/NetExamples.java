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
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Created by tim on 19/01/15.
 */
public class NetExamples {

  public void example1(Vertx vertx) {

    NetServer server = vertx.createNetServer();
  }

  public void example2(Vertx vertx) {

    TcpServerConfig config = new TcpServerConfig()
      .setPort(4321);

    NetServer server = vertx.createNetServer(config);
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

    TcpClientConfig config = new TcpClientConfig()
      .setConnectTimeout(Duration.ofSeconds(10));

    NetClient client = vertx.createNetClient(config);
  }

  public void example15(Vertx vertx) {

    TcpClientConfig options = new TcpClientConfig()
      .setConnectTimeout(Duration.ofSeconds(10));
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

    TcpClientConfig options = new TcpClientConfig().
      setReconnectAttempts(10).
      setReconnectInterval(Duration.ofMillis(500));

    NetClient client = vertx.createNetClient(options);
  }

  public void exampleNetworkActivityLoggingOnServer(Vertx vertx) {

    TcpServerConfig options = new TcpServerConfig()
      .setNetworkLogging(new NetworkLogging());

    NetServer server = vertx.createNetServer(options);
  }

  public void exampleNetworkActivityLoggingFormat(Vertx vertx) {

    TcpServerConfig options = new TcpServerConfig()
      .setNetworkLogging(new NetworkLogging()
        .setDataFormat(ByteBufFormat.SIMPLE));

    NetServer server = vertx.createNetServer(options);
  }

  public void exampleNetworkActivityLoggingOnClient(Vertx vertx) {

    TcpClientConfig options = new TcpClientConfig()
      .setNetworkLogging(new NetworkLogging());

    NetClient client = vertx.createNetClient(options);
  }

  public void sslServerConfiguration(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setKeyCertOptions(
        new JksOptions().
          setPath("/path/to/your/server-keystore.jks").
          setPassword("password-of-your-keystore")
      );

    TcpServerConfig config = new TcpServerConfig()
      .setSsl(true);

    NetServer server = vertx.createNetServer(config, sslOptions);
  }

  public void example29(Vertx vertx) {
    TcpClientConfig config = new TcpClientConfig()
      .setSsl(true);
    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustAll(true);
    NetClient client = vertx.createNetClient(config, sslOptions);
  }

  public void sslClientConfiguration(Vertx vertx) {
    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustOptions(new JksOptions().
        setPath("/path/to/your/truststore.jks").
        setPassword("password-of-your-truststore")
      );

    TcpClientConfig config = new TcpClientConfig()
      .setSsl(true);

    NetClient client = vertx.createNetClient(config, sslOptions);
  }

  public void sslClientSocketConfiguration(Vertx vertx, int port, String host) {
    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustOptions(new JksOptions().
        setPath("/path/to/your/truststore.jks").
        setPassword("password-of-your-truststore")
      );

    TcpClientConfig config = new TcpClientConfig();

    NetClient client = vertx.createNetClient(config, sslOptions);

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
    TcpServerConfig options = new TcpServerConfig().
      setSsl(true);

    // Use JDK SSL engine explicitly
    options = new TcpServerConfig().
      setSsl(true).
      setSslEngineOptions(new JdkSSLEngineOptions());

    // Use OpenSSL engine
    options = new TcpServerConfig().
      setSsl(true).
      setSslEngineOptions(new OpenSSLEngineOptions());
  }

  public void example46(Vertx vertx, String verificationAlgorithm, TrustOptions trustOptions) {
    TcpClientConfig config = new TcpClientConfig().
      setSsl(true);

    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustOptions(trustOptions)
      .setHostnameVerificationAlgorithm(verificationAlgorithm);

    NetClient client = vertx.createNetClient(config, sslOptions);
  }

  public void example47(Vertx vertx) {
    TcpClientConfig config = new TcpClientConfig()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5)
        .setHost("localhost").setPort(1080)
        .setUsername("username").setPassword("secret"));
    NetClient client = vertx.createNetClient(config);
  }

  public void nonProxyHosts(Vertx vertx) {

    TcpClientConfig config = new TcpClientConfig()
      .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5)
        .setHost("localhost").setPort(1080)
        .setUsername("username").setPassword("secret"))
      .addNonProxyHost("*.foo.com")
      .addNonProxyHost("localhost");
    NetClient client = vertx.createNetClient(config);
  }

  public void example51(Vertx vertx) {
    TcpServerConfig config = new TcpServerConfig().setUseProxyProtocol(true);
    NetServer server = vertx.createNetServer(config);
    server.connectHandler(so -> {
      // Print the actual client address provided by the HA proxy protocol instead of the proxy address
      System.out.println(so.remoteAddress());

      // Print the address of the proxy
      System.out.println(so.localAddress());
    });
  }

  public void useSNIInClient(Vertx vertx, JksOptions trustOptions) {

    NetClient client = vertx.createNetClient(
      new TcpClientConfig().setSsl(true),
      new ClientSSLOptions().setTrustOptions(trustOptions)
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
    TcpServerConfig config = new TcpServerConfig()
      .setHost("localhost")
      .setPort(1234)
      .setTrafficShapingOptions(new TrafficShapingOptions()
        .setInboundGlobalBandwidth(64 * 1024)
        .setOutboundGlobalBandwidth(128 * 1024));

    NetServer server = vertx.createNetServer(config);
  }

  public void dynamicallyUpdateTrafficShapingForNetServer(Vertx vertx) {
    TcpServerConfig config = new TcpServerConfig()
      .setHost("localhost")
      .setPort(1234)
      .setTrafficShapingOptions(new TrafficShapingOptions()
        .setInboundGlobalBandwidth(64 * 1024)
        .setOutboundGlobalBandwidth(128 * 1024));

    NetServer server = vertx.createNetServer(config);

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
