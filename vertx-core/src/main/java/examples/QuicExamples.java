/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
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
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.LogConfig;
import io.vertx.core.net.QuicClient;
import io.vertx.core.net.QuicClientConfig;
import io.vertx.core.net.QuicConnectOptions;
import io.vertx.core.net.QuicConnection;
import io.vertx.core.net.QuicServer;
import io.vertx.core.net.QuicServerConfig;
import io.vertx.core.net.QuicStream;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.StreamChannel;
import io.vertx.core.net.TrustOptions;

import java.time.Duration;
import java.util.List;

public class QuicExamples {

  private static final ServerSSLOptions sslOptions = new ServerSSLOptions();
  public static final String APPLICATION_PROTOCOL = "my-protocol";

  public void defaultQuicServer(Vertx vertx) {
    ServerSSLOptions sslOptions = new ServerSSLOptions()
      .setKeyCertOptions(
        new JksOptions().
          setPath("/path/to/your/server-keystore.jks").
          setPassword("password-of-your-keystore")
      )
      .setApplicationLayerProtocols(List.of(APPLICATION_PROTOCOL));

    QuicServer server = vertx.createQuicServer(sslOptions);
  }

  public void configurationOfAQuicServer(Vertx vertx) {

    QuicServerConfig config = new QuicServerConfig()
      .setPort(4321);

    QuicServer server = vertx.createQuicServer(config, sslOptions);
  }

  public void startingAQuicServer(Vertx vertx) {

    QuicServer server = vertx.createQuicServer(sslOptions);
    server.listen();
  }

  public void startingAQuicServerWithHostAndPort(Vertx vertx) {

    QuicServer server = vertx.createQuicServer(sslOptions);
    server.listen(1234, "localhost");
  }

