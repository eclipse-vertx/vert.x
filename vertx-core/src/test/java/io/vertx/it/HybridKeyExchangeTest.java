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

package io.vertx.it;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.internal.tcnative.SSL;
import io.vertx.core.http.*;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.PqcEnforcementPolicy;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import io.vertx.test.http.HttpTestBase;
import org.junit.Assume;
import org.junit.Test;

import io.vertx.core.VertxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests PQC key exchange with OpenSSL.
 */
public class HybridKeyExchangeTest extends HttpTestBase {

  private static final Logger log = LoggerFactory.getLogger(HybridKeyExchangeTest.class);

  private static void assumeMlKemAvailable() {
    boolean available = OpenSsl.isAvailable();
    if (!available) {
      System.err.println("OpenSSL is not available: " + OpenSsl.unavailabilityCause());
      Assume.assumeTrue("OpenSSL is not available", false);
      return;
    }
    String version = OpenSsl.versionString();
    System.out.println("OpenSSL available: version=" + version + " (" + Long.toHexString(OpenSsl.version()) + ")");
    Assume.assumeFalse("BoringSSL does not support X25519MLKEM768", version.contains("BoringSSL"));
    boolean mlkem;
    try {
      SslContext ctx = SslContextBuilder.forClient()
        .sslProvider(SslProvider.OPENSSL)
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .build();
      SslHandler handler = ctx.newHandler(ByteBufAllocator.DEFAULT);
      try {
        long sslPtr = ((ReferenceCountedOpenSslEngine) handler.engine()).sslPointer();
        mlkem = SSL.setCurvesList(sslPtr, "X25519MLKEM768");
      } finally {
        handler.engine().closeOutbound();
      }
    } catch (Exception e) {
      System.err.println("Failed to probe X25519MLKEM768 support: " + e.getMessage());
      mlkem = false;
    }
    if (!mlkem) {
      System.err.println("X25519MLKEM768 is not supported by OpenSSL " + OpenSsl.versionString());
    }
    Assume.assumeTrue("X25519MLKEM768 not supported by OpenSSL " + OpenSsl.versionString(), mlkem);
  }

  @Test
  public void testStrictPolicyHandshake() throws Exception {
    assumeMlKemAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("strict-ok"));
    startServer(server);

