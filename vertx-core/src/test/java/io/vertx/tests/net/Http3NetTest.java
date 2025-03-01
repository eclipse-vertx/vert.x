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
package io.vertx.tests.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.*;
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.http.*;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.Http3ProxyProvider;
import io.vertx.core.net.impl.Http3Utils;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.proxy.SocksProxy;
import io.vertx.tests.http.HttpOptionsFactory;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.test.http.HttpTestBase.*;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http3NetTest extends NetTest {

  private static final Logger log = LoggerFactory.getLogger(Http3NetTest.class);


  protected NetServerOptions createNetServerOptions() {
    return HttpOptionsFactory.createH3NetServerOptions().setHost(testAddress.hostAddress()).setPort(testAddress.port());
  }

  protected NetClientOptions createNetClientOptions() {
    return HttpOptionsFactory.createH3NetClientOptions();
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory.createH3HttpServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createH3HttpClientOptions();
  }

  @Override
  protected SocksProxy createSocksProxy() {
    return new SocksProxy().http3(true);
  }

  @Ignore("Host shortnames are not allowed in netty for QUIC.")
  @Override
  @Test
  public void testSniForceShortname() throws Exception {
    /*
     * QuicheQuicSslEngine.isValidHostNameForSNI() returns false for short hostnames.
     */
    super.testSniForceShortname();
  }

  /**
   * Returns the maximum acceptable packet size for UDP in HTTP/3.
   * A value of 1000 is chosen to avoid exceeding packet limits as we do not split large messages.
   */
  @Override
  protected int maxPacketSize() {
    return 1000;
  }

  @Override
  @Test
  public void testMissingClientSSLOptions() throws Exception {
    NetClientOptions options = new NetClientOptions();
    options.setProtocolVersion(io.vertx.core.http.HttpVersion.HTTP_3);
    client = vertx.createNetClient(options);

    super.testMissingClientSSLOptions();
  }

  protected void testNetClientInternal_(HttpServerOptions options, boolean expectSSL) throws Exception {
    waitFor(5);
    HttpServer server = vertx.createHttpServer(options);
    server.requestHandler(req -> {
      req.response().end("Hello World"); });
    CountDownLatch latch = new CountDownLatch(1);
    server.listen().onComplete(onSuccess(v -> {
      latch.countDown();
    }));
    awaitLatch(latch);
    client.connect(1234, "localhost").onComplete(onSuccess(so -> {
      NetSocketInternal soInt = (NetSocketInternal) so;
      assertEquals(true, soInt.isSsl());
      ChannelHandlerContext chctx = soInt.channelHandlerContext();
      ChannelPipeline pipeline = chctx.pipeline();
      pipeline.addBefore("handler", "myHttp3ClientConnectionHandler", new Http3ClientConnectionHandler());
      AtomicInteger status = new AtomicInteger();
      soInt.handler(buff -> fail());
      soInt.messageHandler(obj -> {
        assertTrue(obj instanceof QuicStreamChannel);
        complete();
      });
      DefaultFullHttpRequest message = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/somepath");
      message.headers().add(HttpHeaderNames.HOST, "localhost");

      Http3Utils.newRequestStream((QuicChannel) soInt.channelHandlerContext().channel(),
        ch -> {
          ch.pipeline().addLast("myHttp3FrameToHttpObjectCodec", Http3Utils.newHttp3ClientFrameToHttpObjectCodec());
          ch.pipeline().addLast("myHttpHandler", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object obj) {
              switch (status.getAndIncrement()) {
                case 0:
                  assertTrue(obj instanceof HttpResponse);
                  HttpResponse resp = (HttpResponse) obj;
                  assertEquals(200, resp.status().code());
                  complete();
                  break;
                case 1:
                  assertTrue(obj instanceof DefaultHttpContent);
                  ByteBuf content = ((DefaultHttpContent) obj).content();
                  assertTrue(content.isDirect());
                  assertEquals(1, content.refCnt());
                  String val = content.toString(StandardCharsets.UTF_8);
                  assertTrue(content.release());
                  assertEquals("Hello World", val);
                  complete();
                  break;
                case 2:
                  assertSame(LastHttpContent.EMPTY_LAST_CONTENT, obj);
                  complete();
                  break;
                default:
                  fail();
              }
            }
          });

          ch.writeAndFlush(message).addListener(future -> {
            if (future.isSuccess()) {
              complete();
            }
          });
        });
    }));
    await();
  }

  @Override
  protected void testNetServerInternal_(HttpClientOptions clientOptions, boolean expectSSL) throws Exception {
    waitFor(2);

    server.connectHandler(so -> {
      NetSocketInternal internal = (NetSocketInternal) so;
      NetSocketImpl soi = (NetSocketImpl) so;

      assertEquals(true, internal.isSsl());
      ChannelPipeline pipeline = soi.channel().pipeline();

      pipeline.replace("handler", "myHttp3ServerConnectionHandler",
        new Http3ServerConnectionHandler(new ChannelInitializer<QuicStreamChannel>() {
          @Override
          protected void initChannel(QuicStreamChannel ch) {
            ch.pipeline().addLast("myHttp3FrameToHttpObjectCodec", Http3Utils.newHttp3ServerFrameToHttpObjectCodec());
            ch.pipeline().addLast(new ChannelDuplexHandler() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof LastHttpContent) {
                  DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.copiedBuffer("Hello World", StandardCharsets.UTF_8));
                  response.headers().set(HttpHeaderNames.CONTENT_LENGTH, "11");
                  ch.writeAndFlush(response).addListener(future -> {
                    if (future.isSuccess()) {
                      complete();
                    }
                  });
                }
              }
            });
          }
        }));

      internal.handler(buff -> fail());
      internal.messageHandler(obj -> {
        System.out.println("obj msg handler = " + obj);
        soi.channelHandlerContext().fireChannelRead(obj);
      });
    });


    startServer();

    HttpClient client = vertx.createHttpClient(clientOptions);
    client.request(io.vertx.core.http.HttpMethod.GET, 1234, "localhost", "/somepath")
      .compose(req -> req
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .compose(HttpClientResponse::body)).onComplete(onSuccess(body -> {
        assertEquals("Hello World", body.toString());
        complete();
      }));
    await();
  }


  @Test
  public void testHttp3Socks5Proxy() throws Exception {
    waitFor(3);
    String clientText = "Hi, I'm client!";
    String serverText = "Hi, I'm server";

    CountDownLatch latch = new CountDownLatch(2);

    server.connectHandler((NetSocket sock) -> {
      complete();
      sock.handler(buf -> {
        byte[]arr = new byte[buf.length()];
        buf.getBytes(arr);
        log.info(" client msg = " + new String(arr));
        assertEquals(clientText, new String(arr));
        sock.write(serverText);
        complete();
      });
    });
    server.listen(server.actualPort(), "localhost").onComplete(onSuccess(v -> {
      log.info("Server started!");
      latch.countDown();
    }));


    proxy = createSocksProxy();
    proxy.startProxy(vertx).onComplete(onSuccess(v -> {
      latch.countDown();
    }));

    latch.await();

    Http3ProxyProvider proxyProvider = new Http3ProxyProvider(((VertxInternal)vertx).nettyEventLoopGroup().next());

    class ChannelInboundHandler extends ChannelInboundHandlerAdapter {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf msg0 = (ByteBuf) msg;
        byte[] arr = new byte[msg0.readableBytes()];
        msg0.copy().readBytes(arr);
        log.info("Received msg is: " + new String(arr));
        assertEquals(serverText, new String(arr));
        super.channelRead(ctx, msg);
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error(cause);
        ctx.close();
        fail();
      }
    }

    proxyProvider.createProxyQuicChannel("localhost", proxy.port(), "localhost", server.actualPort())
      .addListener((GenericFutureListener<Future<QuicChannel>>) channelFuture -> {
        QuicChannel quicChannel = channelFuture.get();
        quicChannel.pipeline().addLast(new ChannelInboundHandler());
        quicChannel.writeAndFlush(Unpooled.copiedBuffer(clientText.getBytes(StandardCharsets.UTF_8)))
          .addListener(future -> {
            assertTrue(future.isSuccess());
            log.info("Wrote to server successfully.");
            complete();
          });
      });
    await();
  }

  @Ignore
  @Override
  @Test
  public void testTLSClientCertRequiredNoClientCert1_3() throws Exception {
    super.testTLSClientCertRequiredNoClientCert1_3();
  }

  @Ignore
  @Override
  @Test
  public void testClientDrainHandler() {
    super.testClientDrainHandler();
  }

  @Ignore
  @Override
  @Test
  public void testServerDrainHandler() {
    super.testServerDrainHandler();
  }

  @Ignore
  @Override
  @Test
  public void testNetSocketInternalEvent() throws Exception {
    super.testNetSocketInternalEvent();
  }

  @Ignore
  @Override
  @Test
  public void testSslHandshakeTimeoutHappenedOnServer() throws Exception {
    super.testSslHandshakeTimeoutHappenedOnServer();
  }

  @Ignore
  @Override
  @Test
  public void testClientSniMultipleServerName() throws Exception {
    super.testClientSniMultipleServerName();
  }

  @Ignore
  @Override
  @Test
  public void testConnectTimeoutOverride() {
    super.testConnectTimeoutOverride();
  }

  @Ignore
  @Override
  @Test
  public void testWriteHandlerSuccess() throws Exception {
    super.testWriteHandlerSuccess();
  }

  @Ignore
  @Override
  @Test
  public void testSniWithServerNameStartTLS() throws Exception {
    super.testSniWithServerNameStartTLS();
  }

  @Ignore
  @Override
  @Test
  public void testSniOverrideServerName() throws Exception {
    super.testSniOverrideServerName();
  }

  @Ignore
  @Override
  @Test
  public void testConnectTimeout() {
    super.testConnectTimeout();
  }

  @Ignore
  @Override
  @Test
  public void testStartTLSServerSSLEnginePeerHost() throws Exception {
    super.testStartTLSServerSSLEnginePeerHost();
  }


  @Ignore
  @Override
  @Test
  public void testSslHandshakeTimeoutHappenedOnSniServer() throws Exception {
    super.testSslHandshakeTimeoutHappenedOnSniServer();
  }

  @Ignore
  @Override
  @Test
  public void testServerShutdown() throws Exception {
    super.testServerShutdown();
  }

  @Ignore
  @Override
  @Test
  public void testUpgradeToSSLIncorrectClientOptions1() {
    super.testUpgradeToSSLIncorrectClientOptions1();
  }

  @Ignore
  @Override
  @Test
  public void testUpgradeToSSLIncorrectClientOptions2() {
    super.testUpgradeToSSLIncorrectClientOptions2();
  }

  @Ignore
  @Override
  @Test
  public void testTLSClientCertClientNotTrusted() throws Exception {
    super.testTLSClientCertClientNotTrusted();
  }

  @Ignore
  @Override
  @Test
  public void testWriteHandlerFailure() throws Exception {
    super.testWriteHandlerFailure();
  }


  @Ignore
  @Override
  @Test
  public void testWithSocks4LocalResolver() throws Exception {
    super.testWithSocks4LocalResolver();
  }

  @Ignore
  @Override
  @Test
  public void testOverrideClientSSLOptions() {
    super.testOverrideClientSSLOptions();
  }


  @Ignore
  @Override
  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    super.testSharedServersRoundRobinButFirstStartAndStopServer();
  }

  @Ignore
  @Override
  @Test
  public void testSharedServersRoundRobin() throws Exception {
    super.testSharedServersRoundRobin();
  }

  @Ignore
  @Override
  @Test
  public void testReconnectAttemptsInfinite() {
    super.testReconnectAttemptsInfinite();
  }

  @Ignore
  @Override
  @Test
  public void testNetClientInternalTLSWithSuppliedSSLContext() throws Exception {
    super.testNetClientInternalTLSWithSuppliedSSLContext();
  }

  @Ignore
  @Override
  @Test
  public void sendFileServerToClient() throws Exception {
    super.sendFileServerToClient();
  }

  @Ignore
  @Override
  @Test
  public void testStartTLSClientCertClientNotTrusted() throws Exception {
    super.testStartTLSClientCertClientNotTrusted();
  }

  @Ignore
  @Override
  @Test
  public void testReconnectAttemptsMany() {
    super.testReconnectAttemptsMany();
  }

  @Ignore
  @Override
  @Test
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    super.testTLSClientCertRequiredNoClientCert();
  }

  @Ignore
  @Override
  @Test
  public void sendFileClientToServer() throws Exception {
    super.sendFileClientToServer();
  }

  @Ignore
  @Override
  @Test
  public void testWorkerClient() throws Exception {
    super.testWorkerClient();
  }

  @Ignore
  @Override
  @Test
  public void testInvalidTlsProtocolVersion() throws Exception {
    super.testInvalidTlsProtocolVersion();
  }

  @Ignore
  @Override
  @Test
  public void testTLSHostnameCertCheckCorrect() {
    super.testTLSHostnameCertCheckCorrect();
  }

  @Ignore
  @Override
  @Test
  public void testClientMissingHostnameVerificationAlgorithm1() {
    super.testClientMissingHostnameVerificationAlgorithm1();
  }

  @Ignore
  @Override
  @Test
  public void testClientMissingHostnameVerificationAlgorithm2() {
    super.testClientMissingHostnameVerificationAlgorithm2();
  }

  @Ignore
  @Override
  @Test
  public void testClientMissingHostnameVerificationAlgorithm3() {
    super.testClientMissingHostnameVerificationAlgorithm3();
  }

  @Ignore
  @Override
  @Test
  public void testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort() throws Exception {
    super.testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort();
  }


  @Ignore
  @Override
  @Test
  public void testSocketAddress() {
    super.testSocketAddress();
  }

  @Ignore
  @Override
  @Test
  public void testStartTLSClientTrustAll() throws Exception {
    super.testStartTLSClientTrustAll();
  }

  @Ignore
  @Override
  @Test
  public void testHostVerificationHttpsNotMatching() {
    super.testHostVerificationHttpsNotMatching();
  }


  @Ignore
  @Override
  @Test
  public void testSslHandshakeTimeoutHappenedWhenUpgradeSsl() {
    super.testSslHandshakeTimeoutHappenedWhenUpgradeSsl();
  }


  //TODO: resolve group1

  @Ignore
  @Override
  @Test
  public void testClientMultiThreaded() throws Exception {
    super.testClientMultiThreaded();
  }

  //TODO: resolve group2


  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion1TCP4() throws Exception {
    super.testHAProxyProtocolVersion1TCP4();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion1TCP6() throws Exception {
    super.testHAProxyProtocolVersion1TCP6();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2TCP4() throws Exception {
    super.testHAProxyProtocolVersion2TCP4();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2TCP6() throws Exception {
    super.testHAProxyProtocolVersion2TCP6();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2UDP4() throws Exception {
    super.testHAProxyProtocolVersion2UDP4();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2UDP6() throws Exception {
    super.testHAProxyProtocolVersion2UDP6();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolIllegalHeader1() throws Exception {
    super.testHAProxyProtocolIllegalHeader1();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolIllegalHeader2() throws Exception {
    super.testHAProxyProtocolIllegalHeader2();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolIdleTimeout() throws Exception {
    super.testHAProxyProtocolIdleTimeout();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2UnixDataGram() throws Exception {
    super.testHAProxyProtocolVersion2UnixDataGram();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2UnixSocket() throws Exception {
    super.testHAProxyProtocolVersion2UnixSocket();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolConnectSSL() throws Exception {
    super.testHAProxyProtocolConnectSSL();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolIdleTimeoutNotHappened() throws Exception {
    super.testHAProxyProtocolIdleTimeoutNotHappened();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion1Unknown() throws Exception {
    super.testHAProxyProtocolVersion1Unknown();
  }

  @Ignore
  @Override
  @Test
  public void testHAProxyProtocolVersion2Unknown() throws Exception {
    super.testHAProxyProtocolVersion2Unknown();
  }

  //TODO: resolve group3


  @Ignore
  @Override
  @Test
  public void testWithSocks5Proxy() throws Exception {
    super.testWithSocks5Proxy();
  }

  @Ignore
  @Override
  @Test
  public void testWithHttpConnectProxy() throws Exception {
    super.testWithHttpConnectProxy();
  }

  @Ignore
  @Override
  @Test
  public void testConnectSSLWithSocks5Proxy() throws Exception {
    super.testConnectSSLWithSocks5Proxy();
  }

  @Ignore
  @Override
  @Test
  public void testUpgradeSSLWithSocks5Proxy() throws Exception {
    super.testUpgradeSSLWithSocks5Proxy();
  }

  @Ignore
  @Override
  @Test
  public void testWithSocks4aProxyAuth() throws Exception {
    super.testWithSocks4aProxyAuth();
  }

  @Ignore
  @Override
  @Test
  public void testWithSocks4aProxy() throws Exception {
    super.testWithSocks4aProxy();
  }

  @Ignore
  @Override
  @Test
  public void testWithSocks5ProxyAuth() throws Exception {
    super.testWithSocks5ProxyAuth();
  }




  //TODO: resolve group5

  @Ignore
  @Override
  @Test
  public void testSniWithServerNameTrustFallback() {
    super.testSniWithServerNameTrustFallback();
  }


  @Ignore
  @Override
  @Test
  public void testSniWithServerNameTrust() {
    super.testSniWithServerNameTrust();
  }

  @Ignore
  @Override
  @Test
  public void testTLSHostnameCertCheckIncorrect() {
    super.testTLSHostnameCertCheckIncorrect();
  }


  @Ignore
  @Override
  @Test
  public void testClientShutdown() throws Exception {
    super.testClientShutdown();
  }

}
