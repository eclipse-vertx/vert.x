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

package io.vertx.tests.http;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.WebSocketVersion;
import io.vertx.core.http.impl.http1.Http1ClientConnection;
import io.vertx.core.http.impl.http1.Http1ServerConnection;
import io.vertx.core.internal.http.WebSocketInternal;
import io.vertx.core.http.impl.websocket.WebSocketFrameImpl;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.CheckingSender;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.ProvidedBy;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxProvider;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.core.VertxTestBase2;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.proxy.HAProxy;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.vertx.test.core.TestUtils.assertIllegalStateException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;
import static io.vertx.test.core.TestUtils.assertWaitUntil;
import static io.vertx.test.core.TestUtils.randomAlphaString;
import static io.vertx.test.core.VertxTestBase.ENABLED_CIPHER_SUITES;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTPS_HOST;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTPS_PORT;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTP_HOST;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTP_HOST_AND_PORT;
import static io.vertx.test.http.HttpTestBase.DEFAULT_HTTP_PORT;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocketTest extends VertxTestBase2 {

  private static final String TEST_REASON = "I'm moving away!";
  private static final short TEST_STATUS_CODE = (short)1001;
  private static final short INVALID_STATUS_CODE = (short)1004;

  private WebSocketClient client;
  private HttpServer server;
  private NetServer netServer;

  public static class WebSocketTestVertxProvider implements VertxProvider {
    @Override
    public Vertx call() {
      VertxOptions options = new VertxOptions();
      options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
          "127.0.0.1 localhost\n" +
          "127.0.0.1 host2.com"));
      return Vertx.builder().with(options).withTransport(VertxTestBase.TRANSPORT).build();
    }
  }

  @Override
  @Before
  public void setUp(@ProvidedBy(WebSocketTestVertxProvider.class) Vertx vertx) throws Exception {
    super.setUp(vertx);
  }

  @After
  public void tearDown() throws Exception {
    if (client != null) {
      client.close();
    }
    if (server != null) {
      server.close().await();
    }
    if (netServer != null) {
      netServer.close().await();
    }
  }

  @Test
  public void testRejectHybi00(Checkpoint checkpoint) {
    testReject(WebSocketVersion.V00, null, 502, "Bad Gateway", checkpoint);
  }

  @Test
  public void testRejectHybi08(Checkpoint checkpoint) {
    testReject(WebSocketVersion.V08, null, 502, "Bad Gateway", checkpoint);
  }

  @Test
  public void testRejectWithStatusCode(Checkpoint checkpoint) {
    testReject(WebSocketVersion.V08, 404, 404, "Not Found", checkpoint);
  }

  @Test
  public void testWSBinaryHybi00(Checkpoint checkpoint) {
    testWSFrames(true, WebSocketVersion.V00, checkpoint);
  }

  @Test
  public void testWSStringHybi00(Checkpoint checkpoint) {
    testWSFrames(false, WebSocketVersion.V00, checkpoint);
  }

  @Test
  public void testWSBinaryHybi08(Checkpoint checkpoint) {
    testWSFrames(true, WebSocketVersion.V08, checkpoint);
  }

  @Test
  public void testWSStringHybi08(Checkpoint checkpoint) {
    testWSFrames(false, WebSocketVersion.V08, checkpoint);
  }

  @Test
  public void testWSBinaryHybi17(Checkpoint checkpoint) {
    testWSFrames(true, WebSocketVersion.V13, checkpoint);
  }

  @Test
  public void testWSStringHybi17(Checkpoint checkpoint) {
    testWSFrames(false, WebSocketVersion.V13, checkpoint);
  }

  @Test
  public void testWSStreamsHybi00(Checkpoint checkpoint) {
    testWSWriteStream(WebSocketVersion.V00, checkpoint);
  }

  @Test
  public void testWSStreamsHybi08(Checkpoint checkpoint) {
    testWSWriteStream(WebSocketVersion.V08, checkpoint);
  }

  @Test
  public void testWSStreamsHybi17(Checkpoint checkpoint) {
    testWSWriteStream(WebSocketVersion.V13, checkpoint);
  }

  @Test
  public void testWriteFromConnectHybi00(Checkpoint checkpoint) {
    testWriteFromConnectHandler(WebSocketVersion.V00, checkpoint);
  }

  @Test
  public void testWriteFromConnectHybi08(Checkpoint checkpoint) {
    testWriteFromConnectHandler(WebSocketVersion.V08, checkpoint);
  }

  @Test
  public void testWriteFromConnectHybi17(Checkpoint checkpoint) {
    testWriteFromConnectHandler(WebSocketVersion.V13, checkpoint);
  }

  @Test
  public void testContinuationWriteFromConnectHybi08(Checkpoint checkpoint) {
    testContinuationWriteFromConnectHandler(WebSocketVersion.V08, checkpoint);
  }

  @Test
  public void testContinuationWriteFromConnectHybi17(Checkpoint checkpoint) {
    testContinuationWriteFromConnectHandler(WebSocketVersion.V13, checkpoint);
  }

  @Test
  public void testValidSubProtocolHybi00(Checkpoint checkpoint) {
    testValidSubProtocol(WebSocketVersion.V00, checkpoint);
  }

  @Test
  public void testValidSubProtocolHybi08(Checkpoint checkpoint) {
    testValidSubProtocol(WebSocketVersion.V08, checkpoint);
  }

  @Test
  public void testValidSubProtocolHybi17(Checkpoint checkpoint) {
    testValidSubProtocol(WebSocketVersion.V13, checkpoint);
  }

  @Test
  public void testInvalidSubProtocolHybi00(Checkpoint checkpoint) {
    testInvalidSubProtocol(WebSocketVersion.V00, checkpoint);
  }

  @Test
  public void testInvalidSubProtocolHybi08(Checkpoint checkpoint) {
    testInvalidSubProtocol(WebSocketVersion.V08, checkpoint);
  }

  @Test
  public void testInvalidSubProtocolHybi17(Checkpoint checkpoint) {
    testInvalidSubProtocol(WebSocketVersion.V13, checkpoint);
  }

  // TODO close and exception tests
  // TODO pause/resume/drain tests

  @Test
  // Client trusts all server certs
  public void testTLSClientTrustAll(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true, checkpoint);

  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE, false, false, false, false, true, checkpoint);

  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertWithSNI(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE, false, false, false, false, true, true, true, true, new String[0],
      (client) -> client.connect(DEFAULT_HTTPS_PORT, "host2.com", "/"), checkpoint);

  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertPKCS12(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_PKCS12, Trust.NONE, false, false, false, false, true, checkpoint);

  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertPEM(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_PEM, Trust.NONE, false, false, false, false, true, checkpoint);

  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertPEM_CA(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA, Cert.SERVER_PEM_ROOT_CA, Trust.NONE, false, false, false, false, true, checkpoint);

  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustPKCS12ServerCert(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.SERVER_PKCS12, Cert.SERVER_JKS, Trust.NONE, false, false, false, false, true, checkpoint);

  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustPEMServerCert(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.SERVER_PEM, Cert.SERVER_JKS, Trust.NONE, false, false, false, false, true, checkpoint);

  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, false, false, false, checkpoint);

  }

  @Test
  //Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired(Checkpoint checkpoint) {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, false, false, false, false, true, checkpoint);

  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertRequired(Checkpoint checkpoint) {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false, true, checkpoint);

  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertRequiredPKCS12(Checkpoint checkpoint) {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PKCS12, true, false, false, false, true, checkpoint);

  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertRequiredPEM(Checkpoint checkpoint) {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PEM, true, false, false, false, true, checkpoint);

  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertPKCS12Required(Checkpoint checkpoint) {
    testTLS(Cert.CLIENT_PKCS12, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false, true, checkpoint);

  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertPEMRequired(Checkpoint checkpoint) {
    testTLS(Cert.CLIENT_PEM, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false, true, checkpoint);

  }

  @Test
  //Client specifies cert signed by CA and it is required
  public void testTLSClientCertPEM_CARequired(Checkpoint checkpoint) {
    testTLS(Cert.CLIENT_PEM_ROOT_CA, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PEM_ROOT_CA, true, false, false, false, true, checkpoint);

  }

  @Test
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false, false, checkpoint);
  }

  @Test
  //Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted(Checkpoint checkpoint) {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE, true, false, false, false, false, checkpoint);
  }

  @Test
  // Server specifies cert that the client does not trust via a revoked certificate of the CA
  public void testTLSClientRevokedServerCert(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA, Cert.SERVER_PEM_ROOT_CA, Trust.NONE, false, false, false, true, false, checkpoint);
  }

  @Test
  //Client specifies cert that the server does not trust via a revoked certificate of the CA
  public void testTLSRevokedClientCertServer(Checkpoint checkpoint) {
    testTLS(Cert.CLIENT_PEM_ROOT_CA, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PEM_ROOT_CA, true, true, false, false, false, checkpoint);
  }

  @Test
  // Test with cipher suites
  public void testTLSCipherSuites(Checkpoint checkpoint) {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true, checkpoint, ENABLED_CIPHER_SUITES);
  }

  // RequestOptions tests

  @Test
  // Client trusts all server certs
  public void testClearClientRequestOptionsSetSSL(Checkpoint checkpoint) {
    WebSocketConnectOptions options = new WebSocketConnectOptions().setHost(HttpTestBase.DEFAULT_HTTP_HOST).setURI("/").setPort(DEFAULT_HTTPS_PORT).setSsl(true);
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true, false, true, false, new String[0], (client) -> client.connect(options), checkpoint);
  }

  @Test
  // Client trusts all server certs
  public void testSSLClientRequestOptionsSetSSL(Checkpoint checkpoint) {
    WebSocketConnectOptions options = new WebSocketConnectOptions().setHost(HttpTestBase.DEFAULT_HTTP_HOST).setURI("/").setPort(DEFAULT_HTTPS_PORT).setSsl(true);
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true, true, true, false, new String[0], (client) -> client.connect(options), checkpoint);
  }

  @Test
  // Client trusts all server certs
  public void testClearClientRequestOptionsSetClear(Checkpoint checkpoint) {
    WebSocketConnectOptions options = new WebSocketConnectOptions().setHost(HttpTestBase.DEFAULT_HTTP_HOST).setURI("/").setPort(DEFAULT_HTTPS_PORT).setSsl(false);
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true, false, false, false, new String[0], (client) -> client.connect(options), checkpoint);
  }

  @Test
  // Client trusts all server certs
  public void testSSLClientRequestOptionsSetClear(Checkpoint checkpoint) {
    WebSocketConnectOptions options = new WebSocketConnectOptions().setHost(DEFAULT_HTTPS_HOST).setURI("/").setPort(DEFAULT_HTTPS_PORT).setSsl(false);
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true, true, false, false, new String[0], (client) -> client.connect(options), checkpoint);
  }

  private void testTLS(Cert<?> clientCert, Trust<?> clientTrust,
                       Cert<?> serverCert, Trust<?> serverTrust,
                       boolean requireClientAuth, boolean serverUsesCrl, boolean clientTrustAll,
                       boolean clientUsesCrl, boolean shouldPass,
                       Checkpoint checkpoint,
                       String... enabledCipherSuites) {
    testTLS(clientCert, clientTrust,
        serverCert, serverTrust,
        requireClientAuth, serverUsesCrl, clientTrustAll, clientUsesCrl, shouldPass, true, true, false,
        enabledCipherSuites, (client) -> client.connect(DEFAULT_HTTPS_PORT, DEFAULT_HTTP_HOST, "/"), checkpoint);
  }

  private void testTLS(Cert<?> clientCert, Trust<?> clientTrust,
                       Cert<?> serverCert, Trust<?> serverTrust,
                       boolean requireClientAuth, boolean serverUsesCrl, boolean clientTrustAll,
                       boolean clientUsesCrl, boolean shouldPass,
                       boolean clientSsl,
                       boolean serverSsl,
                       boolean sni,
                       String[] enabledCipherSuites,
                       Function<WebSocketClient, Future<WebSocket>> wsProvider, Checkpoint checkpoint) {
    WebSocketClientOptions options = new WebSocketClientOptions();
    options.setSsl(clientSsl);
    options.setTrustAll(clientTrustAll);
    if (clientUsesCrl) {
      options.addCrlPath("tls/root-ca/crl.pem");
    }
    options.setTrustOptions(clientTrust.get());
    options.setKeyCertOptions(clientCert.get());
    for (String suite: enabledCipherSuites) {
      options.addEnabledCipherSuite(suite);
    }
    client = vertx.createWebSocketClient(options);
    HttpServerOptions serverOptions = new HttpServerOptions();
    serverOptions.setSsl(serverSsl);
    serverOptions.setSni(sni);
    serverOptions.setTrustOptions(serverTrust.get());
    serverOptions.setKeyCertOptions(serverCert.get());
    if (requireClientAuth) {
      serverOptions.setClientAuth(ClientAuth.REQUIRED);
    }
    if (serverUsesCrl) {
      serverOptions.addCrlPath("tls/root-ca/crl.pem");
    }
    for (String suite: enabledCipherSuites) {
      serverOptions.addEnabledCipherSuite(suite);
    }
    server = vertx.createHttpServer(serverOptions.setPort(DEFAULT_HTTPS_PORT));
    server.webSocketHandler(ws -> {
      ws.handler(ws::write);
    });
    server.listen().await();
    Handler<AsyncResult<WebSocket>> handler = ar2 -> {
      if (ar2.succeeded()) {
        WebSocket ws = ar2.result();
        if (clientSsl && sni) {
          try {
            Certificate clientPeerCert = ws.peerCertificates().get(0);
            assertEquals("host2.com", TestUtils.cnOf(clientPeerCert));
          } catch (Exception err) {
            fail(err.getMessage());
          }
        }
        int size = 100;
        Buffer received = Buffer.buffer();
        ws.handler(data -> {
          received.appendBuffer(data);
          if (received.length() == size) {
            ws.close();
            checkpoint.succeed();
          }
        });
        Buffer buff = Buffer.buffer(TestUtils.randomByteArray(size));
        ws.writeFrame(io.vertx.core.http.WebSocketFrame.binaryFrame(buff, true));
      } else {
        if (shouldPass) {
          ar2.cause().printStackTrace();
          fail("Should not throw exception");
        } else {
          checkpoint.succeed();
        }
      }
    };
    wsProvider.apply(client).onComplete(handler);
  }

  @Test
  public void testOverrideClientSSLOptions(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setSsl(true).setKeyCertOptions(Cert.SERVER_JKS.get()));
    server.webSocketHandler(ws -> {
    }).listen(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST).await();
    client = vertx.createWebSocketClient(new WebSocketClientOptions()
      .setVerifyHost(false)
      .setSsl(true)
      .setTrustOptions(Trust.CLIENT_JKS.get())
    );
    WebSocketConnectOptions connectOptions = new WebSocketConnectOptions()
      .setHost(DEFAULT_HTTPS_HOST)
      .setPort(DEFAULT_HTTPS_PORT);
    client.connect(connectOptions)
      .onComplete(TestUtils.onFailure(err -> {
        client.connect(new WebSocketConnectOptions(connectOptions)
            .setSslOptions(new ClientSSLOptions().setTrustOptions(Trust.SERVER_JKS.get())))
          .onComplete(TestUtils.onSuccess(so -> {
            checkpoint.succeed();
          }));
    }));
  }

  @Test
  public void testHandleWSManually(Checkpoint checkpoint) {
    testHandleWSManually(false, false, checkpoint);
  }

  @Test
  public void testHandleWSManuallyDeclineExtension(Checkpoint checkpoint) {
    testHandleWSManually(true, false, checkpoint);
  }

  @Test
  public void testHandleWSManuallyDeclineSubprotocol(Checkpoint checkpoint) {
    testHandleWSManually(false, true, checkpoint);
  }

  private void testHandleWSManually(boolean declineExtension, boolean declineSubprotocol, Checkpoint checkpoint) {
    String path = "/some/path";
    String message = "here is some text data";
    String extension = "permessage-deflate";
    String subProtocol = "myprotocol";

    HttpServerOptions serverOptions = new HttpServerOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setPerMessageWebSocketCompressionSupported(true) // This should be ignored if declineExtension is true
      .setPerFrameWebSocketCompressionSupported(true) // This should be ignored if declineExtension is true
      .addWebSocketSubProtocol(subProtocol);
    server = vertx.createHttpServer(serverOptions).requestHandler(req -> {
      Map<String, String> extraResponseHeaders = new HashMap<>();
      if (!declineExtension) {
        assertEquals(extension, req.headers().get("sec-websocket-extensions"));
        extraResponseHeaders.put("sec-websocket-extensions", extension);
      }
      if (!declineSubprotocol) {
        extraResponseHeaders.put("sec-websocket-protocol", subProtocol);
      }
      getUpgradedNetSocket(req, path, extraResponseHeaders).onComplete(TestUtils.onSuccess(sock -> {
        // Let's write a Text frame raw
        Buffer buff = Buffer.buffer();
        buff.appendByte((byte)129); // Text frame
        buff.appendByte((byte)message.length());
        buff.appendString(message);
        sock.write(buff);
      }));
    });
    server.listen().await();

    WebSocketClientOptions clientOptions = new WebSocketClientOptions()
      .setTryUsePerMessageCompression(true)
      .setTryUsePerFrameCompression(false);
    client = vertx.createWebSocketClient(clientOptions);
    vertx.runOnContext(v -> {
      WebSocketConnectOptions connectOptions = new WebSocketConnectOptions()
        .setHost(DEFAULT_HTTP_HOST)
        .setPort(DEFAULT_HTTP_PORT)
        .setURI(path)
        .addSubProtocol(subProtocol);
      Handler<AsyncResult<WebSocket>> handler;
      if (declineSubprotocol) {
        handler = TestUtils.onFailure(err -> {
          checkpoint.succeed();
        });
      } else {
        handler = TestUtils.onSuccess(ws -> {
          MultiMap headers = ws.headers();
          if (declineExtension) {
            assertFalse(headers.contains("sec-websocket-extensions"));
          } else {
            assertTrue(headers.contains("sec-websocket-extensions", extension, true));
          }
          assertTrue(headers.contains("sec-websocket-protocol", subProtocol, true));
          ws.handler(buff -> {
            assertEquals(message, buff.toString("UTF-8"));
            checkpoint.succeed();
          });
        });
      }
      client.connect(connectOptions).onComplete(handler);
    });

  }

  @Test
  public void testSharedServersRoundRobin() throws Exception {
    int numServers = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE / 2- 1;
    int numConnections = numServers * 100;

    List<HttpServer> servers = new ArrayList<>();
    Set<HttpServer> connectedServers = ConcurrentHashMap.newKeySet();
    Map<HttpServer, Integer> connectCount = new ConcurrentHashMap<>();

    CountDownLatch latchConns = new CountDownLatch(numConnections);
    for (int i = 0; i < numServers; i++) {
      HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
      servers.add(theServer);
      theServer.webSocketHandler(ws -> {
        connectedServers.add(theServer);
        Integer cnt = connectCount.get(theServer);
        int icnt = cnt == null ? 0 : cnt;
        icnt++;
        connectCount.put(theServer, icnt);
        latchConns.countDown();
      });
      theServer.listen().await();
    }

    // Create a bunch of connections
    client = vertx.createWebSocketClient();
    CountDownLatch latchClient = new CountDownLatch(numConnections);
    for (int i = 0; i < numConnections; i++) {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/someuri").onComplete(TestUtils.onSuccess(ws -> {
        ws.closeHandler(v -> latchClient.countDown());
        ws.close();
      }));
    }

    assertTrue(latchClient.await(10, TimeUnit.SECONDS));
    assertTrue(latchConns.await(10, TimeUnit.SECONDS));

    assertEquals(numServers, connectedServers.size());
    for (HttpServer server: servers) {
      assertTrue(connectedServers.contains(server));
    }
    assertEquals(numServers, connectCount.size());
    for (int cnt: connectCount.values()) {
      assertEquals(numConnections / numServers, cnt);
    }

    for (HttpServer server: servers) {
      server.close().await();
    }
  }

  @Test
  public void testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort() throws Exception {
    // Have a server running on a different port to make sure it doesn't interact
    HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(4321));
    theServer.webSocketHandler(ws -> {
      fail("Should not connect");
    });
    theServer.listen().await();
    testSharedServersRoundRobin();
  }

  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    // Start and stop a server on the same port/host before hand to make sure it doesn't interact
    CountDownLatch latch = new CountDownLatch(1);
    HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(4321));
    theServer.webSocketHandler(ws -> {
      fail("Should not connect");
    });
    theServer.listen().await();
    theServer.close().await();
    testSharedServersRoundRobin();
  }

  @Test
  public void testWebSocketFrameFactoryArguments() {
    assertNullPointerException(() -> io.vertx.core.http.WebSocketFrame.binaryFrame(null, true));
    assertNullPointerException(() -> io.vertx.core.http.WebSocketFrame.textFrame(null, true));
    assertNullPointerException(() -> io.vertx.core.http.WebSocketFrame.continuationFrame(null, true));
  }

  private String sha1(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA1");
      //Hash the data
      byte[] bytes = md.digest(s.getBytes("UTF-8"));
      return Base64.getEncoder().encodeToString(bytes);
    } catch (Exception e) {
      throw new InternalError("Failed to compute sha-1");
    }
  }


  private Future<NetSocket> getUpgradedNetSocket(HttpServerRequest req, String path, Map<String, String> extraResponseHeaders) {
    assertEquals(path, req.path());
    assertEquals("upgrade", req.headers().get("Connection"));
    String secHeader = req.headers().get("Sec-WebSocket-Key");
    String tmp = secHeader + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    String encoded = sha1(tmp);
    HttpServerResponse resp = req.response();
    MultiMap headers = resp.headers();
    headers.set(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE);
    headers.set("upgrade", "WebSocket");
    headers.set("connection", "upgrade");
    headers.set("sec-websocket-accept", encoded);
    if (extraResponseHeaders != null) {
      headers.addAll(extraResponseHeaders);
    }
    return req.toNetSocket();
  }

  private void testWSWriteStream(WebSocketVersion version, Checkpoint checkpoint) {

    String scheme = "http";
    String path = "/some/path";
    String query = "handshake=bar&wibble=eek";
    String uri = path + "?" + query;

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      assertEquals(DEFAULT_HTTP_HOST, ws.authority().host());
      assertEquals(DEFAULT_HTTP_PORT, ws.authority().port());
      assertEquals(scheme, ws.scheme());
      assertEquals(uri, ws.uri());
      assertEquals(path, ws.path());
      assertEquals(query, ws.query());
      assertEquals("upgrade", ws.headers().get("Connection"));
      ws.handler(data -> {
        ws.write(data);
      });
    });

    server.listen().await();

    int bsize = 100;
    int sends = 10;

    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI(path + "?" + query)
      .setVersion(version);

    client = vertx.createWebSocketClient();
    client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
      final Buffer received = Buffer.buffer();
      ws.handler(data -> {
        received.appendBuffer(data);
        if (received.length() == bsize * sends) {
          ws.close();
          checkpoint.succeed();
        }
      });
      final Buffer sent = Buffer.buffer();
      for (int i = 0; i < sends; i++) {
        Buffer buff = Buffer.buffer(TestUtils.randomByteArray(bsize));
        ws.write(buff);
        sent.appendBuffer(buff);
      }
    }));
  }

  private void testWSFrames(boolean binary, WebSocketVersion version, Checkpoint checkpoint) {
    String scheme = "http";
    String path = "/some/path";
    String query = "handshake=bar&wibble=eek";
    String uri = path + "?" + query;

    // version 0 doesn't support continuations so we just send 1 frame per message
    int frames = version == WebSocketVersion.V00 ? 1: 10;

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      assertEquals(DEFAULT_HTTP_HOST, ws.authority().host());
      assertEquals(DEFAULT_HTTP_PORT, ws.authority().port());
      assertEquals(scheme, ws.scheme());
      assertEquals(uri, ws.uri());
      assertEquals(path, ws.path());
      assertEquals(query, ws.query());
      assertEquals("upgrade", ws.headers().get("Connection"));
      AtomicInteger count = new AtomicInteger();
      ws.frameHandler(frame -> {
        if (frame.isClose()) {
          checkpoint.succeed();
        } else {
          if (count.get() == 0) {
            if (binary) {
              assertTrue(frame.isBinary());
              assertFalse(frame.isText());
            } else {
              assertFalse(frame.isBinary());
              assertTrue(frame.isText());
            }
            assertFalse(frame.isContinuation());
          } else {
            assertFalse(frame.isBinary());
            assertFalse(frame.isText());
            assertTrue(frame.isContinuation());
          }
          if (count.get() == frames - 1) {
            assertTrue(frame.isFinal());
          } else {
            assertFalse(frame.isFinal());
          }
          ws.writeFrame(frame);
          if (count.incrementAndGet() == frames) {
            count.set(0);
          }
        }
      });
    });

    server.listen().await();

    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI(path + "?" + query)
      .setVersion(version);

    client = vertx.createWebSocketClient();

    int bsize = 100;
    int msgs = 10;

    Future<WebSocket> webSocketFuture = client.connect(options);
    webSocketFuture.onComplete(TestUtils.onSuccess(ws -> {
      final List<Buffer> sent = new ArrayList<>();
      final List<Buffer> received = new ArrayList<>();

      MultiMap headers = ws.headers();
      String webSocketLocation = headers.get("sec-websocket-location"); // HERE
      if (version == WebSocketVersion.V00) {
        assertEquals("ws://" + DEFAULT_HTTP_HOST_AND_PORT + uri, webSocketLocation);
      } else {
        assertNull(webSocketLocation);
      }

      AtomicReference<Buffer> currentReceived = new AtomicReference<>(Buffer.buffer());
      ws.frameHandler(frame -> {
        //received.appendBuffer(frame.binaryData());
        currentReceived.get().appendBuffer(frame.binaryData());
        if (frame.isFinal()) {
          received.add(currentReceived.get());
          currentReceived.set(Buffer.buffer());
        }
        if (received.size() == msgs) {
          int pos = 0;
          for (Buffer rec : received) {
            assertEquals(rec, sent.get(pos++));
          }
          ws.close();
        }
      });

      AtomicReference<Buffer> currentSent = new AtomicReference<>(Buffer.buffer());
      for (int i = 0; i < msgs; i++) {
        for (int j = 0; j < frames; j++) {
          Buffer buff;
          io.vertx.core.http.WebSocketFrame frame;
          if (binary) {
            buff = Buffer.buffer(TestUtils.randomByteArray(bsize));
            if (j == 0) {
              frame = io.vertx.core.http.WebSocketFrame.binaryFrame(buff, false);
            } else {
              frame = io.vertx.core.http.WebSocketFrame.continuationFrame(buff, j == frames - 1);
            }
          } else {
            String str = randomAlphaString(bsize);
            buff = Buffer.buffer(str);
            if (j == 0) {
              frame = io.vertx.core.http.WebSocketFrame.textFrame(str, false);
            } else {
              frame = io.vertx.core.http.WebSocketFrame.continuationFrame(buff, j == frames - 1);
            }
          }
          currentSent.get().appendBuffer(buff);
          ws.writeFrame(frame);
          if (j == frames - 1) {
            sent.add(currentSent.get());
            currentSent.set(Buffer.buffer());
          }
        }
      }
    }));

  }

  @Test
  public void testWriteFinalTextFrame(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    testWriteFinalFrame(false, checkpoint1, checkpoint2);
  }

  @Test
  public void testWriteFinalBinaryFrame(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    testWriteFinalFrame(true, checkpoint1, checkpoint2);
  }

  private void testWriteFinalFrame(boolean binary, Checkpoint checkpoint1, Checkpoint checkpoint2) {
    String text = TestUtils.randomUnicodeString(100);
    Buffer data = TestUtils.randomBuffer(100);

    Consumer<io.vertx.core.http.WebSocketFrame> frameConsumer = frame -> {
      if (binary) {
        assertTrue(frame.isBinary());
        assertFalse(frame.isText());
        assertEquals(data, frame.binaryData());
      } else {
        assertFalse(frame.isBinary());
        assertTrue(frame.isText());
        assertEquals(text, frame.textData());
      }
      assertTrue(frame.isFinal());
    };

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws ->
      ws.frameHandler(frame -> {
        if (frame.isClose()) {
          checkpoint1.succeed();
        } else {
          frameConsumer.accept(frame);
          if (binary) {
            ws.writeFinalBinaryFrame(frame.binaryData());
          } else {
            ws.writeFinalTextFrame(frame.textData());
          }
        }
      })
    );

    server.listen().await();

    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
        ws.frameHandler(frame -> {
          if (frame.isClose()) {
            checkpoint2.succeed();
          } else {
            frameConsumer.accept(frame);
            ws.close();
          }
        });
        if (binary) {
          ws.writeFinalBinaryFrame(data);
        } else {
          ws.writeFinalTextFrame(text);
        }
      })
    );

  }

  private void testContinuationWriteFromConnectHandler(WebSocketVersion version, Checkpoint checkpoint) {
    String path = "/some/path";
    String firstFrame = "AAA";
    String continuationFrame = "BBB";

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      getUpgradedNetSocket(req, path, null).onComplete(TestUtils.onSuccess(sock -> {
        // Let's write a Text frame raw
        Buffer buff = Buffer.buffer();
        buff.appendByte((byte) 0x01); // Incomplete Text frame
        buff.appendByte((byte) firstFrame.length());
        buff.appendString(firstFrame);
        sock.write(buff);

        buff = Buffer.buffer();
        buff.appendByte((byte) (0x00 | 0x80)); // Complete continuation frame
        buff.appendByte((byte) continuationFrame.length());
        buff.appendString(continuationFrame);
        sock.write(buff);
      }));
    });

    server.listen().await();

    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI(path)
      .setVersion(version);
    // We set 0 closing timeout as the server will not respond with an echo frame nor close the connection
    client = vertx.createWebSocketClient(new WebSocketClientOptions().setClosingTimeout(0));
    vertx.runOnContext(v -> {
      client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
        AtomicBoolean receivedFirstFrame = new AtomicBoolean();
        ws.frameHandler(received -> {
          Buffer receivedBuffer = Buffer.buffer(received.textData());
          if (!received.isFinal()) {
            assertEquals(firstFrame, receivedBuffer.toString());
            receivedFirstFrame.set(true);
          } else if (receivedFirstFrame.get() && received.isFinal()) {
            assertEquals(continuationFrame, receivedBuffer.toString());
            ws.close();
            checkpoint.succeed();
          }
        });
      }));
    });
  }

  private void testWriteFromConnectHandler(WebSocketVersion version, Checkpoint checkpoint) {

    String path = "/some/path";
    Buffer buff = Buffer.buffer("AAA");

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      assertEquals(path, ws.path());
      ws.writeFrame(io.vertx.core.http.WebSocketFrame.binaryFrame(buff, true));
    });

    server.listen().await();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI(path)
      .setVersion(version);
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
        Buffer received = Buffer.buffer();
        ws.handler(data -> {
          received.appendBuffer(data);
          if (received.length() == buff.length()) {
            assertEquals(buff, received);
            ws.close();
            checkpoint.succeed();
          }
        });
      }));
    });
  }

  @Test
  // Test normal negotiation of WebSocket compression
  public void testNormalWSDeflateFrameCompressionNegotiation(Checkpoint checkpoint) {
    String path = "/some/path";
    Buffer buff = Buffer.buffer("AAA");

    // Server should have basic compression enabled by default,
    // client needs to ask for it
    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT))
        .webSocketHandler(ws -> {
          assertEquals("upgrade", ws.headers().get("Connection"));
          assertEquals("deflate-frame", ws.headers().get("sec-websocket-extensions"));
          ws.writeFrame(io.vertx.core.http.WebSocketFrame.binaryFrame(buff, true));
        });

    server.listen().await();
    WebSocketClientOptions options = new WebSocketClientOptions();
    options.setTryUsePerFrameCompression(true);
    client = vertx.createWebSocketClient(options);
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, path).onComplete(TestUtils.onSuccess(ws -> {
        final Buffer received = Buffer.buffer();
        ws.handler(data -> {
          received.appendBuffer(data);
          if (received.length() == buff.length()) {
            assertEquals(buff, received);
            ws.close();
            checkpoint.succeed();
          }
        });
      }));
    });
  }

  @Test
  // Test normal negotiation of WebSocket compression
  public void testNormalWSPermessageDeflateCompressionNegotiation(Checkpoint checkpoint) {
    String path = "/some/path";
    Buffer buff = Buffer.buffer("AAA");

    // Server should have basic compression enabled by default,
    // client needs to ask for it
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      assertEquals("upgrade", ws.headers().get("Connection"));
      assertEquals("permessage-deflate", ws.headers().get("sec-websocket-extensions"));
      ws.writeFrame(io.vertx.core.http.WebSocketFrame.binaryFrame(buff, true));
    });

    server.listen().await();
    WebSocketClientOptions options = new WebSocketClientOptions();
    options.setTryUsePerMessageCompression(true);
    client = vertx.createWebSocketClient(options);
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, path).onComplete(TestUtils.onSuccess(ws -> {
        final Buffer received = Buffer.buffer();
        ws.handler(data -> {
          received.appendBuffer(data);
          if (received.length() == buff.length()) {
            assertEquals(buff, received);
            ws.close();
            checkpoint.succeed();
          }
        });
      }));
    });
  }

  @Test
  // Test if WebSocket compression is enabled by checking that the switch protocols response header contains the requested compression
  public void testWSPermessageDeflateCompressionEnabled(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    HttpClient client = vertx.createHttpClient(new PoolOptions().setHttp1MaxSize(1));
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setWebSocketClosingTimeout(0)).webSocketHandler(ws -> {
      assertEquals("upgrade", ws.headers().get("Connection"));
      assertEquals("permessage-deflate", ws.headers().get("sec-websocket-extensions"));
      checkpoint1.succeed();
    });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).await();
    client.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI("/")).onComplete(TestUtils.onSuccess(req -> {
      req.putHeader("origin", DEFAULT_HTTP_HOST)
        .putHeader("Upgrade", "Websocket")
        .putHeader("Connection", "upgrade")
        .putHeader("Sec-WebSocket-Extensions", "permessage-deflate")
        .send().onComplete(TestUtils.onSuccess(resp -> {
          assertEquals(101, resp.statusCode());
          assertEquals("permessage-deflate", resp.headers().get("sec-websocket-extensions"));
          checkpoint2.succeed();
        }));
    }));
  }

  @Test
  // Test server accepting no compression
  public void testConnectWithWebSocketCompressionDisabled(Checkpoint checkpoint) {
    String path = "/some/path";
    Buffer buff = Buffer.buffer("AAA");

    // Server should have basic compression enabled by default,
    // client needs to ask for it
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setPerFrameWebSocketCompressionSupported(false)
      .setPerMessageWebSocketCompressionSupported(false)
    ).webSocketHandler(ws -> {
      assertEquals("upgrade", ws.headers().get("Connection"));
      assertNull(ws.headers().get("sec-websocket-extensions"));
      ws.writeFrame(io.vertx.core.http.WebSocketFrame.binaryFrame(buff,  true));
    });

    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, path).onComplete(TestUtils.onSuccess(ws -> {
        final Buffer received = Buffer.buffer();
        ws.handler(data -> {
          received.appendBuffer(data);
          if (received.length() == buff.length()) {
            assertEquals(buff, received);
            ws.close();
            checkpoint.succeed();
          }
        });
      }));
    });
  }

  private void testValidSubProtocol(WebSocketVersion version, Checkpoint checkpoint) {
    String path = "/some/path";
    List<String> clientSubProtocols = Arrays.asList("clientproto", "commonproto");
    List<String> serverSubProtocols = Arrays.asList("serverproto", "commonproto");
    Buffer buff = Buffer.buffer("AAA");
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setWebSocketSubProtocols(serverSubProtocols)).webSocketHandler(ws -> {
      assertEquals(path, ws.path());
      assertEquals("commonproto", ws.subProtocol());
      ws.writeFrame(io.vertx.core.http.WebSocketFrame.binaryFrame(buff, true));
    });
    server.listen().await();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI(path)
      .setVersion(version)
      .setSubProtocols(clientSubProtocols);
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
        assertEquals("commonproto", ws.subProtocol());
        final Buffer received = Buffer.buffer();
        ws.handler(data -> {
          received.appendBuffer(data);
          if (received.length() == buff.length()) {
            assertEquals(buff, received);
            ws.close();
            checkpoint.succeed();
          }
        });
      }));
    });
  }

  private void testInvalidSubProtocol(WebSocketVersion version, Checkpoint checkpoint) {
    String path = "/some/path";
    String subProtocol = "myprotocol";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).addWebSocketSubProtocol("invalid")).webSocketHandler(ws -> {
    });
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI(path)
      .setVersion(version)
      .addSubProtocol(subProtocol);
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(options).onComplete(TestUtils.onFailure(err -> {
      // Should fail
      checkpoint.succeed();
    }));
  }

  BiConsumer<HttpClient, Handler<AsyncResult<HttpClientResponse>>> INVALID_MISSING_CONNECTION_HEADER = (client, handler) -> {
    client.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setURI("/some/path")).onComplete(TestUtils.onSuccess(req -> {
        req
          .putHeader("Upgrade", "Websocket")
          .send().onComplete(handler);
    }));
  };


  @Test
  public void testInvalidMissingConnectionHeader(Checkpoint checkpoint) {
    testInvalidHandshake(INVALID_MISSING_CONNECTION_HEADER, false, false,400, checkpoint);
  }

  @Test
  public void testInvalidMissingConnectionHeaderRequestUpgrade(Checkpoint checkpoint) {
    testInvalidHandshake(INVALID_MISSING_CONNECTION_HEADER, false, true,400, checkpoint);
  }

  BiConsumer<HttpClient, Handler<AsyncResult<HttpClientResponse>>> INVALID_HTTP_METHOD = (client, handler) -> {

    client.request(new RequestOptions()
      .setMethod(HttpMethod.HEAD)
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setURI("/some/path")).onComplete(TestUtils.onSuccess(req -> {
        req.putHeader("Upgrade", "Websocket")
          .putHeader("Connection", "Upgrade");
        req.send().onComplete(handler);
    }));
  };

  @Test
  public void testInvalidMethod(Checkpoint checkpoint) {
    testInvalidHandshake(INVALID_HTTP_METHOD, false, false,405, checkpoint);
  }

  @Test
  public void testInvalidMethodRequestUpgrade(Checkpoint checkpoint) {
    testInvalidHandshake(INVALID_HTTP_METHOD, false, true,405, checkpoint);
  }

  BiConsumer<HttpClient, Handler<AsyncResult<HttpClientResponse>>> INVALID_URI = (client, handler) -> {
    client.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setURI(":")).onComplete(TestUtils.onSuccess(req -> {
        req
          .putHeader("Upgrade", "Websocket")
          .putHeader("Connection", "Upgrade");
        req.send().onComplete(handler);
    }));
  };

  @Test
  public void testInvalidUri(Checkpoint checkpoint) {
    testInvalidHandshake(INVALID_URI, false, false,400, checkpoint);
  }

  @Test
  public void testInvalidUriRequestUpgrade(Checkpoint checkpoint) {
    testInvalidHandshake(INVALID_URI, false, true,400, checkpoint);
  }

  BiConsumer<HttpClient, Handler<AsyncResult<HttpClientResponse>>> INVALID_WEBSOCKET_VERSION = (client, handler) -> {

    client.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setURI("/some/path")).onComplete(TestUtils.onSuccess(req -> {
        req
          .putHeader("Upgrade", "Websocket")
          .putHeader("Sec-Websocket-Version", "15")
          .putHeader("Connection", "Upgrade")
          .send().onComplete(handler);
    }));
  };

  @Test
  public void testInvalidWebSocketVersion(Checkpoint checkpoint) {
    testInvalidHandshake(INVALID_WEBSOCKET_VERSION, false, false,426, checkpoint);
  }

  @Test
  public void testInvalidWebSocketVersionRequestUpgrade(Checkpoint checkpoint) {
    testInvalidHandshake(INVALID_WEBSOCKET_VERSION, false, true,426, checkpoint);
  }

  BiConsumer<HttpClient, Handler<AsyncResult<HttpClientResponse>>> HANDSHAKE_EXCEPTION = (client, handler) -> {
    client.request(new RequestOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setURI("/some/path")).onComplete(TestUtils.onSuccess(req -> {
        req
          .putHeader("Upgrade", "Websocket")
          .putHeader("Sec-Websocket-Version", "13")
          .putHeader("Connection", "Upgrade")
          .send()
          .onComplete(handler);
    }));
  };

  @Test
  public void testHandshakeException(Checkpoint checkpoint) {
    testInvalidHandshake(HANDSHAKE_EXCEPTION, true, false,400, checkpoint);
  }

  @Test
  public void testHandshakeExceptionRequestUpgrade(Checkpoint checkpoint) {
    testInvalidHandshake(HANDSHAKE_EXCEPTION, true, true,400, checkpoint);
  }

  // Check client response with the ws handler
  private void testInvalidHandshake(BiConsumer<HttpClient, Handler<AsyncResult<HttpClientResponse>>> requestProvider,
                                    boolean expectEvent,
                                    boolean upgradeRequest,
                                    int expectedStatus, Checkpoint checkpoint) {
    HttpClient client = vertx.createHttpClient(new PoolOptions().setHttp1MaxSize(1));
    if (upgradeRequest) {
      server = vertx.createHttpServer()
        .webSocketHandler(ws -> {
          // Check we can get notified
          // handshake fails after this method returns and does not reject the ws
          assertTrue(expectEvent);
        })
        .requestHandler(req -> {
          req.response().end();
        });
    } else {
      AtomicBoolean first = new AtomicBoolean();
      server = vertx.createHttpServer().requestHandler(req -> {
        if (first.compareAndSet(false, true)) {
          try {
            req.toWebSocket();
          } catch (Exception e) {
            // Expected
          }
        } else {
          req.response().end();
        }
      });
    }
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).onComplete(TestUtils.onSuccess(s -> {
      requestProvider.accept(client, TestUtils.onSuccess(resp -> {
        assertEquals(expectedStatus, resp.statusCode());
        resp.endHandler(v1 -> {
          // Make another request to check the connection remains usable
          client.request(new RequestOptions()
            .setPort(DEFAULT_HTTP_PORT)
            .setHost(DEFAULT_HTTP_HOST)).onComplete(TestUtils.onSuccess(req2 -> {
              req2.send().onComplete(TestUtils.onSuccess(resp2 -> {
                resp2.endHandler(v2 -> {
                  checkpoint.succeed();
                });
              }));
          }));
        });
      }));
    }));
  }

  private void testReject(WebSocketVersion version, Integer rejectionStatus, int expectedRejectionStatus, String expectedBody, Checkpoint checkpoint) {

    String path = "/some/path";

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .webSocketHandler(ws -> {})
      .webSocketHandshakeHandler(ws -> {
      assertEquals(path, ws.path());
      if (rejectionStatus != null) {
        ws.reject(rejectionStatus);
      } else {
        ws.reject();
      }
    });

    server.listen().onComplete(TestUtils.onSuccess(s -> {
      WebSocketConnectOptions options = new WebSocketConnectOptions()
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI(path)
        .setVersion(version);
      client = vertx.createWebSocketClient();
      client.connect(options).onComplete(TestUtils.onFailure(t -> {
        assertTrue(t instanceof UpgradeRejectedException);
        UpgradeRejectedException rejection = (UpgradeRejectedException) t;
        assertEquals(expectedRejectionStatus, rejection.getStatus());
        assertEquals("" + expectedBody.length(), rejection.getHeaders().get(HttpHeaders.CONTENT_LENGTH));
        assertEquals(expectedBody, rejection.getBody().toString());
        checkpoint.succeed();
      }));
    }));
  }

  @Test
  public void testAsyncAccept(Checkpoint checkpoint) {
    AtomicBoolean resolved = new AtomicBoolean();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .webSocketHandler(ws -> {

      })
      .webSocketHandshakeHandler(handshake -> {
      vertx.setTimer(500, id -> {
        resolved.set(true);
        handshake.accept();
      });
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/some/path").onComplete(TestUtils.onSuccess(ws -> {
      assertTrue(resolved.get());
      checkpoint.succeed();
    }));
  }

  @Test
  public void testServerClose(Checkpoint checkpoint) {
    client = vertx.createWebSocketClient();
    testClose(false, true, true, checkpoint);
  }

  @Test
  public void testClientClose(Checkpoint checkpoint) {
    client = vertx.createWebSocketClient();
    testClose(true, false, true, checkpoint);
  }

  @Test
  public void testClientAndServerClose(Checkpoint checkpoint) {
    client = vertx.createWebSocketClient();
    testClose(true, false, true, checkpoint);
  }

  @Test
  public void testConnectionClose(Checkpoint checkpoint) {
    client = vertx.createWebSocketClient(new WebSocketClientOptions().setIdleTimeout(1));
    testClose(false, false, false, checkpoint);
  }

  public void testClose(boolean closeClient, boolean closeServer, boolean regularClose, Checkpoint checkpoint) {
    CountDownLatch latch = checkpoint.asLatch(4);
    Consumer<WebSocketBase> test = ws -> {
      assertFalse(ws.isClosed());
      AtomicInteger cnt = new AtomicInteger();
      ws.exceptionHandler(err -> {
        if (regularClose) {
          fail();
        } else if (cnt.getAndIncrement() == 0) {
          latch.countDown();
        }
      });
      ws.endHandler(v -> {
        if (regularClose) {
          latch.countDown();
        } else {
          fail();
        }
      });
      ws.closeHandler(v -> {
        assertTrue(ws.isClosed());
        try {
          ws.close();
        } catch (Exception e) {
          fail();
        }
        latch.countDown();
      });
    };
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      test.accept(ws);
      if (closeServer) {
        ws.close();
      }
    });
    server.listen().await();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/some/path").onComplete(
        TestUtils.onSuccess(ws -> {
          test.accept(ws);
          if (closeClient) {
            ws.close();
          }
        }));
    });
  }

  @Test
  public void testCloseBeforeHandshake(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      req.connection().close();
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/some/path").onComplete(TestUtils.onFailure(err -> {
      checkpoint.succeed();
    }));
  }

  @Test
  public void testRequestEntityTooLarge(Checkpoint checkpoint) {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> fail());
    server.listen().await();
    HttpClient client = vertx.createHttpClient();
    client.request(new RequestOptions()
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT)
      .setURI(path)).onComplete(TestUtils.onSuccess(req -> req.putHeader("Upgrade", "Websocket")
      .putHeader("Connection", "Upgrade")
      .send(TestUtils.randomBuffer(8192 + 1)).onComplete(TestUtils.onSuccess(resp -> {
        assertEquals(413, resp.statusCode());
        resp.request().connection().closeHandler(v -> {
          checkpoint.succeed();
        });
      }))));
  }

  @Test
  public void testWriteMessageHybi00(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    testWriteMessage(256, WebSocketVersion.V00, checkpoint1, checkpoint2);
  }

  @Test
  public void testWriteFragmentedMessage1Hybi00(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    testWriteMessage(65536 + 256, WebSocketVersion.V00, checkpoint1, checkpoint2);
  }

  @Test
  public void testWriteFragmentedMessage2Hybi00(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    testWriteMessage(65536 + 65536 + 256, WebSocketVersion.V00, checkpoint1, checkpoint2);
  }

  @Test
  public void testWriteMessageHybi08(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    testWriteMessage(256, WebSocketVersion.V08, checkpoint1, checkpoint2);
  }

  @Test
  public void testWriteFragmentedMessage1Hybi08(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    testWriteMessage(65536 + 256, WebSocketVersion.V08, checkpoint1, checkpoint2);
  }

  @Test
  public void testWriteFragmentedMessage2Hybi08(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    testWriteMessage(65536 + 65536 + 256, WebSocketVersion.V08, checkpoint1, checkpoint2);
  }

  @Test
  public void testWriteMessageHybi17(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    testWriteMessage(256, WebSocketVersion.V13, checkpoint1, checkpoint2);
  }

  @Test
  public void testWriteFragmentedMessage1Hybi17(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    testWriteMessage(65536 + 256, WebSocketVersion.V13, checkpoint1, checkpoint2);
  }

  @Test
  public void testWriteFragmentedMessage2Hybi17(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    testWriteMessage(65536 + 65536 + 256, WebSocketVersion.V13, checkpoint1, checkpoint2);
  }

  private void testWriteMessage(int size, WebSocketVersion version, Checkpoint checkpoint1, Checkpoint checkpoint2) {
    client = vertx.createWebSocketClient();
    String path = "/some/path";
    byte[] expected = TestUtils.randomByteArray(size);
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      AtomicInteger count = new AtomicInteger();
      ws.writeBinaryMessage(Buffer.buffer(expected)).onComplete(TestUtils.onSuccess(v -> {
        assertEquals(1, count.incrementAndGet());
        checkpoint1.succeed();
      }));
      ws.close();
    });
    server.listen().await();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT)
      .setURI(path)
      .setVersion(version);
    vertx.runOnContext(v1 -> {
      client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
        Buffer actual = Buffer.buffer();
        ws.handler(actual::appendBuffer);
        ws.closeHandler(v2 -> {
          assertArrayEquals(expected, actual.getBytes(0, actual.length()));
          checkpoint2.succeed();
        });
      }));
    });
  }

  @Test
  public void testNonFragmentedTextMessage2Hybi00() throws InterruptedException {
      String messageToSend = randomAlphaString(256);
      testWriteSingleTextMessage(messageToSend, WebSocketVersion.V00);
  }

  @Test
  public void testFragmentedTextMessage2Hybi07() throws InterruptedException {
    String messageToSend = randomAlphaString(65536 + 65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebSocketVersion.V07);
  }

  @Test
  public void testFragmentedTextMessage2Hybi08() throws InterruptedException {
    String messageToSend = randomAlphaString(65536 + 65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebSocketVersion.V08);
  }

  @Test
  public void testFragmentedTextMessage2Hybi13() throws InterruptedException {
    String messageToSend = randomAlphaString(65536 + 65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebSocketVersion.V13);
  }

  @Test
  public void testMaxLengthFragmentedTextMessage() throws InterruptedException {
    String messageToSend = randomAlphaString(HttpServerOptions.DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE);
    testWriteSingleTextMessage(messageToSend, WebSocketVersion.V13);
  }

  @Test
  public void testFragmentedUnicodeTextMessage2Hybi07() throws InterruptedException {
    String messageToSend = TestUtils.randomUnicodeString(65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebSocketVersion.V07);
  }

  @Test
  public void testFragmentedUnicodeTextMessage2Hybi08() throws InterruptedException {
    String messageToSend = TestUtils.randomUnicodeString(65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebSocketVersion.V08);
  }

  @Test
  public void testFragmentedUnicodeTextMessage2Hybi13() throws InterruptedException {
    String messageToSend = TestUtils.randomUnicodeString(65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebSocketVersion.V13);
  }

  @Test
  public void testTooLargeMessage() throws InterruptedException {
    String messageToSend = randomAlphaString(WebSocketClientOptions.DEFAULT_MAX_MESSAGE_SIZE + 1);
    SocketMessages socketMessages = testWriteTextMessages(Collections.singletonList(messageToSend), WebSocketVersion.V13);
    List<String> receivedMessages = socketMessages.getReceivedMessages();
    List<String> expectedMessages = Collections.emptyList();
    assertEquals("Should not have received any messages", expectedMessages, receivedMessages);
    List<Throwable> receivedExceptions = socketMessages.getReceivedExceptions();
    assertEquals("Should have received a single exception", 1, receivedExceptions.size());
    assertTrue("Should have received IllegalStateException",
            receivedExceptions.get(0) instanceof IllegalStateException);
  }

  @Test
  public void testContinueAfterTooLargeMessage() throws InterruptedException {
    int shortMessageLength = WebSocketClientOptions.DEFAULT_MAX_FRAME_SIZE;
    String shortFirstMessage = randomAlphaString(shortMessageLength);
    String tooLongMiddleMessage = randomAlphaString(WebSocketClientOptions.DEFAULT_MAX_MESSAGE_SIZE * 2);
    String shortLastMessage = randomAlphaString(shortMessageLength);
    List<String> messagesToSend = Arrays.asList(shortFirstMessage, tooLongMiddleMessage, shortLastMessage);

    SocketMessages socketMessages = testWriteTextMessages(messagesToSend, WebSocketVersion.V13);
    List<String> receivedMessages = socketMessages.getReceivedMessages();
    List<String> expectedMessages = Arrays.asList(shortFirstMessage, shortLastMessage);
    assertEquals("Incorrect received messages", expectedMessages, receivedMessages);
  }

  private void testWriteSingleTextMessage(String messageToSend, WebSocketVersion version) throws InterruptedException {
    List<String> messagesToSend = Collections.singletonList(messageToSend);
    SocketMessages socketMessages = testWriteTextMessages(messagesToSend, version);
    assertEquals("Did not receive all messages", messagesToSend, socketMessages.getReceivedMessages());
    List<Throwable> expectedExceptions = Collections.emptyList();
    assertEquals("Should not have received any exceptions", expectedExceptions, socketMessages.getReceivedExceptions());
  }

  private SocketMessages testWriteTextMessages(List<String> messagesToSend, WebSocketVersion version) throws InterruptedException {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      for (String messageToSend : messagesToSend) {
        ws.writeTextMessage(messageToSend);
      }
      ws.close();
    });

    List<String> receivedMessages = new ArrayList<>();
    List<Throwable> receivedExceptions = new ArrayList<>();
    server.listen().await();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI(path)
      .setVersion(version);
    client = vertx.createWebSocketClient();
    CountDownLatch done = new CountDownLatch(1);
    vertx.runOnContext(v1 -> {
      client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
        ws.textMessageHandler(receivedMessages::add);
        ws.exceptionHandler(receivedExceptions::add);
        ws.closeHandler(v2 -> done.countDown());
      }));
    });
    TestUtils.awaitLatch(done);
    return new SocketMessages(receivedMessages, receivedExceptions);
  }

  private static class SocketMessages {
    private final List<String> receivedMessages;
    private final List<Throwable> receivedExceptions;

    public SocketMessages(List<String> receivedMessages, List<Throwable> receivedExceptions) {
      this.receivedMessages = receivedMessages;
      this.receivedExceptions = receivedExceptions;
    }

    public List<String> getReceivedMessages() {
      return receivedMessages;
    }

    public List<Throwable> getReceivedExceptions() {
      return receivedExceptions;
    }
  }

  @Test
  public void testHandshakeTimeoutFires(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    NetServer server = vertx.createNetServer()
      .connectHandler(so -> checkpoint2.succeed())
      .listen(1234, DEFAULT_HTTP_HOST)
      .await(20, TimeUnit.SECONDS);
    try {
      client = vertx.createWebSocketClient();
      WebSocketConnectOptions options = new WebSocketConnectOptions()
        .setPort(1234)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI("/")
        .setTimeout(1000);
      client.connect(options).onComplete(TestUtils.onFailure(err -> {
        assertEquals(WebSocketHandshakeException.class, err.getClass());
        checkpoint1.succeed();
      }));
      checkpoint1.awaitSuccess();
    } finally {
      server.close();
    }
  }

  @Test
  public void testHandshakeTimeoutDoesNotFire(Checkpoint checkpoint) {
    server = vertx.createHttpServer()
      .webSocketHandler(ws -> {

    });
    server
      .listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST)
      .await();
    client = vertx.createWebSocketClient(new WebSocketClientOptions().setConnectTimeout(1000));
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI("/")
      .setTimeout(1000);
    client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
      AtomicBoolean closed = new AtomicBoolean();
      ws.closeHandler(v -> closed.set(true));
      vertx.setTimer(1100, id -> {
        assertFalse(closed.get());
        checkpoint.succeed();
      });
    }));
  }

  private void connectUntilWebSocketReject(WebSocketClient client, int count, Handler<AsyncResult<Void>> doneHandler) {
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/some/path").onComplete(ar -> {
        if (ar.succeeded()) {
          if (count < 100) {
            connectUntilWebSocketReject(client, count + 1, doneHandler);
          } else {
            doneHandler.handle(Future.failedFuture(new AssertionError()));
          }
        } else {
          Throwable err = ar.cause();
          if (err instanceof UpgradeRejectedException || err instanceof IOException) {
            doneHandler.handle(Future.succeededFuture());
          } else if (count < 100) {
            connectUntilWebSocketReject(client, count + 1, doneHandler);
          } else {
            doneHandler.handle(Future.failedFuture(err));
          }
        }
      });
    });
  }

  @Test
  public void testMultipleServerClose(Checkpoint checkpoint) {
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    // We assume the endHandler and the close completion handler are invoked in the same context task
    ThreadLocal stack = new ThreadLocal();
    stack.set(true);
    server.close().onComplete(ar1 -> {
      assertNull(stack.get());
      assertTrue(Vertx.currentContext().isEventLoopContext());
      server.close().onComplete(ar2 -> {
        server.close().onComplete(ar3 -> {
          checkpoint.succeed();
        });
      });
    });
  }

  @Test
  public void testClearClientHandlersOnEnd(Checkpoint checkpoint) {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(WebSocketBase::close);
    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path).onComplete(TestUtils.onSuccess(ws -> {
        ws.endHandler(v2 -> {
          try {
            ws.endHandler(null);
            ws.exceptionHandler(null);
            ws.handler(null);
          } catch (Exception e) {
            fail("Was expecting to set to null the handlers when the socket is closed");
            return;
          }
          checkpoint.succeed();
        });
      }));
    });
  }

  @Test
  public void testWriteOnEnd(Checkpoint checkpoint) {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(WebSocketBase::close);
    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path).onComplete(TestUtils.onSuccess(ws -> {
        ws.endHandler(v2 -> {
          ws.write(Buffer.buffer("test")).onComplete(TestUtils.onFailure(err -> {
            checkpoint.succeed();
          }));
        });
      }));
    });
  }

  @Test
  public void testReceiveHttpResponseHeadersOnClient(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      handshakeWithCookie(req);
    });
    AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/some/path").onComplete(TestUtils.onSuccess(ws -> {
      MultiMap entries = ws.headers();
      assertNotNull(entries);
      assertFalse(entries.isEmpty());
      assertEquals("websocket".toLowerCase(), entries.get("Upgrade").toLowerCase());
      assertEquals("upgrade".toLowerCase(), entries.get("Connection").toLowerCase());
      Set<String> cookiesToSet = new HashSet(entries.getAll("Set-Cookie"));
      assertEquals(2, cookiesToSet.size());
      assertTrue(cookiesToSet.contains("SERVERID=test-server-id"));
      assertTrue(cookiesToSet.contains("JSONID=test-json-id"));
      webSocketRef.set(ws);
      checkpoint.succeed();
    }));
  }

  @Test
  public void testUpgrade(Checkpoint checkpoint) {
    testUpgrade(false, checkpoint);
  }

  @Test
  public void testUpgradeDelayed(Checkpoint checkpoint) {
    testUpgrade(true, checkpoint);
  }

  private void testUpgrade(boolean delayed, Checkpoint checkpoint) {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    server.requestHandler(request -> {
      Runnable runner = () -> {
        request.toWebSocket().onComplete(TestUtils.onSuccess(ws -> {
          HttpServerResponse response = request.response();
          assertTrue(response.ended());
          try {
            response.putHeader("foo", "bar");
            fail();
          } catch (IllegalStateException ignore) {
          }
          try {
            response.end();
            fail();
          } catch (IllegalStateException ignore) {
          }
          ws.handler(buff -> {
            ws.write(Buffer.buffer("helloworld"));
            ws.close();
          });
        }));
      };
      if (delayed) {
        // This tests the case where the last http content comes of the request (its not full) comes in
        // before the upgrade has happened and before HttpServerImpl.expectWebsockets is true
        request.pause();
        vertx.runOnContext(v -> {
          runner.run();
        });
      } else {
        runner.run();
      }
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v1 -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path).onComplete(TestUtils.onSuccess(ws -> {
        Buffer buff = Buffer.buffer();
        ws.handler(buff::appendBuffer);
        ws.endHandler(v2 -> {
          // Last two bytes are status code payload
          assertEquals("helloworld", buff.toString("UTF-8"));
          checkpoint.succeed();
        });
        ws.write(Buffer.buffer("foo"));
      }));
    });
  }

  @Test
  public void testUpgradeFailure(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    server.requestHandler(request -> {
      request.toWebSocket().onComplete(TestUtils.onFailure(err -> {
        checkpoint.succeed();
      }));
    });
    server.listen().await();
    HttpClient client = vertx.createHttpClient();
    handshake(client, req -> {
      req.putHeader(HttpHeaders.CONTENT_LENGTH, "100");
      req.writeHead().onComplete(TestUtils.onSuccess(v -> {
        req.connection().close();
      }));
    });
  }

  @Test
  public void testUnmaskedFrameRequest(Checkpoint checkpoint){

    client = vertx.createWebSocketClient(new WebSocketClientOptions().setSendUnmaskedFrames(true));
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setAcceptUnmaskedFrames(true));
    server.requestHandler(req -> {
      req.response().setChunked(true).write("connect");
    });
    server.webSocketHandler(ws -> {

      ws.handler(new Handler<Buffer>() {
        public void handle(Buffer data) {
          assertEquals(data.toString(), "first unmasked frame");
          checkpoint.succeed();
        }
      });

    });
    server.listen().onComplete(TestUtils.onSuccess(server -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
        ws.writeFinalTextFrame("first unmasked frame");
      }));
    }));
  }

  @Test
  public void testInvalidUnmaskedFrameRequest(Checkpoint checkpoint) {

    client = vertx.createWebSocketClient(new WebSocketClientOptions().setSendUnmaskedFrames(true));
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setAcceptUnmaskedFrames(false));
    server.requestHandler(req -> {
      req.response().setChunked(true).write("connect");
    });
    server.webSocketHandler(ws -> {

      ws.exceptionHandler(exception -> {
        // Will receive decode error and close error
        ws.exceptionHandler(null);
        checkpoint.succeed();
      });

      ws.handler(result -> {
        fail("Cannot decode unmasked message because I require masked frame as configured");
      });
    });

    server.listen().await();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
      ws.writeFinalTextFrame("first unmasked frame");
    }));

  }

  @Test
  public void testUpgradeInvalidRequest(Checkpoint checkpoint1, Checkpoint checkpoint2) {

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    server.requestHandler(request -> {
      request.toWebSocket().onComplete(TestUtils.onFailure(err -> {
        checkpoint1.succeed();
      }));
      request.response().end();
    });
    server.listen().await();
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .compose(HttpClientRequest::send)
      .onComplete(TestUtils.onSuccess(resp -> {
        checkpoint2.succeed();
      }));
  }

  @Test
  public void testRaceConditionWithWebSocketClientEventLoop(Checkpoint checkpoint) {
    testRaceConditionWithWebSocketClient(vertx.getOrCreateContext(), checkpoint);
  }

  @Test
  public void testRaceConditionWithWebSocketClientWorker(Checkpoint checkpoint) throws Exception {
    CompletableFuture<Context> fut = new CompletableFuture<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        fut.complete(context);
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)).onComplete(ar -> {
      if (ar.failed()) {
        fut.completeExceptionally(ar.cause());
      }
    });
    testRaceConditionWithWebSocketClient(fut.get(), checkpoint);
  }

  private Future<NetSocket> handshakeWithCookie(HttpServerRequest req) {
    return ((Http1ServerConnection)req.connection()).netSocket().compose(so -> {
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] inputBytes = (req.getHeader("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes();
        digest.update(inputBytes);
        byte[] hashedBytes = digest.digest();
        byte[] accept = Base64.getEncoder().encode(hashedBytes);
        Buffer data = Buffer.buffer();
        data.appendString("HTTP/1.1 101 Switching Protocols\r\n");
        data.appendString("Upgrade: websocket\r\n");
        data.appendString("Connection: upgrade\r\n");
        data.appendString("Sec-WebSocket-Accept: " + new String(accept) + "\r\n");
        data.appendString("Set-Cookie: SERVERID=test-server-id\r\n");
        data.appendString("Set-Cookie: JSONID=test-json-id\r\n");
        data.appendString("\r\n");
        so.write(data);
        return Future.succeededFuture(so);
      } catch (NoSuchAlgorithmException e) {
        req.response().setStatusCode(500).end();
        return Future.failedFuture(e);
      }
    });
  }

  private Future<NetSocket> handshake(HttpServerRequest req) {
    return ((Http1ServerConnection)req.connection()).netSocket().flatMap(so -> {
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] inputBytes = (req.getHeader("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes();
        digest.update(inputBytes);
        byte[] hashedBytes = digest.digest();
        byte[] accept = Base64.getEncoder().encode(hashedBytes);
        Buffer data = Buffer.buffer();
        data.appendString("HTTP/1.1 101 Switching Protocols\r\n");
        data.appendString("Upgrade: websocket\r\n");
        data.appendString("Connection: upgrade\r\n");
        data.appendString("Sec-WebSocket-Accept: " + new String(accept) + "\r\n");
        data.appendString("\r\n");
        so.write(data);
        return Future.succeededFuture(so);
      } catch (NoSuchAlgorithmException e) {
        req.response().setStatusCode(500).end();
        return Future.failedFuture(e);
      }
    });
  }

  private void handshake(HttpClient client, Handler<HttpClientRequest> handler) {
    client.request(new RequestOptions()
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT)
      .setURI("/")
    ).onComplete(TestUtils.onSuccess(req -> {
      req
        .putHeader("Upgrade", "websocket")
        .putHeader("Connection", "Upgrade")
        .putHeader("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
        .putHeader("Sec-WebSocket-Protocol", "chat")
        .putHeader("Sec-WebSocket-Version", "13")
        .putHeader("Origin", "http://example.com");
        handler.handle(req);
    }));
  }

  private void testRaceConditionWithWebSocketClient(Context context, Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    // Handcrafted websocket handshake for sending a frame immediately after the handshake
    server.requestHandler(req -> {
      handshake(req).onComplete(TestUtils.onSuccess(so -> {
        so.write(Buffer.buffer(new byte[]{
          (byte) 0x82,
          0x05,
          0x68,
          0x65,
          0x6c,
          0x6c,
          0x6f,
        }));
      }));
    });
    server.listen().onComplete(TestUtils.onSuccess(s -> {
      context.runOnContext(v -> {
        client = vertx.createWebSocketClient();
        client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
          ws.handler(buf -> {
            assertEquals("hello", buf.toString());
            checkpoint.succeed();
          });
        }));
      });
    }));
  }

  @Test
  public void testRaceConditionWithWebSocketClientWorker2(Checkpoint checkpoint) {
    int size = VertxOptions.DEFAULT_WORKER_POOL_SIZE - 4;
    List<Context> workers = IntStream.range(0, size + 1)
      .mapToObj(val -> ((VertxInternal)vertx).createWorkerContext())
      .collect(Collectors.toList());
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    server.webSocketHandler(ws -> {
      ws.write(Buffer.buffer("hello"));
    });
    server.listen().onComplete(TestUtils.onSuccess(s -> {
      client = vertx.createWebSocketClient();
      workers.get(0).runOnContext(v -> {
        client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
          ws.handler(buf -> {
            assertEquals("hello", buf.toString());
            checkpoint.succeed();
          });
        }));
      });
    }));
  }

  @Test
  public void testWorker(Checkpoint checkpoint1, Checkpoint checkpoint2) {

    DeploymentOptions deploymentOptions = new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER);
    vertx.deployVerticle(() -> new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) {
        server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
        server.webSocketHandler(ws -> {
          assertTrue(Context.isOnWorkerThread());
          ws.handler(msg -> {
            assertTrue(Context.isOnWorkerThread());
            ws.write(Buffer.buffer("pong"));
          });
          ws.endHandler(v -> {
            assertTrue(Context.isOnWorkerThread());
            checkpoint1.succeed();
          });
        });
        server.listen()
          .<Void>mapEmpty()
          .onComplete(startPromise);
      }
    }, deploymentOptions).onComplete(TestUtils.onSuccess(serverID -> {
      vertx.deployVerticle(() -> new AbstractVerticle() {
        @Override
        public void start() {
          client = vertx.createWebSocketClient();
          Future<WebSocket> fut = client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/");
          fut.onComplete(TestUtils.onSuccess(ws -> {
            assertTrue(Context.isOnWorkerThread());
            ws.write(Buffer.buffer("ping"));
            ws.handler(buf -> {
              assertTrue(Context.isOnWorkerThread());
              ws.end();
            });
            ws.endHandler(v -> {
              assertTrue(Context.isOnWorkerThread());
              checkpoint2.succeed();
            });
          }));
        }
      }, deploymentOptions).onComplete(TestUtils.onSuccess(id -> {}));
    }));
  }

  @Test
  public void httpClientWebSocketConnectionFailureHandlerShouldBeCalled(Checkpoint checkpoint) {
    int port = 7867;
    client = vertx.createWebSocketClient();
    client.connect(port, "localhost", "").onComplete(TestUtils.onFailure(err -> {
      checkpoint.succeed();
    }));
  }

  @Test
  public void testClientWebSocketWithHttp2Client(Checkpoint checkpoint) {
    client = vertx.createWebSocketClient();
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setHttp2ClearTextUpgrade(false).setProtocolVersion(HttpVersion.HTTP_2));
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    server.requestHandler(req -> {
      req.response().setChunked(true).write("connect");
    });
    server.webSocketHandler(ws -> {
      ws.writeFinalTextFrame("ok");
    });
    server.listen().await();
    client.request(new RequestOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST))
      .onComplete(TestUtils.onSuccess(req -> {
        req.send().onComplete(TestUtils.onSuccess(resp -> {
          this.client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
            ws.handler(buff -> {
              assertEquals("ok", buff.toString());
              checkpoint.succeed();
            });
          }));
        }));
      }));
  }

  @Test
  public void testClientWebSocketConnectionCloseOnBadResponseWithKeepalive() throws Throwable {
    // issue #1757
    doTestClientWebSocketConnectionCloseOnBadResponse(true);
  }

  @Test
  public void testClientWebSocketConnectionCloseOnBadResponseWithoutKeepalive() throws Throwable {
    doTestClientWebSocketConnectionCloseOnBadResponse(false);
  }

  final BlockingQueue<Throwable> resultQueue = new ArrayBlockingQueue<Throwable>(10);

  void addResult(Throwable result) {
    try {
      resultQueue.put(result);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void doTestClientWebSocketConnectionCloseOnBadResponse(boolean keepAliveInOptions) throws Throwable {
    final Exception serverGotCloseException = new Exception();

    netServer = vertx.createNetServer().connectHandler(sock -> {
      final Buffer fullReq = Buffer.buffer(230);
      sock.handler(b -> {
        fullReq.appendBuffer(b);
        String reqPart = b.toString();
        if (fullReq.toString().contains("\r\n\r\n")) {
          try {
            String content = "0123456789";
            content = content + content;
            content = content + content + content + content + content;
            String resp = "HTTP/1.1 200 OK\r\n";
            if (keepAliveInOptions) {
              resp += "Connection: close\r\n";
            }
            resp += "Content-Length: 100\r\n\r\n" + content;
            sock.write(Buffer.buffer(resp.getBytes("ASCII")));
          } catch (UnsupportedEncodingException e) {
            addResult(e);
          }
        }
      });
      sock.closeHandler(v -> {
        addResult(serverGotCloseException);
      });
    });
    netServer.listen().onComplete(ar -> {
      if (ar.failed()) {
        addResult(ar.cause());
        return;
      }
      NetServer server = ar.result();
      int port = server.actualPort();

      client = vertx.createWebSocketClient();
      client.connect(port, "localhost", "/").onComplete(ar2 -> {
        if (ar2.succeeded()) {
          addResult(new AssertionError("WebSocket unexpectedly connected"));
          ar2.result().close();
        } else {
          addResult(ar2.cause());
        }
      });
    });

    boolean serverGotClose = false;
    boolean clientGotCorrectException = false;
    while (!serverGotClose || !clientGotCorrectException) {
      Throwable result = resultQueue.poll(20, TimeUnit.SECONDS);
      if (result == null) {
        throw new AssertionError("Timed out waiting for expected state, current: serverGotClose = " + serverGotClose + ", clientGotCorrectException = " + clientGotCorrectException);
      } else if (result == serverGotCloseException) {
        serverGotClose = true;
      } else if (result instanceof UpgradeRejectedException
              && ((UpgradeRejectedException)result).getStatus() == 200) {
        clientGotCorrectException = true;
      } else {
        throw result;
      }
    }
  }

  @Test
  public void testClearClientSslOptions(Checkpoint checkpoint) {
    HttpServerOptions serverOptions = new HttpServerOptions().setPort(DEFAULT_HTTPS_PORT)
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get());
    WebSocketClientOptions clientOptions = new WebSocketClientOptions()
      .setTrustAll(true)
      .setVerifyHost(false);
    client = vertx.createWebSocketClient(clientOptions);
    server = vertx.createHttpServer(serverOptions).webSocketHandler(WebSocketBase::close);
    server.listen().await();
    WebSocketConnectOptions options = new WebSocketConnectOptions().setPort(DEFAULT_HTTPS_PORT).setSsl(true);
    vertx.runOnContext(v1 -> {
      client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
        ws.closeHandler(v2 -> {
          checkpoint.succeed();
        });
      }));
    });
  }

  @Test
  public void testServerWebSocketPingPong(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST));
    server.webSocketHandler(ws -> {
      ws.pongHandler(buff -> {
        assertEquals("ping", buff.toString());
        ws.close();
      });
      ws.writePing(Buffer.buffer("ping"));
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
        ws.handler(buff -> {
          fail("Should not receive a buffer");
        });
        ws.closeHandler(v2 -> {
          checkpoint.succeed();
        });
      }));
    });
  }

  @Test
  public void testWebSocketPausePing(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST));
    server.webSocketHandler(ws -> {
      ws.pongHandler(buff -> {
        assertEquals("ping", buff.toString());
        ws.close();
      });
      ws.writePing(Buffer.buffer("ping"));
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
        ws.pause();
        ws.handler(buff -> {
          fail("Should not receive a buffer");
        });
        ws.fetch(1);
        ws.endHandler(v2 -> {
          checkpoint.succeed();
        });
      }));
    });
  }

  @Test
  public void testServerWebSocketPingExceeds125Bytes(Checkpoint checkpoint) {
    testServerWebSocketPingPongCheck(255, ws -> ws.writePing(Buffer.buffer(randomAlphaString(126))), checkpoint);
  }

  @Test
  public void testServerWebSocketPongExceeds125Bytes(Checkpoint checkpoint) {
    testServerWebSocketPingPongCheck(255, ws -> ws.writePong(Buffer.buffer(randomAlphaString(126))), checkpoint);
  }

  @Test
  public void testServerWebSocketPingExceedsMaxFrameSize(Checkpoint checkpoint) {
    testServerWebSocketPingPongCheck(100, ws -> ws.writePing(Buffer.buffer(randomAlphaString(101))), checkpoint);
  }

  @Test
  public void testServerWebSocketPongExceedsMaxFrameSize(Checkpoint checkpoint) {
    testServerWebSocketPingPongCheck(100, ws -> ws.writePong(Buffer.buffer(randomAlphaString(101))), checkpoint);
  }

  private void testServerWebSocketPingPongCheck(int maxFrameSize, Function<ServerWebSocket, Future<Void>> check, Checkpoint checkpoint) {
    Pattern pattern = Pattern.compile("^P[io]ng cannot exceed maxWebSocketFrameSize or 125 bytes$");
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebSocketFrameSize(maxFrameSize));
    server.webSocketHandler(ws -> {
      ws.pongHandler(buff -> fail());
      check.apply(ws).onComplete(TestUtils.onFailure(err -> {
        Matcher matcher = pattern.matcher(err.getMessage());
        if (matcher.matches()) {
          ws.close();
        } else {
          fail("Unexpected error message" + err.getMessage());
        }
      }));
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
        ws.closeHandler(v2 -> {
          checkpoint.succeed();
        });
      }));
    });
  }

  @Test
  public void testServerWebSocketSendPingExceeds125Bytes(Checkpoint checkpoint) {
    //Netty will prevent us from encoding a pingBody greater than 126 bytes by silently throwing an error in the background
    String pingBody = randomAlphaString(126);
    int maxFrameSize = 256;
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebSocketFrameSize(maxFrameSize));
    server.webSocketHandler(ws -> {
      ws.pongHandler(buff -> fail());
      ws.writeFrame(io.vertx.core.http.WebSocketFrame.pingFrame(Buffer.buffer(pingBody)));
      vertx.setTimer(2000, id -> checkpoint.succeed());
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {}));
  }

  @Test
  public void testClientWebSocketSendPingExceeds125Bytes(Checkpoint checkpoint) {
    //Netty will prevent us from encoding a pingBody greater than 126 bytes by silently throwing an error in the background
    String pingBody = randomAlphaString(126);
    int maxFrameSize = 256;
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebSocketFrameSize(maxFrameSize));
    server.webSocketHandler(ws -> { });
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
      ws.pongHandler(buffer -> fail());
      ws.writeFrame(io.vertx.core.http.WebSocketFrame.pingFrame(Buffer.buffer(pingBody)));
      vertx.setTimer(2000, id -> checkpoint.succeed());
    }));
  }

  @Test
  public void testServerWebSocketSendPongExceeds125Bytes(Checkpoint checkpoint) {
    //Netty will prevent us from encoding a pingBody greater than 126 bytes by silently throwing an error in the background
    String pingBody = randomAlphaString(126);
    int maxFrameSize = 256;
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebSocketFrameSize(maxFrameSize));
    server.webSocketHandler(ws -> {
      ws.writeFrame(io.vertx.core.http.WebSocketFrame.pongFrame(Buffer.buffer(pingBody)));
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
        ws.pongHandler(buff -> fail());
        vertx.setTimer(2000, id -> checkpoint.succeed());
      }));
    });
  }

  @Test
  public void testClientWebSocketSendPongExceeds125Bytes(Checkpoint checkpoint) {
    //Netty will prevent us from encoding a pingBody greater than 126 bytes by silently throwing an error in the background
    String pingBody = randomAlphaString(126);
    int maxFrameSize = 256;
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebSocketFrameSize(maxFrameSize));
    server.webSocketHandler(ws -> {
      ws.pongHandler(buff -> fail());
      vertx.setTimer(2000, id -> checkpoint.succeed());
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
      ws.writeFrame(io.vertx.core.http.WebSocketFrame.pongFrame(Buffer.buffer(pingBody)));
    }));
  }

  @Test
  public void testServerWebSocketReceivePongExceedsMaxFrameSize(Checkpoint checkpoint) {
    String pingBody = randomAlphaString(113);
    Integer maxFrameSize = 64;
    Buffer ping1 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(0, maxFrameSize));
    Buffer ping2 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(maxFrameSize, pingBody.length()));

    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebSocketFrameSize(maxFrameSize));
    server.webSocketHandler(ws -> {
      List<Buffer> pongs = new ArrayList<>();
      ws.pongHandler(pong -> {
        pongs.add(pong);
        if (pongs.size() == 2) {
          assertEquals(pongs, Arrays.asList(ping1, ping2));
          checkpoint.succeed();
        }
      });
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
      try {
        ws.writeFrame(new WebSocketFrameImpl(WebSocketFrameType.PONG, ((BufferInternal)ping1.copy()).getByteBuf(), false));
        ws.writeFrame(new WebSocketFrameImpl(WebSocketFrameType.PONG, ((BufferInternal)ping2.copy()).getByteBuf(), true));
      } catch(Throwable t) {
        fail(t.getMessage());
      }
    }));
  }

  @Test
  public void testClientWebSocketReceivePongExceedsMaxFrameSize(Checkpoint checkpoint) {
    String pingBody = randomAlphaString(113);
    Integer maxFrameSize = 64;
    Buffer ping1 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(0, maxFrameSize));
    Buffer ping2 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(maxFrameSize, pingBody.length()));

    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebSocketFrameSize(maxFrameSize));
    server.webSocketHandler(ws -> {
      try {
        ws.writeFrame(new WebSocketFrameImpl(WebSocketFrameType.PONG, ((BufferInternal)ping1.copy()).getByteBuf(), false));
        ws.writeFrame(new WebSocketFrameImpl(WebSocketFrameType.PONG, ((BufferInternal)ping2.copy()).getByteBuf(), true));
      } catch(Throwable t) {
        fail(t.getMessage());
      }
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
        List<Buffer> pongs = new ArrayList<>();
        ws.pongHandler(pong -> {
          pongs.add(pong);
          if (pongs.size() == 2) {
            assertEquals(pongs, Arrays.asList(ping1, ping2));
            checkpoint.succeed();
          }
        });
      }));
    });
  }

  @Test
  public void testServerWebSocketReceivePingExceedsMaxFrameSize(Checkpoint checkpoint) {
    String pingBody = randomAlphaString(113);
    Integer maxFrameSize = 64;
    Buffer ping1 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(0, maxFrameSize));
    Buffer ping2 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(maxFrameSize, pingBody.length()));

    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebSocketFrameSize(maxFrameSize));
    server.webSocketHandler(ws -> {

    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
        List<Buffer> pongs = new ArrayList<>();
        ws.pongHandler(pong -> {
          pongs.add(pong);
          if (pongs.size() == 2) {
            assertEquals(pongs, Arrays.asList(ping1, ping2));
            checkpoint.succeed();
          }
        });
        try {
          ws.writeFrame(new WebSocketFrameImpl(WebSocketFrameType.PING, ((BufferInternal)ping1.copy()).getByteBuf(), false));
          ws.writeFrame(new WebSocketFrameImpl(WebSocketFrameType.PING, ((BufferInternal)ping2.copy()).getByteBuf(), true));
        } catch(Throwable t) {
          fail(t.getMessage());
        }
      }));
    });
  }

  @Test
  public void testClientWebSocketReceivePingExceedsMaxFrameSize(Checkpoint checkpoint) {
    String pingBody = randomAlphaString(113);
    Integer maxFrameSize = 64;
    Buffer ping1 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(0, maxFrameSize));
    Buffer ping2 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(maxFrameSize, pingBody.length()));

    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebSocketFrameSize(maxFrameSize));
    server.webSocketHandler(ws -> {

    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
        List<Buffer> pongs = new ArrayList<>();
        ws.pongHandler(pong -> {
          pongs.add(pong);
          if (pongs.size() == 2) {
            assertEquals(pongs, Arrays.asList(ping1, ping2));
            checkpoint.succeed();
          }
        });
        try {
          ws.writeFrame(new WebSocketFrameImpl(WebSocketFrameType.PING, ((BufferInternal)ping1.copy()).getByteBuf(), false));
          ws.writeFrame(new WebSocketFrameImpl(WebSocketFrameType.PING, ((BufferInternal)ping2.copy()).getByteBuf(), true));
        } catch(Throwable t) {
          fail(t.getMessage());
        }
      }));
    });
  }

  @Test
  public void testClientWebSocketPingPong(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST));
    server.webSocketHandler(ws -> {
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
      ws.pongHandler( pong -> {
        assertEquals("ping", pong.toString());
        checkpoint.succeed();
      });
      ws.writePing(Buffer.buffer("ping"));
    }));
  }

  @Test
  public void testWebSocketAbs(Checkpoint checkpoint) {
    HttpServerOptions serverOptions = new HttpServerOptions().setPort(DEFAULT_HTTPS_PORT)
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_JKS.get());
    WebSocketClientOptions clientOptions = new WebSocketClientOptions()
      .setTrustAll(true)
      .setVerifyHost(false);
    client = vertx.createWebSocketClient(clientOptions);
    server = vertx.createHttpServer(serverOptions).requestHandler(request -> {
      if ("/test".equals(request.path())) {
        request
          .toWebSocket()
          .onComplete(TestUtils.onSuccess(ServerWebSocket::close));
      } else {
        request.response().end();
      }
    });
    server.listen().await();
    String url = "wss://" + "localhost" + ":" + DEFAULT_HTTPS_PORT + "/test";
    client.connect(new WebSocketConnectOptions().setAbsoluteURI(url)).onComplete(TestUtils.onSuccess(ws -> {
      ws.closeHandler(v -> {
        checkpoint.succeed();
      });
    }));
  }

  @Test
  public void testCloseStatusCodeFromServer(Checkpoint checkpoint) {
    CountDownLatch latch = checkpoint.asLatch(3);
    testCloseStatusCodeFromServer(latch, ServerWebSocket::close);
  }

  @Test
  public void testCloseStatusCodeFromServerWithHandler(Checkpoint checkpoint) {
    CountDownLatch latch = checkpoint.asLatch(4);
    testCloseStatusCodeFromServer(latch, ws -> ws.close().onComplete(TestUtils.onSuccess(v -> latch.countDown())));
  }

  private void testCloseStatusCodeFromServer(CountDownLatch latch, Consumer<ServerWebSocket> closeOp) {
    client = vertx.createWebSocketClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .webSocketHandler(socket -> {
        socket.closeHandler(a -> {
          latch.countDown();
        });
        vertx.setTimer(100, id -> closeOp.accept(socket));
      });
    server.listen().await();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
        ws.frameHandler(frame -> {
          assertEquals(1000, ((BufferInternal)frame.binaryData()).getByteBuf().getShort(0));
          assertEquals(1000, frame.closeStatusCode());
          assertNull(frame.closeReason());
          latch.countDown();
        });
        ws.closeHandler(sc -> {
          assertEquals((Short)(short)1000, ws.closeStatusCode());
          latch.countDown();
        });
      }));
    });
  }

  @Test
  public void testCloseStatusCodeFromClient() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);
    client = vertx.createWebSocketClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .webSocketHandler(socket -> {
        socket.closeHandler(a -> {
          latch.countDown();
        });
        socket.frameHandler(frame -> {
          assertEquals(1000, ((BufferInternal)frame.binaryData()).getByteBuf().getShort(0));
          assertEquals(1000, frame.closeStatusCode());
          assertNull(frame.closeReason());
          latch.countDown();
        });
      });
    server.listen().await();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(WebSocketBase::close));
    TestUtils.awaitLatch(latch);
  }

  @Test
  public void testCloseFrame(Checkpoint checkpoint1, Checkpoint checkpoint2, Checkpoint checkpoint3) {

    client = vertx.createWebSocketClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .webSocketHandler(socket -> {
        socket.closeHandler(a -> {
          assertEquals((Short)(short)TEST_STATUS_CODE, socket.closeStatusCode());
          assertEquals(TEST_REASON, socket.closeReason());
          checkpoint1.succeed();
        });
        socket.frameHandler(frame -> {
          if (frame.isText()) {
            assertIllegalStateException(frame::closeStatusCode);
            checkpoint2.succeed();
          } else {
            assertEquals(frame.closeReason(), TEST_REASON);
            assertEquals(frame.closeStatusCode(), TEST_STATUS_CODE);
            checkpoint3.succeed();
          }
        });
      });
    server.listen().await();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
      ws.writeTextMessage("Hello");
      ws.close(TEST_STATUS_CODE, TEST_REASON);
    }));
  }

  @Test
  public void testCloseCustomPayloadFromServer(Checkpoint checkpoint) {
    CountDownLatch latch = checkpoint.asLatch(2);
    testCloseCustomPayloadFromServer(latch, ws -> ws.close(TEST_STATUS_CODE, TEST_REASON));
  }

  @Test
  public void testCloseCustomPayloadFromServerWithHandler(Checkpoint checkpoint) {
    CountDownLatch latch = checkpoint.asLatch(3);
    testCloseCustomPayloadFromServer(latch, ws -> ws.close(TEST_STATUS_CODE, TEST_REASON).onComplete(TestUtils.onSuccess(v -> latch.countDown())));
  }

  private void testCloseCustomPayloadFromServer(CountDownLatch latch, Consumer<ServerWebSocket> closeOp) {
    client = vertx.createWebSocketClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .webSocketHandler(socket -> {
        socket.closeHandler(a -> {
          assertEquals((Short)TEST_STATUS_CODE, socket.closeStatusCode());
          assertEquals(TEST_REASON, socket.closeReason());
          latch.countDown();
        });
        vertx.setTimer(100, (ar) -> closeOp.accept(socket));
      });
    server.listen().await();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
        ws.frameHandler(frame -> {
          assertEquals(TEST_REASON, ((BufferInternal)frame.binaryData()).getByteBuf().readerIndex(2).toString(StandardCharsets.UTF_8));
          assertEquals(TEST_STATUS_CODE, ((BufferInternal)frame.binaryData()).getByteBuf().getShort(0));
          assertEquals(TEST_REASON, frame.closeReason());
          assertEquals(TEST_STATUS_CODE, frame.closeStatusCode());
          latch.countDown();
        });
      }));
    });
  }

  @Test
  public void testCloseCustomPayloadFromClient(Checkpoint checkpoint) {
    CountDownLatch latch = checkpoint.asLatch(2);
    testCloseCustomPayloadFromClient(latch, ws -> ws.close(TEST_STATUS_CODE, TEST_REASON));
  }

  @Test
  public void testCloseCustomPayloadFromClientWithHandler(Checkpoint checkpoint) {
    CountDownLatch latch = checkpoint.asLatch(3);
    testCloseCustomPayloadFromClient(latch, ws -> ws.close(TEST_STATUS_CODE, TEST_REASON).onComplete(TestUtils.onSuccess(v -> latch.countDown())));
  }

  private void testCloseCustomPayloadFromClient(CountDownLatch latch, Consumer<WebSocket> closeOp) {
    client = vertx.createWebSocketClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .webSocketHandler(socket -> {
        socket.closeHandler(a -> {
          latch.countDown();
        });
        socket.frameHandler(frame -> {
          assertEquals(TEST_REASON, ((BufferInternal)frame.binaryData()).getByteBuf().readerIndex(2).toString(StandardCharsets.UTF_8));
          assertEquals(TEST_STATUS_CODE, ((BufferInternal)frame.binaryData()).getByteBuf().getShort(0));
          assertEquals(TEST_REASON, frame.closeReason());
          assertEquals(TEST_STATUS_CODE, frame.closeStatusCode());
          latch.countDown();
        });
      });
    server.listen().await();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(closeOp));
  }

  @Test
  public void testServerWebSocketHandshakeWithNonPersistentHTTP1_0Connection(Checkpoint checkpoint) {
    testServerWebSocketHandshakeWithNonPersistentConnection(HttpVersion.HTTP_1_0, checkpoint);
  }

  @Ignore
  @Test
  public void testServerWebSocketHandshakeWithNonPersistentHTTP1_1Connection(Checkpoint checkpoint) {
    // Cannot pass until we merge connection header as it implies a "Connection: upgrade, close" header
    testServerWebSocketHandshakeWithNonPersistentConnection(HttpVersion.HTTP_1_1, checkpoint);
  }

  private void testServerWebSocketHandshakeWithNonPersistentConnection(HttpVersion version, Checkpoint checkpoint) {
    server = vertx.createHttpServer();
    AtomicBoolean webSocketClose = new AtomicBoolean();
    server.webSocketHandler(ws -> {
      ws.frameHandler(frame -> {
        webSocketClose.set(true);
        ws.close();
      });
    });
    HttpClientAgent client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(version).setKeepAlive(false));
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).onComplete(TestUtils.onSuccess(v1 -> {
      handshake(client, req -> {
        req.send().onComplete(TestUtils.onSuccess(resp -> {
          assertEquals(101, resp.statusCode());
          resp.endHandler(v -> {
            Http1ClientConnection conn = (Http1ClientConnection) req.connection();
            NetSocketInternal soi = conn.toNetSocket();
            soi.messageHandler(msg -> {
              if (msg instanceof CloseWebSocketFrame) {
                soi.close();
              }
            });
            ChannelPipeline pipeline = soi.channelHandlerContext().pipeline();
            pipeline.addBefore("handler", "encoder", new WebSocket13FrameEncoder(true));
            pipeline.addBefore("handler", "decoder", new WebSocket13FrameDecoder(false, false, 1000));
            pipeline.remove("codec");
            Future<Void> pingSent = soi.writeMessage(new PingWebSocketFrame());
            soi.closeHandler(v2 -> {
              assertTrue(webSocketClose.get());
              assertTrue(pingSent.succeeded());
              checkpoint.succeed();
            });
          });
        }));
      });
    }));
  }

  @Test
  public void testServerCloseHandshake(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    short status = (short)(4000 + TestUtils.randomPositiveInt() % 100);

    server = vertx.createHttpServer();
    server.webSocketHandler(ws -> {
      ws.closeHandler(sc -> {
        assertEquals((Short)(short)status, ws.closeStatusCode());
        checkpoint1.succeed();
      });
    });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).onComplete(TestUtils.onSuccess(v1 -> {
      HttpClient client = vertx.createHttpClient();
      handshake(client, req -> {
        req.send().onComplete(TestUtils.onSuccess(resp -> {
          assertEquals(101, resp.statusCode());
          Http1ClientConnection conn = (Http1ClientConnection) req.connection();
          NetSocketInternal soi = conn.toNetSocket();
          ChannelPipeline pipeline = soi.channelHandlerContext().pipeline();
          pipeline.addBefore("handler", "encoder", new WebSocket13FrameEncoder(true));
          pipeline.addBefore("handler", "decoder", new WebSocket13FrameDecoder(false, false, 1000));
          pipeline.remove("codec");
          String reason = randomAlphaString(10);
          soi.writeMessage(new CloseWebSocketFrame(status, reason));
          AtomicBoolean closeFrameReceived = new AtomicBoolean();
          soi.messageHandler(msg -> {
            try {
              if (msg instanceof CloseWebSocketFrame) {
                CloseWebSocketFrame frame = (CloseWebSocketFrame) msg;
                assertEquals(status, frame.statusCode());
                assertEquals(reason, frame.reasonText());
                closeFrameReceived.set(true);
              }
            } finally {
              ReferenceCountUtil.release(msg);
            }
          });
          soi.closeHandler(v2 -> {
            assertTrue(closeFrameReceived.get());
            checkpoint2.succeed();
          });
        }));
      });
    }));
  }

  @Test
  public void testClientCloseHandshake(Checkpoint checkpoint1, Checkpoint checkpoint2) {

    server = vertx.createHttpServer();
    server.requestHandler(req -> {
      handshake(req).onComplete(TestUtils.onSuccess(so -> {
        NetSocketInternal soi = (NetSocketInternal) so;
        soi.channelHandlerContext().pipeline().addBefore("handler", "encoder", new WebSocket13FrameEncoder(false));
        soi.channelHandlerContext().pipeline().addBefore("handler", "decoder", new WebSocket13FrameDecoder(true, false, 1000));
        Deque<Object> received = new ArrayDeque<>();
        soi.messageHandler(msg -> {
          received.add(msg);
          if (msg instanceof CloseWebSocketFrame) {
            so.close();
          }
        });
        int status = 4000 + TestUtils.randomPositiveInt() % 100;
        String reason = randomAlphaString(10);
        soi.writeMessage(new CloseWebSocketFrame(status, reason));
        soi.closeHandler(v -> {
          assertEquals(1, received.size());
          Object msg = received.getFirst();
          assertEquals(msg.getClass(), CloseWebSocketFrame.class);
          CloseWebSocketFrame frame = (CloseWebSocketFrame) msg;
          try {
            assertEquals(status, frame.statusCode());
            assertEquals(reason, frame.reasonText());
          } finally {
            ReferenceCountUtil.release(msg);
          }
          checkpoint1.succeed();
        });
      }));
    });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).onComplete(TestUtils.onSuccess(v1 -> {
      client = vertx.createWebSocketClient();
      client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/chat").onComplete(TestUtils.onSuccess(ws -> {
        ws.closeHandler(v -> {
          checkpoint2.succeed();
        });
      }));
    }));
  }

  @Test
  public void testClientConnectionCloseTimeout(Checkpoint checkpoint) {
    testClientConnectionCloseTimeout(1, true, 1000, checkpoint);
  }

  @Test
  public void testClientConnectionCloseImmediately(Checkpoint checkpoint) {
    testClientConnectionCloseTimeout(0, true, 1000, checkpoint);
  }

  @Test
  public void testClientConnectionCloseTimeoutWithoutCloseFrame(Checkpoint checkpoint) {
    testClientConnectionCloseTimeout(1, false, 1006, checkpoint);
  }

  public void testClientConnectionCloseTimeout(int timeout, boolean respondWithCloseFrame, int expectedStatusCode, Checkpoint checkpoint) {
    CountDownLatch latch = checkpoint.asLatch(3);
    List<Object> received = Collections.synchronizedList(new ArrayList<>());
    server = vertx.createHttpServer();
    server.requestHandler(req -> {
      handshake(req).onComplete(TestUtils.onSuccess(so -> {
        NetSocketInternal soi = (NetSocketInternal) so;
        soi.channelHandlerContext().pipeline().addBefore("handler", "encoder", new WebSocket13FrameEncoder(false));
        soi.channelHandlerContext().pipeline().addBefore("handler", "decoder", new WebSocket13FrameDecoder(true, false, 1000));
        soi.messageHandler(msg -> {
          received.add(msg);
          if (msg instanceof CloseWebSocketFrame && respondWithCloseFrame) {
            CloseWebSocketFrame frame = (CloseWebSocketFrame) msg;
            soi.writeMessage(new CloseWebSocketFrame(frame.statusCode(), frame.reasonText()));
          }
        });
        soi.closeHandler(v -> latch.countDown());
      }));
    });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).await();
    client = vertx.createWebSocketClient(new WebSocketClientOptions().setClosingTimeout(timeout));
    WebSocket ws = client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/chat").await();
    ws.endHandler(v -> {
      latch.countDown();
    });
    ws.exceptionHandler(err -> {
      latch.countDown();
    });
    ws.closeHandler(v -> {
      if (timeout > 0L) {
        assertEquals(1, received.size());
        Object msg = received.get(0);
        try {
          assertNotNull(ws.closeStatusCode());
          assertEquals(expectedStatusCode, (short)ws.closeStatusCode());
          assertEquals(msg.getClass(), CloseWebSocketFrame.class);
        } finally {
          ReferenceCountUtil.release(msg);
        }
      }
      latch.countDown();
    });
    // Client sends a close frame but server will not close the TCP connection as expected
    ws.close();
  }

  @Test
  public void testServerCloseTimeout(Checkpoint checkpoint) {
    testServerConnectionClose(1, checkpoint);
  }

  @Test
  public void testServerImmediateClose(Checkpoint checkpoint) {
    testServerConnectionClose(0, checkpoint);
  }

  public void testServerConnectionClose(int timeout, Checkpoint checkpoint) {
    CountDownLatch latch = checkpoint.asLatch(3);
    server = vertx.createHttpServer(new HttpServerOptions().setWebSocketClosingTimeout(timeout))
      .webSocketHandler(ws -> {
        long now = System.currentTimeMillis();
        ws.endHandler(v -> fail());
        ws.exceptionHandler(ignore -> latch.countDown());
        ws.closeHandler(v -> {
          long elapsed = System.currentTimeMillis() - now;
          assertTrue(timeout <= elapsed && elapsed < 5000);
          latch.countDown();
        });
        ws.close();
      });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).await();
    HttpClient client = vertx.createHttpClient();
    handshake(client, req -> {
      req.send().onComplete(TestUtils.onSuccess(resp -> {
        assertEquals(101, resp.statusCode());
        Http1ClientConnection conn = (Http1ClientConnection) req.connection();
        NetSocketInternal soi = conn.toNetSocket();
        soi.channelHandlerContext().pipeline().addBefore("handler", "encoder", new WebSocket13FrameEncoder(true));
        soi.channelHandlerContext().pipeline().addBefore("handler", "decoder", new WebSocket13FrameDecoder(false, false, 1000));
        soi.closeHandler(v -> {
          latch.countDown();
        });
      }));
    });
  }

  @Test
  public void testCloseServer(Checkpoint checkpoint) {
    client = vertx.createWebSocketClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .webSocketHandler(socket -> {
        socket.textMessageHandler(msg -> {
          server.close();
        });
      });
    server.listen().await();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
      ws.writeTextMessage("ping");
      AtomicBoolean closeFrameReceived = new AtomicBoolean();
      ws.frameHandler(frame -> {
        if (frame.isClose()) {
          closeFrameReceived.set(true);
        }
      });
      ws.endHandler(v -> {
        assertTrue(closeFrameReceived.get());
        checkpoint.succeed();
      });
    }));
  }

  @Test
  public void testCloseClient(Checkpoint checkpoint) {
    client = vertx.createWebSocketClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .webSocketHandler(ws -> {
        AtomicBoolean closeFrameReceived = new AtomicBoolean();
        ws.frameHandler(frame -> {
          if (frame.isClose()) {
            closeFrameReceived.set(true);
          }
        });
        ws.endHandler(v -> {
          assertTrue(closeFrameReceived.get());
          checkpoint.succeed();
        });
      });
    server.listen().await();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/").onComplete(TestUtils.onSuccess(ws -> {
      client.close();
    }));
  }

  @Test
  public void testReportProtocolViolationOnClient(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      getUpgradedNetSocket(req, "/some/path", null).onComplete(TestUtils.onSuccess(sock -> {
        // Let's write an invalid frame
        Buffer buff = Buffer.buffer();
        buff.appendByte((byte)(0x8)).appendByte((byte)0); // Violates protocol with V13 (final control frame)
        sock.write(buff);
      }));
    });
    server.listen().await();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI("/some/path")
      .setVersion(WebSocketVersion.V13);
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v1 -> {
      client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ws.closeHandler(v2 -> {
          assertNotNull(failure.get());
          checkpoint.succeed();
        });
        ws.exceptionHandler(failure::set);
      }));
    });
  }

  @Test
  public void testReportProtocolViolationOnServer(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      AtomicReference<Throwable> failure = new AtomicReference<>();
      ws.closeHandler(v -> {
        assertNotNull(failure.get());
        checkpoint.succeed();
      });
      ws.exceptionHandler(failure::set);
    });
    server.listen().await();
    HttpClient client = vertx.createHttpClient();
    handshake(client, req -> {
      req.connect().onComplete(TestUtils.onSuccess(resp -> {
        assertEquals(101, resp.statusCode());
        NetSocket sock = resp.netSocket();
        // Let's write an invalid frame
        Buffer buff = Buffer.buffer();
        buff.appendByte((byte)(0x8)).appendByte((byte)0); // Violates protocol with V13 (final control frame)
        sock.write(buff);
      }));
    });
  }

  @Test
  public void testServerWebSocketShouldBeClosedWhenTheClosedHandlerIsCalled(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), ws);
      sender.send();
      ws.closeHandler(v -> {
        Throwable failure = sender.close();
        if (failure != null) {
          fail(failure.getMessage());
        } else {
          checkpoint.succeed();
        }
      });
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/someuri").onComplete(TestUtils.onSuccess(ws -> {
        vertx.setTimer(1000, id -> {
          ws.close();
        });
      }));
    });
  }

  @Test
  public void testClientWebSocketShouldBeClosedWhenTheClosedHandlerIsCalled(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      vertx.setTimer(1000, id -> {
        ws.close();
      });
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/someuri").onComplete(TestUtils.onSuccess(ws -> {
      CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), ws);
      sender.send();
      ws.closeHandler(v -> {
        Throwable failure = sender.close();
        if (failure != null) {
          fail(failure.getMessage());
        } else {
          checkpoint.succeed();
        }
      });
    }));
  }

  @Test
  public void testDontReceiveMessagerAfterCloseHandlerCalled(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      boolean[] closed = new boolean[1];
      ws.handler(msg -> {
        // We will still receive messages after the close frame is sent
        if (closed[0]) {
          fail("Should not receive a message after close handler callback");
        }
      });
      ws.closeHandler(v -> {
        closed[0] = true;
        // Let some time to let message arrive in the handler
        vertx.setTimer(10, id -> {
          checkpoint.succeed();
        });
      });
      vertx.setTimer(500, id -> {
        // Fill the buffer, so the close frame will be delayed
        while (!ws.writeQueueFull()) {
          ws.write(TestUtils.randomBuffer(1000));
        }
        // Send the close frame, the TCP connection will be closed after that frame is sent
        ws.close();
      });
    });
    server.listen().await();
    // Create a new client that will use the same event-loop than the server
    // so the ws.writeQueueFull() will return true since the client won't be able to read the socket
    // when the server is busy writing the WebSocket
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/someuri").onComplete(TestUtils.onSuccess(ws -> {
      CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), ws);
      ws.closeHandler(v -> sender.close());
      sender.send();
    }));
  }

  @Test
  public void testNoRequestHandler(Checkpoint checkpoint) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    vertx.createHttpServer()
      .webSocketHandler(ws -> fail())
      .listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).onComplete(TestUtils.onSuccess(v -> latch.countDown()));
    TestUtils.awaitLatch(latch);
    HttpClient client = vertx.createHttpClient();
    client.request(new RequestOptions()
      .setHost(DEFAULT_HTTP_HOST)
      .setPort(DEFAULT_HTTP_PORT)).onComplete(TestUtils.onSuccess(req -> {
        req.send().onComplete(TestUtils.onSuccess(resp -> {
          resp.endHandler(v -> {
            assertEquals(400, resp.statusCode());
            checkpoint.succeed();
          });
        }));
    }));
  }

  @Test
  public void testPausedDuringClose(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .webSocketHandler(ws -> {
        AtomicBoolean paused = new AtomicBoolean(true);
        ws.pause();
        ws.closeHandler(v1 -> {
          paused.set(false);
          vertx.runOnContext(v2 -> {
            ws.resume();
          });
        });
        ws.endHandler(v -> {
          assertFalse(paused.get());
          checkpoint.succeed();
        });
      });
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri").onComplete(TestUtils.onSuccess(ws -> {
      ws.close();
    }));
  }

  @Test
  public void testPausedBeforeClosed(Checkpoint checkpoint1, Checkpoint checkpoint2) {

    Buffer expected = TestUtils.randomBuffer(128);
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .webSocketHandler(ws -> {
        AtomicBoolean paused = new AtomicBoolean(true);
        ws.pause();
        ws.closeHandler(v1 -> {
          paused.set(false);
          vertx.runOnContext(v2 -> {
            ws.resume();
          });
        });
        ws.handler(buffer -> {
          assertFalse(paused.get());
          assertEquals(expected, buffer);
          checkpoint1.succeed();
        });
        ws.endHandler(v -> {
          assertFalse(paused.get());
          checkpoint2.succeed();
        });
      });
    server.listen().await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri").onComplete(TestUtils.onSuccess(ws -> {
      ws.write(expected);
      ws.close();
    }));
  }

  @Test
  public void testContext(Checkpoint checkpoint) throws Exception {
    int num = 10;
    CountDownLatch latch = checkpoint.asLatch(num);
    Context serverCtx = vertx.getOrCreateContext();
    server = vertx.createHttpServer()
      .webSocketHandler(ws -> {
        Context current = Vertx.currentContext();
        TestUtils.assertSameEventLoop(serverCtx, current);
        ws.handler(buff -> {
          assertEquals(current, Vertx.currentContext());
        });
        ws.frameHandler(frame -> {
          assertEquals(current, Vertx.currentContext());
        });
        ws.closeHandler(v -> {
          assertEquals(current, Vertx.currentContext());
        });
        ws.endHandler(v -> {
          assertEquals(current, Vertx.currentContext());
          latch.countDown();
        });
      });
    CountDownLatch listenLatch = new CountDownLatch(1);
    serverCtx.runOnContext(v -> {
      server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).onComplete(TestUtils.onSuccess(s -> listenLatch.countDown()));
    });
    TestUtils.awaitLatch(listenLatch);
    client = vertx.createWebSocketClient();
    for (int i = 0;i < num;i++) {
      Context clientCtx = vertx.getOrCreateContext();
      clientCtx.runOnContext(v -> {
        client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri").onComplete(TestUtils.onSuccess(ws -> {
          assertEquals(clientCtx, Vertx.currentContext());
          ws.write(Buffer.buffer("data"));
          ws.pongHandler(pong -> {
            assertEquals(clientCtx, Vertx.currentContext());
            ws.close();
          });
          ws.writePing(Buffer.buffer("ping"));
        }));
      });
    }
  }

  private void fillQueue(WebSocketBase ws, Handler<Void> onFull) {
    if (!ws.writeQueueFull()) {
      ws.writeFrame(io.vertx.core.http.WebSocketFrame.textFrame(randomAlphaString(512), true));
      vertx.runOnContext(v -> {
        fillQueue(ws, onFull);
      });
    } else {
      onFull.handle(null);
    }
  }

  @Test
  public void testDrainServerWebSocket(Checkpoint checkpoint) {
    Promise<Void> resume = Promise.promise();
    server = vertx.createHttpServer()
      .webSocketHandler(ws -> {
        fillQueue(ws, v1 -> {
          resume.complete();
          ws.drainHandler(v2 -> {
            checkpoint.succeed();
          });
        });
      });
    server.listen(DEFAULT_HTTP_PORT).await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri").onComplete(TestUtils.onSuccess(ws -> {
      ws.pause();
      resume.future().onComplete(TestUtils.onSuccess(v2 -> {
        ws.resume();
      }));
    }));
  }

  @Test
  public void testDrainClientWebSocket(Checkpoint checkpoint) {
    testDrainClientWebSocket(vertx.getOrCreateContext(), checkpoint);
  }

  @Test
  public void testDrainClientWorkerWebSocket(Checkpoint checkpoint) {
    testDrainClientWebSocket(((VertxInternal)vertx).createWorkerContext(), checkpoint);
  }

  private void testDrainClientWebSocket(Context ctx, Checkpoint checkpoint) {
    Promise<Void> resume = Promise.promise();
    server = vertx.createHttpServer()
      .webSocketHandler(ws -> {
        ws.pause();
        resume.future().onComplete(TestUtils.onSuccess(v2 -> {
          ws.resume();
        }));
      });
    server.listen(DEFAULT_HTTP_PORT).await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri").onComplete(TestUtils.onSuccess(ws -> {
      while (!ws.writeQueueFull()) {
        ws.writeFrame(WebSocketFrame.textFrame(randomAlphaString(512), true));
      }
      ws.drainHandler(v -> {
        checkpoint.succeed();
      });
      resume.complete();
    }));
  }

  @Test
  public void testWriteHandlerSuccess(Checkpoint checkpoint1, Checkpoint checkpoint2) {

    server = vertx.createHttpServer()
      .webSocketHandler(ws -> {
        ws.handler(buff -> {
          checkpoint1.succeed();
        });
      });
    server.listen(DEFAULT_HTTP_PORT).await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri").onComplete(TestUtils.onSuccess(ws -> {
      ws.write(Buffer.buffer("foo")).onComplete(TestUtils.onSuccess(v -> {
        checkpoint2.succeed();
      }));
    }));
  }

  @Test
  public void testWriteHandlerFailure(Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setWebSocketClosingTimeout(0))
      .webSocketHandler(ServerWebSocket::pause);
    server.listen(DEFAULT_HTTP_PORT).await();
    Buffer buffer = TestUtils.randomBuffer(1024);
    client = vertx.createWebSocketClient();
    vertx.runOnContext(v -> {
      client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri").onComplete(TestUtils.onSuccess(ws -> {
        while (!ws.writeQueueFull()) {
          ws.write(buffer);
        }
        ws.write(buffer).onComplete(TestUtils.onFailure(err -> {
          checkpoint.succeed();
        }));
        ((WebSocketInternal)ws).channelHandlerContext().close();
      }));
    });
  }

  @Test
  public void testCloseClientImmediately(Checkpoint checkpoint) {
    WebSocketClient client = vertx.createWebSocketClient();
    server = vertx.createHttpServer()
      .requestHandler(req -> {
        // Don't perform the handshake
      });
    server.listen(DEFAULT_HTTP_PORT).await();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri").onComplete(ar -> {
      if (ar.succeeded()) {
        fail();
      } else {
        checkpoint.succeed();
      }
    });
    client.close();
  }

  @Test
  public void testHAProxy(Checkpoint checkpoint) throws Exception {
    CountDownLatch latch = checkpoint.asLatch(2);

    SocketAddress remote = SocketAddress.inetSocketAddress(56324, "192.168.0.1");
    SocketAddress local = SocketAddress.inetSocketAddress(443, "192.168.0.11");
    Buffer header = HAProxy.createVersion1TCP4ProtocolHeader(remote, local);
    HAProxy proxy = new HAProxy(DEFAULT_HTTP_HOST, DEFAULT_HTTP_PORT, header);
    proxy.start(vertx);

    server = vertx.createHttpServer(new HttpServerOptions().setUseProxyProtocol(true))
      .webSocketHandler(ws -> {
        assertEquals(remote,ws.remoteAddress());
        assertEquals(local, ws.localAddress());
        ws.handler(buff -> {
          latch.countDown();
        });
      });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).await();
    client = vertx.createWebSocketClient();
    client.connect(proxy.getPort(), proxy.getHost(), "/someuri").onComplete(TestUtils.onSuccess(ws -> {
      ws.write(Buffer.buffer("foo")).onComplete(TestUtils.onSuccess(v -> {
        latch.countDown();
      }));
    }));
    try {
      checkpoint.awaitSuccess();
    } finally {
      proxy.stop();
    }
  }

  @Test
  public void testWebSocketDisablesALPN() {
/*
    client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setUseAlpn(true)
      .setSsl(true)
      .setTrustAll(true));
    server = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setUseAlpn(true)
      .setSni(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get()))
      .requestHandler(req -> req.response().end())
      .webSocketHandler(ws -> {
        ws.handler(msg -> {
          assertEquals("hello", msg.toString());
          ws.close();
        });
      });
    server.listen(DEFAULT_HTTPS_PORT, DEFAULT_HTTP_HOST).await();
    client.request(HttpMethod.GET, DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, DEFAULT_TEST_URI).onComplete(TestUtils.onSuccess(req -> {
      req.send().onComplete(TestUtils.onSuccess(resp -> {
        assertEquals(HttpVersion.HTTP_2, resp.version());
        client.webSocket(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST, "/").onComplete(
          TestUtils.onSuccess(ws -> {
            assertTrue(ws.isSsl());
            ws.write(Buffer.buffer("hello"));
            ws.closeHandler(v -> {
              checkpoint.succeed();
            });
          }));
      }));
    }));
*/
  }

  @Test
  public void testSetOriginHeaderV13(Checkpoint checkpoint) {
    testOriginHeader(WebSocketVersion.V13, true, "http://www.example.com", HttpHeaders.ORIGIN, "http://www.example.com", checkpoint);
  }

  @Test
  public void testEnableOriginHeaderV13(Checkpoint checkpoint) {
    testOriginHeader(WebSocketVersion.V13, true, null, HttpHeaders.ORIGIN, "http://" + DEFAULT_HTTP_HOST_AND_PORT, checkpoint);
  }

  @Test
  public void testDisableOriginHeaderV13(Checkpoint checkpoint) {
    testOriginHeader(WebSocketVersion.V13, false, null, HttpHeaders.ORIGIN, null, checkpoint);
  }

  @Test
  public void testSetOriginHeaderV08(Checkpoint checkpoint) {
    testOriginHeader(WebSocketVersion.V08, true, "http://www.example.com", HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, "http://www.example.com", checkpoint);
  }

  @Test
  public void testEnableOriginHeaderV08(Checkpoint checkpoint) {
    testOriginHeader(WebSocketVersion.V08, true, null, HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, "http://" + DEFAULT_HTTP_HOST_AND_PORT, checkpoint);
  }

  @Test
  public void testDisableOriginHeaderV08(Checkpoint checkpoint) {
    testOriginHeader(WebSocketVersion.V08, false, null, HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, null, checkpoint);
  }

  @Test
  public void testSetOriginHeaderV07(Checkpoint checkpoint) {
    testOriginHeader(WebSocketVersion.V07, true, "http://www.example.com", HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, "http://www.example.com", checkpoint);
  }

  @Test
  public void testEnableOriginHeaderV07(Checkpoint checkpoint) {
    testOriginHeader(WebSocketVersion.V07, true, null, HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, "http://" + DEFAULT_HTTP_HOST_AND_PORT, checkpoint);
  }

  @Test
  public void testDisableOriginHeaderV07(Checkpoint checkpoint) {
    testOriginHeader(WebSocketVersion.V07, false, null, HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, null, checkpoint);
  }

  private void testOriginHeader(WebSocketVersion version, boolean allow, String origin, CharSequence header, String expected, Checkpoint checkpoint) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST));
    server.webSocketHandler(ws -> {
      if (expected != null) {
        assertEquals(expected, ws.headers().get(header));
      } else {
        assertNull(ws.headers().get(header));
      }
    });
    server.listen().await();
    client = vertx.createWebSocketClient();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setVersion(version)
      .setAllowOriginHeader(allow)
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI("/");
    if (origin != null) {
      options.addHeader(header, origin);
    }
    client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
      checkpoint.succeed();
    }));
  }

  @Test
  public void testWriteHandlerIdNullByDefault(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    String path = "/some/path";
    Buffer hello = Buffer.buffer("hello");
    Buffer bye = Buffer.buffer("bye");

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).webSocketHandler(ws -> {
      assertNull(ws.textHandlerID());
      assertNull(ws.binaryHandlerID());
      ws.binaryMessageHandler(data -> {
        assertEquals(hello, data);
        ws.writeBinaryMessage(bye).eventually(ws::close);
      });
    });



    server.listen().await();

    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST)
      .setURI(path);
    client = vertx.createWebSocketClient();
    client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
      assertNull(ws.textHandlerID());
      assertNull(ws.binaryHandlerID());
      ws
        .closeHandler(v -> checkpoint1.succeed())
        .binaryMessageHandler(data -> {
          assertEquals(bye, data);
          checkpoint2.succeed();
        })
        .write(hello);
    }));

  }

  @Test
  public void testFanoutWithBinary(Checkpoint checkpoint) {
    testFanout(Buffer.buffer("hello"), Buffer.buffer("bye"), WebSocketBase::binaryHandlerID, WebSocketBase::binaryMessageHandler, WebSocket::writeBinaryMessage, checkpoint);
  }

  @Test
  public void testFanoutWithText(Checkpoint checkpoint) {
    testFanout("hello", "bye", WebSocketBase::textHandlerID, WebSocketBase::textMessageHandler, WebSocket::writeTextMessage, checkpoint);
  }

  private <T> void testFanout(T hello, T bye, Function<WebSocketBase, String> handlerIDGetter, BiConsumer<WebSocketBase, Handler<T>> messageHandlerSetter, BiFunction<WebSocket, T, Future<Void>> messageWriter, Checkpoint checkpoint) {
    String path = "/some/path";
    int numConnections = 10;

    Set<String> connections = ConcurrentHashMap.newKeySet();
    HttpServerOptions httpServerOptions = new HttpServerOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setRegisterWebSocketWriteHandlers(true);
    server = vertx.createHttpServer(httpServerOptions).webSocketHandler(ws -> {
      String handlerID = handlerIDGetter.apply(ws);
      assertNotNull(handlerID);
      messageHandlerSetter.accept(ws, data -> {
        assertEquals(hello, data);
        connections.add(handlerID);
        if (connections.size() == numConnections) {
          for (String actorID : connections) {
            vertx.eventBus().send(actorID, bye);
          }
        }
      });
    });

    CountDownLatch latch = checkpoint.asLatch(numConnections);

    server.listen().await();

    client = vertx.createWebSocketClient();
    for (int i = 0; i < numConnections; i++) {
      WebSocketConnectOptions options = new WebSocketConnectOptions()
        .setPort(DEFAULT_HTTP_PORT)
        .setHost(DEFAULT_HTTP_HOST)
        .setURI(path);
      client.connect(options).onComplete(TestUtils.onSuccess(ws -> {
        messageHandlerSetter.accept(ws, data -> {
          assertEquals(bye, data);
          latch.countDown();
        });
        messageWriter.apply(ws, hello);
      }));
    }

  }

  @Test
  public void testConnect(Checkpoint checkpoint1, Checkpoint checkpoint2) {
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(HttpTestBase.DEFAULT_HTTP_HOST))
      .webSocketHandler(ws -> {
        ws.write(Buffer.buffer("Ping"));
        ws.handler(buff -> {
          ws.close();
        });
      });
    server.listen().await();
    client = vertx.createWebSocketClient();
    ClientWebSocket ws = client.webSocket();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(DEFAULT_HTTP_PORT)
      .setHost(DEFAULT_HTTP_HOST);
    ws.handler(buff -> {
        ws.write(buff);
        ws.connect(options)
          .onComplete(TestUtils.onFailure(err -> {
          checkpoint1.succeed();
        }));
      })
      .closeHandler(v -> checkpoint2.succeed()).connect(options
      ).onComplete(TestUtils.onSuccess(v -> {

      }));
  }

  @Test
  public void testServerWebSocketExceptionHandlerIsCalled(Checkpoint checkpoint1, Checkpoint checkpoint2) {

    AtomicBoolean failed = new AtomicBoolean();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)
                                                           .setHost(DEFAULT_HTTP_HOST))
                  .exceptionHandler(t -> fail())
                  .connectionHandler(connection -> connection.exceptionHandler(t -> fail()))
                  .webSocketHandler(ws -> {
                    ws.endHandler(v -> fail());
                    ws.closeHandler(v -> checkpoint1.succeed());
                    ws.exceptionHandler(t -> {
                      if (failed.compareAndSet(false, true)) {
                        checkpoint2.succeed();
                      }
                    });
                  });
    server.listen().await();
    vertx.createWebSocketClient()
         .connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
         .onSuccess(ws -> ws.close(INVALID_STATUS_CODE));
  }

  @Test
  public void testClientShutdownClose() throws Exception {
    int num = 4;
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    server = vertx
      .createHttpServer()
      .webSocketHandler(ws -> {
        ws.handler(buff -> {
          latch2.countDown();
          try {
            // Prevents event-loop sending back a close frame to the client
            latch1.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            fail(e.getMessage());
          }
        });
      });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).await();
    client = vertx.createWebSocketClient(new WebSocketClientOptions().setMaxConnections(1));
    CountDownLatch failures = new CountDownLatch(num - 1);
    CountDownLatch closure = new CountDownLatch(1);
    CountDownLatch shutdown = new CountDownLatch(1);
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        // Need to use a verticle to ensure we don't use the same the server loop since we rely on blocking it
        for (int i = 0;i < num;i++) {
          int val = i;
          client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
            .onComplete(ar -> {
              if (val == 0) {
                assertTrue(ar.succeeded());
                WebSocket ws = ar.result();
                ws.write(Buffer.buffer("ping"));
                ws.closeHandler(v -> closure.countDown());
                ws.shutdownHandler(v -> shutdown.countDown());
              } else {
                failures.countDown();
              }
            });
        }
      }
    });
    TestUtils.awaitLatch(latch2);
    Future<Void> fut = client.shutdown(2, TimeUnit.SECONDS);
    TestUtils.awaitLatch(failures);
    latch1.countDown();
    TestUtils.awaitLatch(closure);
    TestUtils.awaitLatch(shutdown);
    fut.await();
  }

  @Test
  public void testServerShutdownClose() throws Exception {
    long now = System.currentTimeMillis();
    AtomicInteger shutdown = new AtomicInteger();
    AtomicInteger closure = new AtomicInteger();
    CountDownLatch latch1 = new CountDownLatch(1);
    server = vertx
      .createHttpServer()
      .webSocketHandler(ws -> {
        WebSocketInternal impl = (WebSocketInternal) ws;
        ChannelHandlerContext chctx = impl.channelHandlerContext();
        chctx.pipeline().addBefore("handler", "test",  new ChannelOutboundHandlerAdapter() {
          @Override
          public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof CloseWebSocketFrame) {
              latch1.countDown();
            }
            super.write(ctx, msg, promise);
          }
        });
        ws.handler(buff -> {
          ws.write(Buffer.buffer("pong"));
          ws.shutdownHandler(v -> {
            assertTrue(System.currentTimeMillis() - now < 1000);
            shutdown.incrementAndGet();
          });
          ws.closeHandler(v -> {
            assertTrue(System.currentTimeMillis() - now > 2000);
            closure.incrementAndGet();
          });
        });
      });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).await();
    client = vertx.createWebSocketClient();
    CountDownLatch latch2 = new CountDownLatch(1);
    AtomicReference<WebSocket> wsRef = new AtomicReference<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        // Need to use a verticle to ensure we don't use the same the server loop since we rely on blocking it
        client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
          .onComplete(TestUtils.onSuccess(ws -> {
            wsRef.set(ws);
            ws.handler(buff -> {
              latch2.countDown();
              try {
                // Prevents event-loop sending back a close frame to the server
                latch1.await(10, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                fail(e.getMessage());
              }
            });
            ws.write(Buffer.buffer("ping"));
          }));
      }
    });
    TestUtils.awaitLatch(latch2);
    Future<Void> fut = server.shutdown(2, TimeUnit.SECONDS);
    fut.await();
    long elapsed = System.currentTimeMillis() - now;
    assertTrue(elapsed >= 2000);
    assertTrue(elapsed < 4000);
    assertWaitUntil(() -> shutdown.get() == 1);
    assertWaitUntil(() -> closure.get() == 1);
  }

  @Test
  public void testServerShutdownOverride(Checkpoint checkpoint1, Checkpoint checkpoint2) {

    long now = System.currentTimeMillis();
    server = vertx
      .createHttpServer()
      .webSocketHandler(ws -> {
        ws.shutdownHandler(v -> {
          vertx.setTimer(200, id -> {
            ws.close();
          });
        });
        ws.closeHandler(v -> {
          long d = System.currentTimeMillis() - now;
          assertTrue(d >= 200);
          assertTrue(d <= 2000);
          checkpoint1.succeed();
        });
        ws.handler(buff -> {
          ws.shutdown(10, TimeUnit.SECONDS);
        });
      });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).await();
    client = vertx.createWebSocketClient();
    AtomicReference<WebSocket> wsRef = new AtomicReference<>();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .onComplete(TestUtils.onSuccess(ws -> {
        ws.write(Buffer.buffer("ping"));
        ws.closeHandler(v -> {
          checkpoint2.succeed();
        });
      }));
  }

  @Test
  public void testServerShutdown(Checkpoint checkpoint1, Checkpoint checkpoint2) {

    long now = System.currentTimeMillis();
    server = vertx
      .createHttpServer()
      .webSocketHandler(ws -> {
        ws.closeHandler(v -> {
          long d = System.currentTimeMillis() - now;
          assertTrue(d <= 500);
          checkpoint1.succeed();
        });
        ws.handler(buff -> {
          ws.shutdown(10, TimeUnit.SECONDS);
        });
      });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/")
      .onComplete(TestUtils.onSuccess(ws -> {
        ws.write(Buffer.buffer("ping"));
        ws.closeHandler(v -> {
          checkpoint2.succeed();
        });
      }));
  }

  @Test
  public void testCustomResponseHeadersBeforeUpgrade(Checkpoint checkpoint) {
    String path = "/some/path";
    String message = "here is some text data";
    String headerKey = "custom";
    String headerValue = "value";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      req.response().headers().set(headerKey, headerValue);
      req.toWebSocket()
        .onComplete(TestUtils.onSuccess(ws -> {
          ws.writeFinalTextFrame(message);
        }));
    });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).await();
    client = vertx.createWebSocketClient();
    client.connect(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path)
      .onComplete(TestUtils.onSuccess(ws -> {
        assertTrue(ws.headers().contains(headerKey));
        assertEquals(headerValue, ws.headers().get(headerKey));
        ws.handler(buff -> {
          assertEquals(message, buff.toString("UTF-8"));
          checkpoint.succeed();
        });
      }));
  }

  @Test
  public void testPoolShouldNotStarveOnConnectError() throws Exception {

    server = vertx.createHttpServer();

    CountDownLatch shutdownLatch = new CountDownLatch(1);
    AtomicInteger accepted = new AtomicInteger();
    server.webSocketHandler(ws -> {
      ws.shutdownHandler(v -> shutdownLatch.countDown());
      assertTrue(accepted.getAndIncrement() == 0);
    });

    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).toCompletionStage().toCompletableFuture().get();

    int maxConnections = 5;

    client = vertx.createWebSocketClient(new WebSocketClientOptions()
      .setMaxConnections(maxConnections)
      .setConnectTimeout(4000));

    Future<WebSocket> wsFut = client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/").andThen(TestUtils.onSuccess(v -> {
    }));

    // Finish handshake
    wsFut.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

    // This test requires a server socket to respond for the first connection
    // Subsequent connections need to fail (connect error)
    server.shutdown(30, TimeUnit.SECONDS);
    TestUtils.awaitLatch(shutdownLatch);

    int num = maxConnections + 10;
    CountDownLatch latch = new CountDownLatch(num);
    for (int i = 0;i < num;i++) {
      client.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/").onComplete(ar -> {
        latch.countDown();
      });
    }

    TestUtils.awaitLatch(latch, 10, TimeUnit.SECONDS);
  }
}
