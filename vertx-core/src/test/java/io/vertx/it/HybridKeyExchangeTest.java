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
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.internal.tcnative.SSL;
import io.vertx.core.http.*;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import io.vertx.test.http.HttpTestBase;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests hybrid key exchange (X25519MLKEM768) with OpenSSL.
 */
public class HybridKeyExchangeTest extends HttpTestBase {

  @Test
  public void testHybridKeyExchangeHandshake() throws Exception {
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setuseHybridKeyExchangeProtocol(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server.close();
    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("hybrid-ok"));
    startServer(server);

    ClientSSLOptions hybridClientSsl = new ClientSSLOptions()
      .setuseHybridKeyExchangeProtocol(true)
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new OpenSSLEngineOptions())
      .with(hybridClientSsl)
      .build();

    ClientSSLOptions nonHybridClientSsl = new ClientSSLOptions()
      .setuseHybridKeyExchangeProtocol(false)
      .setTrustAll(true);
    HttpClientAgent client2 = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new OpenSSLEngineOptions())
      .with(nonHybridClientSsl)
      .build();

    CompletableFuture<Boolean> cf1 = new CompletableFuture<>();
    CompletableFuture<Boolean> cf2 = new CompletableFuture<>();

    client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        assertEquals("TLSv1.3", req.connection().sslSession().getProtocol());
        resp.body().onComplete(onSuccess(body -> {
          assertEquals("hybrid-ok", body.toString());
          cf1.complete(true);
        }));
      }));
    }));

    client2.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(
      onFailure(err -> {
        assertTrue(err instanceof javax.net.ssl.SSLHandshakeException);
        cf2.complete(true);
      })
    );

    CompletableFuture.allOf(cf1, cf2).thenAccept((v) -> testComplete()).get();
  }

  @Test
  public void testHybridKeyExchangeHandshakeMTLS() throws Exception {
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setuseHybridKeyExchangeProtocol(true)
      .setClientAuth(io.vertx.core.http.ClientAuth.REQUIRED)
      .setKeyCertOptions(Cert.SERVER_PEM_ROOT_CA.get())
      .setTrustOptions(Trust.SERVER_PEM_ROOT_CA.get());

    server.close();
    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> {
      assertTrue(req.isSSL());
      req.response().end("mtls-hybrid-ok");
    });
    startServer(server);

    ClientSSLOptions hybridClientSsl = new ClientSSLOptions()
      .setuseHybridKeyExchangeProtocol(true)
      .setKeyCertOptions(Cert.CLIENT_PEM_ROOT_CA.get())
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new OpenSSLEngineOptions())
      .with(hybridClientSsl)
      .build();

    ClientSSLOptions nonHybridClientSsl = new ClientSSLOptions()
      .setuseHybridKeyExchangeProtocol(false)
      .setKeyCertOptions(Cert.CLIENT_PEM_ROOT_CA.get())
      .setTrustAll(true);
    HttpClientAgent client2 = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(new OpenSSLEngineOptions())
      .with(nonHybridClientSsl)
      .build();

    CompletableFuture<Boolean> cf1 = new CompletableFuture<>();
    CompletableFuture<Boolean> cf2 = new CompletableFuture<>();

    client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        assertEquals(200, resp.statusCode());
        assertEquals("TLSv1.3", req.connection().sslSession().getProtocol());
        resp.body().onComplete(onSuccess(body -> {
          assertEquals("mtls-hybrid-ok", body.toString());
          cf1.complete(true);
        }));
      }));
    }));

    client2.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(
      onFailure(err -> {
        assertTrue(err instanceof javax.net.ssl.SSLHandshakeException);
        cf2.complete(true);
      })
    );

    CompletableFuture.allOf(cf1, cf2).thenAccept((v) -> testComplete()).get();
  }

  @Test
  public void testHybridFailsWhenPqcNotAvailable() throws Exception {
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setuseHybridKeyExchangeProtocol(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server.close();
    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("should-not-reach"));
    startServer(server);

    ClientSSLOptions clientSsl = new ClientSSLOptions()
      .setuseHybridKeyExchangeProtocol(true)
      .setTrustAll(true);
    client = vertx.httpClientBuilder()
      .with(new HttpClientOptions().setSsl(true))
      .with(clientSsl)
      .build();

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
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setuseHybridKeyExchangeProtocol(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get());

    server.close();
    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> req.response().end("hybrid-ok"));
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
  public void testHybridMTLSWithRawNettySocket() throws Exception {
    ServerSSLOptions serverSslOptions = new ServerSSLOptions()
      .setuseHybridKeyExchangeProtocol(true)
      .setClientAuth(io.vertx.core.http.ClientAuth.REQUIRED)
      .setKeyCertOptions(Cert.SERVER_PEM_ROOT_CA.get())
      .setTrustOptions(Trust.SERVER_PEM_ROOT_CA.get());

    server.close();
    server = vertx.httpServerBuilder()
      .with(new HttpServerConfig(new HttpServerOptions()
        .setPort(DEFAULT_HTTPS_PORT)
        .setHost(DEFAULT_HTTPS_HOST)))
      .with(new OpenSSLEngineOptions())
      .with(serverSslOptions)
      .build();
    server.requestHandler(req -> {
      assertTrue(req.isSSL());
      req.response().end("mtls-hybrid-ok");
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