  public void gettingNotifiedWhenStartingAQuicServer(Vertx vertx) {

    QuicServer server = vertx.createQuicServer(sslOptions);
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

  public void startingAQuicServerOnARandomPort(Vertx vertx) {

    QuicServer server = vertx.createQuicServer(sslOptions);
    server
      .listen(0, "localhost")
      .onComplete(res -> {
        if (res.succeeded()) {
          SocketAddress bindAddr = res.result();
          System.out.println("Server is now listening on actual port: " + bindAddr.port());
        } else {
          System.out.println("Failed to bind!");
        }
      });
  }

  public void gettingNotifiedOfIncomingConnections(Vertx vertx) {

    QuicServer server = vertx.createQuicServer(sslOptions);
    server.connectHandler(connection -> {
      // Handle the connection in here
    });
  }

  public void gettingNotifiedOfIncomingStreams(Vertx vertx) {

    QuicServer server = vertx.createQuicServer(sslOptions);
    server.connectHandler(connection -> {
      // Handle the connection in here
      connection.streamHandler(stream -> {
        // Handle streams here
      });
    });
  }

  public void readingDataFromAStream(Vertx vertx, QuicConnection connection) {

    connection.streamHandler(stream -> {
      stream.handler(buffer -> {
        System.out.println("I received some bytes: " + buffer.length());
      });
    });
  }

  public void writingDataToAStream(StreamChannel stream) {

    // Write a buffer
    Buffer buffer = Buffer.buffer().appendFloat(12.34f).appendInt(123);
    stream.write(buffer);

    // Write a string in UTF-8 encoding
    stream.write("some data");

    // Write a string using the specified encoding
    stream.write("some data", "UTF-16");
  }

  public void gettingNotifiedOnStreamClose(StreamChannel stream) {

    stream.closeHandler(v -> {
      System.out.println("The stream has been closed");
    });
  }

  public void sendingAFile(QuicStream stream) {

    stream
      .sendFile("myfile.dat")
      .onSuccess(v -> System.out.println("File successfully sent"))
      .onFailure(err -> System.out.println("Could not send file: " + err.getMessage()));
  }

  public void gracefullyShuttingDownAServer(QuicServer server) {
    server
      .shutdown()
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public void gettingNotifiedOnShutdown(QuicConnection connection) {

    connection.streamHandler(stream -> {
      stream.shutdownHandler(duration -> {
        stream
          // Write close frame
          .write(closeFrame())
          // Wait until we receive the remote close frame
          .compose(success -> closeFrameHandler(stream))
          // Close the socket
          .eventually(() -> stream.close());
      });
    });

    connection.shutdownHandler(duration -> {
      // Connection is shutting down
    });
  }

  public void gettingNotifiedOnStreamShutdown(QuicStream stream) {
    stream.shutdownHandler(v -> {
      stream
        // Write close frame
        .write(closeFrame())
        // Wait until we receive the remote close frame
        .compose(success -> closeFrameHandler(stream))
        // Close the socket
        .eventually(() -> stream.close());
    });
  }

  public void gracefullyShuttingDownAServerWithTimeout(QuicServer server) {
    server
      .shutdown(Duration.ofSeconds(60))
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public void gracefullyShuttingDownAConnection(QuicConnection server) {
    server
      .shutdown()
      .onSuccess(res -> {
        System.out.println("Connection is now closed");
      });
  }

  public void closingAServer(QuicServer server) {

    server
      .close()
      .onSuccess(res -> {
        System.out.println("Server is now closed");
      });
  }

  public void defaultQuicClient(Vertx vertx) {
    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustOptions(new JksOptions().
        setPath("/path/to/your/truststore.jks").
        setPassword("password-of-your-truststore")
      )
      .setApplicationLayerProtocols(List.of(APPLICATION_PROTOCOL));

    QuicClient client = vertx.createQuicClient(sslOptions);
  }

  public void configurationOfAQuicClient(Vertx vertx, ClientSSLOptions sslOptions) {

    QuicClientConfig config = new QuicClientConfig()
      .setConnectTimeout(Duration.ofSeconds(10));

    QuicClient client = vertx.createQuicClient(config, sslOptions);
  }

  public void connectingToAServer(Vertx vertx, ClientSSLOptions sslOptions) {

    QuicClientConfig options = new QuicClientConfig()
      .setConnectTimeout(Duration.ofSeconds(10));
    QuicClient client = vertx.createQuicClient(options, sslOptions);
    client
      .connect(4321, "localhost")
      .onComplete(res -> {
        if (res.succeeded()) {
          System.out.println("Connected!");
          QuicConnection connection = res.result();
        } else {
          System.out.println("Failed to connect: " + res.cause().getMessage());
        }
      });
  }

  public void openingAStream(QuicConnection connection) {

    connection
      .openStream()
      .onComplete(res -> {
        if (res.succeeded()) {
          QuicStream stream = res.result();
        } else {
          System.out.println("Failed to open: " + res.cause().getMessage());
        }
      });
  }

  public void configurationOfQuicClientReconnect(Vertx vertx, ClientSSLOptions sslOptions) {

    QuicClientConfig options = new QuicClientConfig().
      setReconnectAttempts(10).
      setReconnectInterval(Duration.ofMillis(500));

    QuicClient client = vertx.createQuicClient(options, sslOptions);
  }

  public void configurationOfQuicServerLogging(Vertx vertx) {

    QuicServerConfig options = new QuicServerConfig()
      .setLogConfig(new LogConfig()
        .setEnabled(true));

    QuicServer server = vertx.createQuicServer(options, sslOptions);
  }

  public void configurationOfQuicServerLoggingFormat(Vertx vertx) {

    QuicServerConfig options = new QuicServerConfig()
      .setLogConfig(new LogConfig()
        .setEnabled(true)
        .setDataFormat(ByteBufFormat.SIMPLE));

    QuicServer server = vertx.createQuicServer(options, sslOptions);
  }

  public void configurationOfQuicClientLogging(Vertx vertx, ClientSSLOptions sslOptions) {

    QuicClientConfig options = new QuicClientConfig()
      .setLogConfig(new LogConfig()
        .setEnabled(true));

    QuicClient client = vertx.createQuicClient(options, sslOptions);
  }

  public void configurationOfAQuicClientConnection(QuicClient client, int port, String host) {
    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustOptions(new JksOptions().
        setPath("/path/to/your/truststore.jks").
        setPassword("password-of-your-truststore")
      )
      .setApplicationLayerProtocols(List.of(APPLICATION_PROTOCOL));

    Future<QuicConnection> future = client.connect(
      port,
      host,
      new QuicConnectOptions().setSslOptions(sslOptions)
    );
  }

  public void configurationOfQuicClientHostVerification(Vertx vertx, String verificationAlgorithm, TrustOptions trustOptions) {
    ClientSSLOptions sslOptions = new ClientSSLOptions()
      .setTrustOptions(trustOptions)
      .setApplicationLayerProtocols(List.of(APPLICATION_PROTOCOL))
      .setHostnameVerificationAlgorithm(verificationAlgorithm);
  }

  public void updateSslOptionsOfAQuicServer(QuicServer server) {
    Future<Boolean> fut = server.updateSSLOptions(new ServerSSLOptions()
      .setKeyCertOptions(
        new JksOptions()
          .setPath("/path/to/your/server-keystore.jks").
          setPassword("password-of-your-keystore"))
      .setApplicationLayerProtocols(List.of(APPLICATION_PROTOCOL)));
  }

  private static Buffer closeFrame() {
    return null;
  }

  private static Future<?> closeFrameHandler(StreamChannel so) {
    return null;
  }
}