    ClientSSLOptions pqcClientSsl = new ClientSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new OpenSSLEngineOptions())
      .with(pqcClientSsl)
      .build();

    var bodyBuffer = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/")
      .expecting(req -> req.connection().sslSession().getProtocol().equals("TLSv1.3"))
      .compose(HttpClientRequest::send)
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await();
    assertEquals("strict-ok", bodyBuffer.toString());
  }

  @Test
  public void testStrictPolicyRejectsNonPqcClient() throws Exception {
    assumeMlKemAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("should-not-reach"));
    startServer(server);

    ClientSSLOptions nonPqcClientSsl = new ClientSSLOptions()
      .setTrustAll(true);
    HttpClientAgent client2 = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new OpenSSLEngineOptions())
      .with(nonPqcClientSsl)
      .build();

    client2.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/")
      .compose(HttpClientRequest::send)
      .onComplete(ar -> {
        assertTrue(ar.failed());
        assertTrue(ar.cause() instanceof SSLHandshakeException);
        testComplete();
      });
    await();
  }

  @Test
  public void testClientNegotiatedPolicyAllowsNonPqcClient() throws Exception {
    assumeMlKemAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.CLIENT_NEGOTIATED)
      .setKeyExchangeGroups(List.of("X25519"))
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("client-negotiated-ok"));
    startServer(server);

    ClientSSLOptions nonPqcClientSsl = new ClientSSLOptions()
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(nonPqcClientSsl)
      .build();

    var bodyBuffer = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/")
      .compose(HttpClientRequest::send)
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await();
    assertEquals("client-negotiated-ok", bodyBuffer.toString());
  }

  @Test
  public void testClientNegotiatedPolicyWithPqcClient() throws Exception {
    assumeMlKemAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.CLIENT_NEGOTIATED)
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("pqc-negotiated-ok"));
    startServer(server);

    ClientSSLOptions pqcClientSsl = new ClientSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new OpenSSLEngineOptions())
      .with(pqcClientSsl)
      .build();

    var bodyBuffer = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/")
      .expecting(req -> req.connection().sslSession().getProtocol().equals("TLSv1.3"))
      .compose(HttpClientRequest::send)
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await();
    assertEquals("pqc-negotiated-ok", bodyBuffer.toString());
  }

  @Test
  public void testStrictPolicyMTLS() throws Exception {
    assumeMlKemAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setClientAuth(io.vertx.core.http.ClientAuth.REQUIRED)
      .setKeyCertOptions(Cert.SERVER_PEM_ROOT_CA.get())
      .setTrustOptions(Trust.SERVER_PEM_ROOT_CA.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> {
      assertTrue(req.isSSL());
      req.response().end("mtls-strict-ok");
    });
    startServer(server);

    ClientSSLOptions pqcClientSsl = new ClientSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setKeyCertOptions(Cert.CLIENT_PEM_ROOT_CA.get())
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new OpenSSLEngineOptions())
      .with(pqcClientSsl)
      .build();

    var buffer = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/")
      .expecting(req -> req.connection().sslSession().getProtocol().equals("TLSv1.3"))
      .compose(HttpClientRequest::send)
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await();

    assertEquals("mtls-strict-ok", buffer.toString());
  }

  @Test
  public void testStrictPolicyMTLSRejectsNonPqcClient() throws Exception {
    assumeMlKemAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setClientAuth(io.vertx.core.http.ClientAuth.REQUIRED)
      .setKeyCertOptions(Cert.SERVER_PEM_ROOT_CA.get())
      .setTrustOptions(Trust.SERVER_PEM_ROOT_CA.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("should-not-reach"));
    startServer(server);

    ClientSSLOptions nonPqcClientSsl = new ClientSSLOptions()
      .setKeyCertOptions(Cert.CLIENT_PEM_ROOT_CA.get())
      .setTrustAll(true);
    HttpClientAgent client2 = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new OpenSSLEngineOptions())
      .with(nonPqcClientSsl)
      .build();

    client2.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/")
      .compose(HttpClientRequest::send)
      .onComplete(ar -> {
        assertTrue(ar.failed());
        assertTrue(ar.cause() instanceof SSLHandshakeException);
        testComplete();
      });
    await();
  }

  @Test
  public void testStrictPolicyFailsServerStartWhenJdkPqcNotAvailable() throws Exception {
    Assume.assumeFalse("JDK PQC is available, skipping", JdkSSLEngineOptions.isPqcAvailable());
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    try {
      server = vertx.httpServerBuilder()
        .with(new HttpServerConfig(new HttpServerOptions()
          .setPort(DEFAULT_HTTPS_PORT)
          .setHost(DEFAULT_HTTPS_HOST)))
        .with(new JdkSSLEngineOptions())
        .with(serverSslOptions)
        .build();
      server.requestHandler(req -> req.response().end("should-not-reach"));
      server.listen().await();
      fail("Server should have failed to start");
    } catch (VertxException e) {
      assertTrue(e.getMessage().contains("X25519MLKEM768"));
      assertTrue(e.getMessage().contains("does not support it"));
    }
  }

  @Test
  public void testStrictPolicyFailsClientStartWhenJdkPqcNotAvailable() throws Exception {
    Assume.assumeFalse("JDK PQC is available, skipping", JdkSSLEngineOptions.isPqcAvailable());
    ClientSSLOptions clientSsl = new ClientSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setTrustAll(true);

    try {
      client = vertx.httpClientBuilder()
        .with(new HttpClientOptions().setSsl(true))
        .with(new JdkSSLEngineOptions())
        .with(clientSsl)
        .build();
      fail("Client should have failed to build");
    } catch (VertxException e) {
      assertTrue(e.getMessage().contains("X25519MLKEM768"));
      assertTrue(e.getMessage().contains("does not support it"));
    }
  }

  @Test
  public void testStrictPolicyWithSNI() throws Exception {
    assumeMlKemAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setSni(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("sni-strict-ok"));
    startServer(server);

    ClientSSLOptions pqcClientSsl = new ClientSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new OpenSSLEngineOptions())
      .with(pqcClientSsl)
      .build();

    var body = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/")
      .compose(HttpClientRequest::send)
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await();
    assertEquals("sni-strict-ok", body.toString());
  }

  @Test
  public void testRelaxedPolicyWithCustomGroups() throws Exception {
    assumeMlKemAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.RELAXED)
      .setKeyExchangeGroups(List.of("X25519MLKEM768"))
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("relaxed-custom-ok"));
    startServer(server);

    ClientSSLOptions clientSsl = new ClientSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.RELAXED)
      .setKeyExchangeGroups(List.of("X25519MLKEM768"))
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new OpenSSLEngineOptions())
      .with(clientSsl)
      .build();

    var body = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/")
      .compose(HttpClientRequest::send)
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await();
    assertEquals("relaxed-custom-ok", body.toString());
  }

  @Test
  public void testRelaxedPolicyDefaultGroups() throws Exception {
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("relaxed-default-ok"));
    startServer(server);

    ClientSSLOptions clientSsl = new ClientSSLOptions()
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(clientSsl)
      .build();

    var body = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/")
      .compose(HttpClientRequest::send)
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await();
    assertEquals("relaxed-default-ok", body.toString());
  }

  @Test
  public void testStrictPolicyWithRawNettySocket() throws Exception {
    assumeMlKemAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("strict-ok"));
    startServer(server);

    SslContext sslContext = SslContextBuilder.forClient()
      .sslProvider(SslProvider.OPENSSL)
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .build();

    CompletableFuture<Integer> negotiatedGroup = new CompletableFuture<>();

    EventLoopGroup group = new NioEventLoopGroup();
    try {
      Bootstrap bootstrap = new Bootstrap()
        .group(group)
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) {
            SslHandler sslHandler = sslContext.newHandler(ch.alloc(),
              DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT);

            ReferenceCountedOpenSslEngine engine =
              (ReferenceCountedOpenSslEngine) sslHandler.engine();
            SSL.setCurvesList(engine.sslPointer(), "X25519MLKEM768");

            ch.pipeline().addLast("server-hello-interceptor",
              new ServerHelloGroupExtractor(negotiatedGroup));
            ch.pipeline().addLast("ssl", sslHandler);

            sslHandler.handshakeFuture().addListener(future -> {
              if (!future.isSuccess()) {
                negotiatedGroup.completeExceptionally(future.cause());
              }
            });
          }
        });

      Channel ch = bootstrap.connect(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT)
        .sync().channel();

      int groupId = negotiatedGroup.get(10, TimeUnit.SECONDS);
      // 0x11ec = 4588 = X25519MLKEM768 see https://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml
      assertEquals(0x11ec, groupId);

      ch.close().sync();
    } finally {
      group.shutdownGracefully();
    }
  }

  @Test
  public void testStrictPolicyMTLSWithRawNettySocket() throws Exception {
    assumeMlKemAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setClientAuth(io.vertx.core.http.ClientAuth.REQUIRED)
      .setKeyCertOptions(Cert.SERVER_PEM_ROOT_CA.get())
      .setTrustOptions(Trust.SERVER_PEM_ROOT_CA.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> {
      assertTrue(req.isSSL());
      req.response().end("mtls-strict-ok");
    });
    startServer(server);

    SslContext sslContext = SslContextBuilder.forClient()
      .sslProvider(SslProvider.OPENSSL)
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .keyManager(
        getClass().getClassLoader().getResourceAsStream("tls/client-cert-root-ca.pem"),
        getClass().getClassLoader().getResourceAsStream("tls/client-key.pem"))
      .build();

    CompletableFuture<Integer> negotiatedGroup = new CompletableFuture<>();

    EventLoopGroup group = new NioEventLoopGroup();
    try {
      Bootstrap bootstrap = new Bootstrap()
        .group(group)
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) {
            SslHandler sslHandler = sslContext.newHandler(ch.alloc(),
              DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT);

            ReferenceCountedOpenSslEngine engine =
              (ReferenceCountedOpenSslEngine) sslHandler.engine();
            SSL.setCurvesList(engine.sslPointer(), "X25519MLKEM768");

            ch.pipeline().addLast("server-hello-interceptor",
              new ServerHelloGroupExtractor(negotiatedGroup));
            ch.pipeline().addLast("ssl", sslHandler);

            sslHandler.handshakeFuture().addListener(future -> {
              if (!future.isSuccess()) {
                negotiatedGroup.completeExceptionally(future.cause());
              }
            });
          }
        });

      Channel ch = bootstrap.connect(DEFAULT_HTTPS_HOST, DEFAULT_HTTPS_PORT)
        .sync().channel();

      int groupId = negotiatedGroup.get(10, TimeUnit.SECONDS);
      // 0x11ec = 4588 = X25519MLKEM768 see https://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml
      assertEquals(0x11ec, groupId);

      ch.close().sync();
    } finally {
      group.shutdownGracefully();
      testComplete();
    }
  }


  private static void assumeJdkPqcAvailable() {
    Assume.assumeTrue("JDK PQC is not available", JdkSSLEngineOptions.isPqcAvailable());
  }

  @Test
  public void testStrictPolicyHandshakeJdk() throws Exception {
    assumeJdkPqcAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new JdkSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("jdk-strict-ok"));
    startServer(server);

    ClientSSLOptions pqcClientSsl = new ClientSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new JdkSSLEngineOptions())
      .with(pqcClientSsl)
      .build();

    var bodyBuffer = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/")
      .expecting(req -> req.connection().sslSession().getProtocol().equals("TLSv1.3"))
      .compose(HttpClientRequest::send)
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await();
    assertEquals("jdk-strict-ok", bodyBuffer.toString());
  }

  @Test
  public void testStrictPolicyMTLSJdk() throws Exception {
    assumeJdkPqcAvailable();
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setClientAuth(io.vertx.core.http.ClientAuth.REQUIRED)
      .setKeyCertOptions(Cert.SERVER_PEM_ROOT_CA.get())
      .setTrustOptions(Trust.SERVER_PEM_ROOT_CA.get());

    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new JdkSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> {
      assertTrue(req.isSSL());
      req.response().end("jdk-mtls-strict-ok");
    });
    startServer(server);

    ClientSSLOptions pqcClientSsl = new ClientSSLOptions()
      .setPqcEnforcementPolicy(PqcEnforcementPolicy.STRICT)
      .setKeyCertOptions(Cert.CLIENT_PEM_ROOT_CA.get())
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new JdkSSLEngineOptions())
      .with(pqcClientSsl)
      .build();

    var buffer = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/")
      .expecting(req -> req.connection().sslSession().getProtocol().equals("TLSv1.3"))
      .compose(HttpClientRequest::send)
      .expecting(HttpResponseExpectation.SC_OK)
      .compose(HttpClientResponse::body)
      .await();
    assertEquals("jdk-mtls-strict-ok", buffer.toString());
  }

  static class ServerHelloGroupExtractor extends ChannelInboundHandlerAdapter {

    private static final int HANDSHAKE_CONTENT_TYPE = 0x16;
    private static final int SERVER_HELLO = 0x02;
    private static final int KEY_SHARE_EXTENSION = 0x0033;

    private final CompletableFuture<Integer> result;

    ServerHelloGroupExtractor(CompletableFuture<Integer> result) {
      this.result = result;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof ByteBuf && !result.isDone()) {
        ByteBuf buf = (ByteBuf) msg;
        int readerIndex = buf.readerIndex();
        try {
          parseServerHello(buf);
        } catch (Exception e) {
          // Not a ServerHello or not parseable yet, ignore
        } finally {
          buf.readerIndex(readerIndex);
        }
      }
      super.channelRead(ctx, msg);
    }

    private void parseServerHello(ByteBuf buf) {
      if (buf.readableBytes() < 5) return;

      int contentType = buf.readUnsignedByte();
      if (contentType != HANDSHAKE_CONTENT_TYPE) return;

      buf.skipBytes(2); // protocol version
      int recordLength = buf.readUnsignedShort();
      if (buf.readableBytes() < recordLength) return;

      int handshakeType = buf.readUnsignedByte();
      if (handshakeType != SERVER_HELLO) return;

      buf.skipBytes(3);  // handshake length
      buf.skipBytes(2);  // server version (0x0303)
      buf.skipBytes(32); // random

      int sessionIdLen = buf.readUnsignedByte();
      buf.skipBytes(sessionIdLen); // session id

      buf.skipBytes(2); // cipher suite
      buf.skipBytes(1); // compression method

      if (buf.readableBytes() < 2) return;
      int extensionsLength = buf.readUnsignedShort();

      int extensionsEnd = buf.readerIndex() + extensionsLength;
      while (buf.readerIndex() < extensionsEnd && buf.readableBytes() >= 4) {
        int extType = buf.readUnsignedShort();
        int extLen = buf.readUnsignedShort();

        if (extType == KEY_SHARE_EXTENSION && extLen >= 2) {
          int groupId = buf.readUnsignedShort();
          result.complete(groupId);
          return;
        }
        buf.skipBytes(extLen);
      }
    }
  }
}
