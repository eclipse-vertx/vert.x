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
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.http2.Http3Utils;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.QuicProxyProvider;
import io.vertx.core.net.impl.QuicUtils;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.proxy.HAProxy;
import io.vertx.test.proxy.HttpProxy;
import io.vertx.test.proxy.Socks4Proxy;
import io.vertx.test.proxy.SocksProxy;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class QuicNetTest extends NetTest {

  protected NetServerOptions createNetServerOptions() {
    return createH3NetServerOptions();
  }

  protected NetClientOptions createNetClientOptions() {
    return createH3NetClientOptions();
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpTestBase.createH3HttpServerOptions(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpTestBase.createH3HttpClientOptions();
  }

  @Override
  protected HttpServerOptions createHttpServerOptionsForNetTest() {
    return HttpTestBase.createH3HttpServerOptions();
  }

  @Override
  protected HttpClientOptions createHttpClientOptionsForNetTest() {
    return HttpTestBase.createH3HttpClientOptions();
  }

  @Override
  protected Socks4Proxy createSocks4Proxy() {
    return new Socks4Proxy().http3(true);
  }

  @Override
  protected SocksProxy createSocksProxy() {
    return new SocksProxy().http3(true);
  }

  @Override
  protected HttpProxy createHttpProxy() {
    return new HttpProxy().http3(true);
  }

  @Override
  protected HAProxy createHAProxy(SocketAddress remoteAddress, Buffer header) {
    HAProxy haProxy = new HAProxy(remoteAddress, header);
    haProxy.http3(true);
    return haProxy;
  }

  @Override
  protected List<String> alpnNames() {
    return List.of(io.vertx.core.http.HttpVersion.HTTP_3.alpnName());
  }

  @Override
  protected SslContext createSSLContext() {
    return QuicSslContextBuilder.forClient()
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .applicationProtocols(Http3.supportedApplicationProtocols()).build();
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
  //TODO: correct this issue
  @Override
  protected int maxPacketSize() {
    return 1000;
  }

  @Override
  @Test
  public void testMissingClientSSLOptions() throws Exception {
    NetClientOptions options = new NetClientOptions();
    client = vertx.createNetClient(options);

    super.testMissingClientSSLOptions();
  }

  protected void testNetClientInternal_(HttpServerOptions options, boolean expectSSL) throws Exception {
    waitFor(5);
    HttpServer server = vertx.createHttpServer(options);
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
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
          ch.pipeline().addLast("myHttp3FrameToHttpObjectCodec", QuicUtils.newClientFrameToHttpObjectCodec());
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

    Handler<QuicStreamChannel> requestStreamHandler = ch -> {
      ch.pipeline().addLast("myHttp3FrameToHttpObjectCodec", QuicUtils.newServerFrameToHttpObjectCodec());
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
    };

    server.connectHandler(so -> {
      NetSocketInternal internal = (NetSocketInternal) so;
      NetSocketImpl soi = (NetSocketImpl) so;

      assertEquals(true, internal.isSsl());
      ChannelPipeline pipeline = soi.channel().pipeline();

      pipeline.replace("handler", "myHttp3ServerConnectionHandler",
        Http3Utils.newServerConnectionHandlerBuilder().requestStreamHandler(requestStreamHandler).build());

      internal.handler(buff -> fail());
      internal.messageHandler(obj -> {
        log.info("obj msg handler = " + obj);
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

  @Category(QuicProxyProvider.class)
  @Test
  public void testVertxBasedSocks5Proxy() throws Exception {
    QuicProxyProvider.IS_NETTY_BASED_PROXY = false;
    testProxy_(ProxyType.SOCKS5);
  }

  // TODO: Remove this method/class/field once Netty merges PR #14993, which adds destination support to ProxyHandler's.
  // This is currently a temporary duplicate of a class with the same name in Netty.
  // See: https://github.com/netty/netty/pull/14993
  @Category(QuicProxyProvider.class)
  @Test
  @Ignore
  public void testNettyBasedSocks5Proxy() throws Exception {
    QuicProxyProvider.IS_NETTY_BASED_PROXY = true;
    testProxy_(ProxyType.SOCKS5);
  }

  @Category(QuicProxyProvider.class)
  @Test
  @Ignore
  public void testVertxBasedSocks4Proxy() throws Exception {
    QuicProxyProvider.IS_NETTY_BASED_PROXY = false;
    testProxy_(ProxyType.SOCKS4);
  }

  // TODO: Remove this method/class/field once Netty merges PR #14993, which adds destination support to ProxyHandler's.
  // This is currently a temporary duplicate of a class with the same name in Netty.
  // See: https://github.com/netty/netty/pull/14993
  @Category(QuicProxyProvider.class)
  @Test
  public void testNettyBasedSocks4Proxy() throws Exception {
    QuicProxyProvider.IS_NETTY_BASED_PROXY = true;
    testProxy_(ProxyType.SOCKS4);
  }

  @Category(QuicProxyProvider.class)
  @Test
  @Ignore
  public void testVertxBasedHttpProxy() throws Exception {
    QuicProxyProvider.IS_NETTY_BASED_PROXY = false;
    testProxy_(ProxyType.HTTP);
  }

  // TODO: Remove this method/class/field once Netty merges PR #14993, which adds destination support to ProxyHandler's.
  // This is currently a temporary duplicate of a class with the same name in Netty.
  // See: https://github.com/netty/netty/pull/14993
  @Category(QuicProxyProvider.class)
  @Ignore("It is not possible to use an HTTP proxy without modifying Netty.")
  @Test
  public void testNettyBasedHttpProxy() throws Exception {
    QuicProxyProvider.IS_NETTY_BASED_PROXY = true;
    testProxy_(ProxyType.HTTP);
  }

  private void testProxy_(ProxyType proxyType) throws Exception {
    log.info("Proxy test is running with proxyType: " + proxyType);
    waitFor(4);
    String clientText = "Hi, I'm client!";
    String serverText = "Hi, I'm server";

    CountDownLatch latch = new CountDownLatch(2);

    // Start of server part
    server.connectHandler((NetSocket sock) -> {
      log.info("Created socket on server!");
      complete();
      sock.handler(buffer -> {
        log.info("Client msg is: " + buffer.toString(StandardCharsets.UTF_8));
        assertEquals(clientText, buffer.toString(StandardCharsets.UTF_8));
        sock.write(serverText);
        complete();
      });
    });
    server.listen(1234, "localhost").onComplete(onSuccess(v -> {
      log.info("Server started!");
      latch.countDown();
    }));


    // Start of proxy server part
    switch (proxyType) {
      case HTTP:
        proxy = new HttpProxy().http3(true);
        break;
      case SOCKS4:
        proxy = new Socks4Proxy().http3(true);
        break;
      case SOCKS5:
        proxy = new SocksProxy().http3(true);
        break;
      default:
        throw new RuntimeException("Not Supported!");
    }

    proxy.startAsync(vertx).onComplete(onSuccess(v -> {
      latch.countDown();
      log.info("Proxy started!");
    }));
    awaitLatch(latch);

    // Start of client part

    NetClientOptions clientOptions = createNetClientOptions()
      .setProxyOptions(createProxyOptions().setType(proxyType).setPort(proxy.port()));
    NetClient client = vertx.createNetClient(clientOptions);
    client.connect(1234, "localhost").onComplete(onSuccess(so -> {
      log.info("Sending a message to proxy server...");
      so.handler(buffer -> {
        log.info("Server msg is : " + buffer);
        // make sure we have gone through the proxy
        assertEquals("localhost:1234", proxy.getLastUri());

        assertEquals(serverText, buffer.toString(StandardCharsets.UTF_8));
        complete();
      });
      so.exceptionHandler(this::fail);

      so.write(clientText).onComplete(onSuccess(e -> {
        complete();
      }));
    }));

    await();
  }

  @Override
  @Test
  public void testWithSocks5Proxy() throws Exception {
    QuicProxyProvider.IS_NETTY_BASED_PROXY = false;
    super.testWithSocks5Proxy();
  }

  @Test
  public void testWithSocks5ProxyNettyBased() throws Exception {
    QuicProxyProvider.IS_NETTY_BASED_PROXY = true;
    super.testWithSocks5Proxy();
  }

  // TODO: Remove this method/class/field once Netty merges PR #14993, which adds destination support to ProxyHandler's.
  // This is currently a temporary duplicate of a class with the same name in Netty.
  // See: https://github.com/netty/netty/pull/14993
  @Test
  public void testWithSocks4aProxyAuthNettyBased() throws Exception {
    QuicProxyProvider.IS_NETTY_BASED_PROXY = true;
    super.testWithSocks4aProxyAuth();
  }

  // TODO: Remove this method/class/field once Netty merges PR #14993, which adds destination support to ProxyHandler's.
  // This is currently a temporary duplicate of a class with the same name in Netty.
  // See: https://github.com/netty/netty/pull/14993
  @Test
  public void testWithSocks4aProxyNettyBased() throws Exception {
    QuicProxyProvider.IS_NETTY_BASED_PROXY = true;
    super.testWithSocks4aProxy();
  }

  // TODO: Remove this method/class/field once Netty merges PR #14993, which adds destination support to ProxyHandler's.
  // This is currently a temporary duplicate of a class with the same name in Netty.
  // See: https://github.com/netty/netty/pull/14993
  @Test
  public void testWithSocks5ProxyAuthNettyBased() throws Exception {
    QuicProxyProvider.IS_NETTY_BASED_PROXY = true;
    super.testWithSocks5ProxyAuth();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolVersion1TCP4() throws Exception {
    super.testHAProxyProtocolVersion1TCP4();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolVersion1TCP6() throws Exception {
    super.testHAProxyProtocolVersion1TCP6();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolVersion2TCP4() throws Exception {
    super.testHAProxyProtocolVersion2TCP4();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolVersion2TCP6() throws Exception {
    super.testHAProxyProtocolVersion2TCP6();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolVersion2UDP4() throws Exception {
    super.testHAProxyProtocolVersion2UDP4();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolVersion2UDP6() throws Exception {
    super.testHAProxyProtocolVersion2UDP6();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolIllegalHeader1() throws Exception {
    super.testHAProxyProtocolIllegalHeader1();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolIllegalHeader2() throws Exception {
    super.testHAProxyProtocolIllegalHeader2();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolIdleTimeout() throws Exception {
    super.testHAProxyProtocolIdleTimeout();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolVersion2UnixDataGram() throws Exception {
    super.testHAProxyProtocolVersion2UnixDataGram();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolVersion2UnixSocket() throws Exception {
    super.testHAProxyProtocolVersion2UnixSocket();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolConnectSSL() throws Exception {
    super.testHAProxyProtocolConnectSSL();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolIdleTimeoutNotHappened() throws Exception {
    super.testHAProxyProtocolIdleTimeoutNotHappened();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolVersion1Unknown() throws Exception {
    super.testHAProxyProtocolVersion1Unknown();
  }

  @Ignore("HAProxy protocol is TCP-based and not applicable to Netty's HTTP/3 (UDP/QUIC) transport.")
  @Override
  @Test
  public void testHAProxyProtocolVersion2Unknown() throws Exception {
    super.testHAProxyProtocolVersion2Unknown();
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


  //TODO: resolve group3

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
  public void testWithHttpConnectProxy() throws Exception {
    super.testWithHttpConnectProxy();
  }

  @Ignore
  @Override
  @Test
  public void testWorkerServer() {
    super.testWorkerServer();
  }

  @Ignore
  @Override
  @Test
  public void testTLSHostnameCertCheckIncorrect() {
    super.testTLSHostnameCertCheckIncorrect();
  }

  @Test
  @Ignore
  @Override
  public void testConnectLocalHost() {
    super.testConnectLocalHost();
  }

  @Test
  @Ignore
  @Override
  public void testAsyncWriteIsFlushed() throws Exception {
    super.testAsyncWriteIsFlushed();
  }

  @Test
  @Ignore
  @Override
  public void testTLSServerSSLEnginePeerHost() throws Exception {
    super.testTLSServerSSLEnginePeerHost();
  }

  @Test
  @Ignore
  @Override
  public void testSNIServerSSLEnginePeerHost() throws Exception {
    super.testSNIServerSSLEnginePeerHost();
  }
}
