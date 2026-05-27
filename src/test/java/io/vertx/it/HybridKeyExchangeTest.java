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
import io.vertx.core.Future;
import io.vertx.core.http.*;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Assume;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests hybrid key exchange (X25519MLKEM768) with OpenSSL.
 */
public class HybridKeyExchangeTest extends HttpTestBase {

  private static void assumeMlKemAvailable() {
    boolean available = OpenSsl.isAvailable();
    if (!available) {
      System.err.println("OpenSSL is not available: " + OpenSsl.unavailabilityCause());
      Assume.assumeTrue("OpenSSL is not available", false);
      return;
    }
    System.out.println("OpenSSL available: version=" + OpenSsl.versionString() + " (" + Long.toHexString(OpenSsl.version()) + ")");
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
  public void testHybridKeyExchangeHandshake() throws Exception {
    assumeMlKemAvailable();
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setSsl(true)
      .setUseAlpn(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get()));
    server.requestHandler(req -> req.response().end("hybrid-ok"));
    startServer(server);

    client = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
        .setUseAlpn(true)
      .setUseHybridKeyExchangeProtocol(true)
      .setTrustAll(true));
    HttpClient client2 = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(false)
      .setTrustAll(true));

    CompletableFuture<Boolean> cf1 = new CompletableFuture<>();
    CompletableFuture<Boolean> cf2 = new CompletableFuture<>();

    Future<HttpClientRequest> reqSuccess = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        assertEquals("TLSv1.3", req.connection().sslSession().getProtocol());
        resp.body().onComplete(onSuccess(body -> {
          assertEquals("hybrid-ok", body.toString());
          cf1.complete(true);
        }));
      }));
    }));

    Future<HttpClientRequest> reqFail = client2.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(
      onFailure(req -> {
        assertTrue(req instanceof javax.net.ssl.SSLHandshakeException);
        cf2.complete(true);
      })
    );

    CompletableFuture.allOf(cf1, cf2).thenAccept((v) -> testComplete()).get();
  }

  @Test
  public void testHybridKeyExchangeHandshakeMTLS() throws Exception {
    assumeMlKemAvailable();
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(true)
      .setClientAuth(ClientAuth.REQUIRED)
      .setKeyCertOptions(Cert.SERVER_PEM_ROOT_CA.get())
      .setTrustOptions(Trust.SERVER_PEM_ROOT_CA.get()));
    server.requestHandler(req -> {
      assertTrue(req.isSSL());
      req.response().end("mtls-hybrid-ok");
    });
    startServer(server);

    client = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(true)
      .setKeyCertOptions(Cert.CLIENT_PEM_ROOT_CA.get())
      .setTrustAll(true));
    HttpClient client2 = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(false)
      .setKeyCertOptions(Cert.CLIENT_PEM_ROOT_CA.get())
      .setTrustAll(true));

    CompletableFuture<Boolean> cf1 = new CompletableFuture<>();
    CompletableFuture<Boolean> cf2 = new CompletableFuture<>();

    Future<HttpClientRequest> reqSuccess = client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        assertEquals("TLSv1.3", req.connection().sslSession().getProtocol());
        resp.body().onComplete(onSuccess(body -> {
          assertEquals("mtls-hybrid-ok", body.toString());
          cf1.complete(true);
        }));
      }));
    }));

    Future<HttpClientRequest> reqFail = client2.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(
      onFailure(req -> {
        assertTrue(req instanceof javax.net.ssl.SSLHandshakeException);
        cf2.complete(true);
      })
    );

    CompletableFuture.allOf(cf1, cf2).thenAccept((v) -> testComplete()).get();
  }

  @Test
  public void testHybridFailsServerSideWhenPqcNotAvailable() throws Exception {
    assumeMlKemAvailable();
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get()));
    server.requestHandler(req -> req.response().end("should-not-reach"));
    startServer(server);

    client = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(true)
      .setTrustAll(true));

    client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(
      onFailure(err -> {
        assertTrue(err instanceof javax.net.ssl.SSLHandshakeException);
        testComplete();
      })
    );
    await();
  }

  @Test
  public void testHybridFailsClientSideWhenPqcNotAvailable() throws Exception {
    assumeMlKemAvailable();
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get()));
    server.requestHandler(req -> req.response().end("should-not-reach"));
    startServer(server);

    client = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(true)
      .setTrustAll(true));

    client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(
      onFailure(err -> {
        assertTrue(err instanceof javax.net.ssl.SSLHandshakeException);
        testComplete();
      })
    );
    await();
  }

  @Test
  public void testHybridKeyExchangeWithSNI() throws Exception {
    assumeMlKemAvailable();
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(true)
      .setSni(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get()));
    server.requestHandler(req -> req.response().end("sni-hybrid-ok"));
    startServer(server);

    client = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(true)
      .setTrustAll(true));

    client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        assertEquals("TLSv1.3", req.connection().sslSession().getProtocol());
        resp.body().onComplete(onSuccess(body -> {
          assertEquals("sni-hybrid-ok", body.toString());
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testHybridFailsWithSNIWhenPqcNotAvailable() throws Exception {
    assumeMlKemAvailable();
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(true)
      .setSni(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get()));
    server.requestHandler(req -> req.response().end("should-not-reach"));
    startServer(server);

    client = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(true)
      .setTrustAll(true));

    client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(
      onFailure(err -> {
        assertTrue(err instanceof javax.net.ssl.SSLHandshakeException);
        testComplete();
      })
    );
    await();
  }

  @Test
  public void testHybridWithRawNettySocket() throws Exception {
    assumeMlKemAvailable();
    // Start Vert.x server with hybrid
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get()));
    server.requestHandler(req -> req.response().end("hybrid-ok"));
    startServer(server);

    // Raw Netty client
    SslContext sslContext = SslContextBuilder.forClient()
      .sslProvider(SslProvider.OPENSSL)
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .build();

    // Will hold the negotiated group from key_share extension
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

            // Set hybrid curves on the OpenSSL engine
            ReferenceCountedOpenSslEngine engine =
              (ReferenceCountedOpenSslEngine) sslHandler.engine();
            SSL.setCurvesList(engine.sslPointer(), "X25519MLKEM768");

            // Interceptor BEFORE SslHandler sees raw TLS records
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
  public void testHybridMTLSWithRawNettySocket() throws Exception {
    assumeMlKemAvailable();
    // Start Vert.x server with hybrid + mTLS
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setSsl(true)
      .setSslEngineOptions(new OpenSSLEngineOptions())
      .setUseHybridKeyExchangeProtocol(true)
      .setClientAuth(ClientAuth.REQUIRED)
      .setKeyCertOptions(Cert.SERVER_PEM_ROOT_CA.get())
      .setTrustOptions(Trust.SERVER_PEM_ROOT_CA.get()));
    server.requestHandler(req -> {
      assertTrue(req.isSSL());
      req.response().end("mtls-hybrid-ok");
    });
    startServer(server);

    // Raw Netty client with client cert
    SslContext sslContext = SslContextBuilder.forClient()
      .sslProvider(SslProvider.OPENSSL)
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .keyManager(
        getClass().getClassLoader().getResourceAsStream("tls/client-cert-root-ca.pem"),
        getClass().getClassLoader().getResourceAsStream("tls/client-key.pem"))
      .build();

    // Will hold the negotiated group from key_share extension
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

            // Set hybrid curves on the OpenSSL engine
            ReferenceCountedOpenSslEngine engine =
              (ReferenceCountedOpenSslEngine) sslHandler.engine();
            SSL.setCurvesList(engine.sslPointer(), "X25519MLKEM768");

            // Interceptor BEFORE SslHandler sees raw TLS records
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
      // Always forward to SslHandler
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

      // Walk extensions
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
