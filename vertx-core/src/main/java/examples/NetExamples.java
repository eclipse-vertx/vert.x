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

    socket.sendFile("myfile.dat");
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

  // SSL/TLS server key/cert

  public void example17(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().setSsl(true).setKeyCertOptions(
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
      setKeyCertOptions(jksOptions);
    NetServer server = vertx.createNetServer(options);
  }

  public void example19(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().setSsl(true).setKeyCertOptions(
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
      setKeyCertOptions(pfxOptions);
    NetServer server = vertx.createNetServer(options);
  }

  public void example21(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().setSsl(true).setKeyCertOptions(
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
      setKeyCertOptions(pemOptions);
    NetServer server = vertx.createNetServer(options);
  }

  public void exampleBKS(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().setSsl(true).setKeyCertOptions(
      new KeyStoreOptions().
        setType("BKS").
        setPath("/path/to/your/server-keystore.bks").
        setPassword("password-of-your-keystore")
    );
    NetServer server = vertx.createNetServer(options);
  }

  // SSL/TLS server trust

  public void example23(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().
      setSsl(true).
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
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
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
        new JksOptions().
          setValue(myTrustStoreAsABuffer).
          setPassword("password-of-your-truststore")
      );
    NetServer server = vertx.createNetServer(options);
  }

  public void example25(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().
      setSsl(true).
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
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
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
        new PfxOptions().
          setValue(myTrustStoreAsABuffer).
          setPassword("password-of-your-truststore")
      );
    NetServer server = vertx.createNetServer(options);
  }

  public void example27(Vertx vertx) {
    NetServerOptions options = new NetServerOptions().
      setSsl(true).
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
        new PemTrustOptions().
          addCertPath("/path/to/your/server-ca.pem")
      );
    NetServer server = vertx.createNetServer(options);
  }

  public void example28(Vertx vertx) {
    Buffer myCaAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/server-ca.pfx");
    NetServerOptions options = new NetServerOptions().
      setSsl(true).
      setClientAuth(ClientAuth.REQUIRED).
      setTrustOptions(
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
      setTrustOptions(
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
      setTrustOptions(
        new JksOptions().
          setValue(myTrustStoreAsABuffer).
          setPassword("password-of-your-truststore")
      );
    NetClient client = vertx.createNetClient(options);
  }

  public void example32(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().
      setSsl(true).
      setTrustOptions(
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
      setTrustOptions(
        new PfxOptions().
          setValue(myTrustStoreAsABuffer).
          setPassword("password-of-your-truststore")
      );
    NetClient client = vertx.createNetClient(options);
  }

  public void example34(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().
      setSsl(true).
      setTrustOptions(
        new PemTrustOptions().
          addCertPath("/path/to/your/ca-cert.pem")
      );
    NetClient client = vertx.createNetClient(options);
  }

  public void example35(Vertx vertx) {
    Buffer myTrustStoreAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/ca-cert.pem");
    NetClientOptions options = new NetClientOptions().
      setSsl(true).
      setTrustOptions(
        new PemTrustOptions().
          addCertValue(myTrustStoreAsABuffer)
      );
    NetClient client = vertx.createNetClient(options);
  }

  // SSL/TLS client key/cert

  public void example36(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().setSsl(true).setKeyCertOptions(
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
      setKeyCertOptions(jksOptions);
    NetClient client = vertx.createNetClient(options);
  }

  public void example38(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().setSsl(true).setKeyCertOptions(
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
      setKeyCertOptions(pfxOptions);
    NetClient client = vertx.createNetClient(options);
  }

  public void example40(Vertx vertx) {
    NetClientOptions options = new NetClientOptions().setSsl(true).setKeyCertOptions(
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
      setKeyCertOptions(pemOptions);
    NetClient client = vertx.createNetClient(options);
  }

  public void updateSSLOptions(HttpServer server) {
    Future<Boolean> fut = server.updateSSLOptions(new ServerSSLOptions()
      .setKeyCertOptions(
        new JksOptions()
          .setPath("/path/to/your/server-keystore.jks").
          setPassword("password-of-your-keystore")));
  }

  public void example42(Vertx vertx, JksOptions trustOptions) {
    NetClientOptions options = new NetClientOptions().
      setSsl(true).
      setTrustOptions(trustOptions).
      addCrlPath("/path/to/your/crl.pem");
    NetClient client = vertx.createNetClient(options);
  }

  public void example43(Vertx vertx, JksOptions trustOptions) {
    Buffer myCrlAsABuffer = vertx.fileSystem().readFileBlocking("/path/to/your/crl.pem");
    NetClientOptions options = new NetClientOptions().
      setSsl(true).
      setTrustOptions(trustOptions).
      addCrlValue(myCrlAsABuffer);
    NetClient client = vertx.createNetClient(options);
  }

  public void example44(Vertx vertx, JksOptions keyStoreOptions) {
    NetServerOptions options = new NetServerOptions().
      setSsl(true).
      setKeyCertOptions(keyStoreOptions).
      addEnabledCipherSuite("ECDHE-RSA-AES128-GCM-SHA256").
      addEnabledCipherSuite("ECDHE-ECDSA-AES128-GCM-SHA256").
      addEnabledCipherSuite("ECDHE-RSA-AES256-GCM-SHA384").
      addEnabledCipherSuite("CDHE-ECDSA-AES256-GCM-SHA384");
    NetServer server = vertx.createNetServer(options);
  }

  public void addEnabledTLSPrococol(Vertx vertx, JksOptions keyStoreOptions) {
    NetServerOptions options = new NetServerOptions().
      setSsl(true).
      setKeyCertOptions(keyStoreOptions).
      addEnabledSecureTransportProtocol("TLSv1.1");
    NetServer server = vertx.createNetServer(options);
  }

  public void removeEnabledTLSPrococol(Vertx vertx, JksOptions keyStoreOptions) {
    NetServerOptions options = new NetServerOptions().
      setSsl(true).
      setKeyCertOptions(keyStoreOptions).
      removeEnabledSecureTransportProtocol("TLSv1.2");
    NetServer server = vertx.createNetServer(options);
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

  public void example46(Vertx vertx, String verificationAlgorithm) {
    NetClientOptions options = new NetClientOptions().
      setSsl(true).
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

  public void example48(Vertx vertx) throws CertificateException {
    SelfSignedCertificate certificate = SelfSignedCertificate.create();

    NetServerOptions serverOptions = new NetServerOptions()
      .setSsl(true)
      .setKeyCertOptions(certificate.keyCertOptions())
      .setTrustOptions(certificate.trustOptions());

    vertx.createNetServer(serverOptions)
      .connectHandler(socket -> socket.end(Buffer.buffer("Hello!")))
      .listen(1234, "localhost");

    NetClientOptions clientOptions = new NetClientOptions()
      .setSsl(true)
      .setKeyCertOptions(certificate.keyCertOptions())
      .setTrustOptions(certificate.trustOptions());

    NetClient client = vertx.createNetClient(clientOptions);
    client
      .connect(1234, "localhost")
      .onComplete(ar -> {
        if (ar.succeeded()) {
          ar.result().handler(buffer -> System.out.println(buffer));
        } else {
          System.err.println("Woops: " + ar.cause().getMessage());
        }
      });
  }

  public void example49() {
    NetClientOptions clientOptions = new NetClientOptions()
      .setSsl(true)
      .setTrustAll(true);
  }

  public void example50(Vertx vertx) throws CertificateException {
    SelfSignedCertificate certificate = SelfSignedCertificate.create();

    vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setKeyCertOptions(certificate.keyCertOptions())
      .setTrustOptions(certificate.trustOptions()))
      .requestHandler(req -> req.response().end("Hello!"))
      .listen(8080);
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
