/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;


import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.FrameType;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.impl.NetSocketInternal;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.core.streams.ReadStream;
import io.vertx.test.core.CheckingSender;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.Test;

import javax.security.cert.X509Certificate;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import java.util.function.Consumer;
import java.util.function.Function;

import static io.vertx.core.http.HttpTestBase.DEFAULT_HTTP_HOST;
import static io.vertx.core.http.HttpTestBase.DEFAULT_HTTP_PORT;
import static io.vertx.core.http.HttpTestBase.DEFAULT_TEST_URI;
import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocketTest extends VertxTestBase {

  private HttpClient client;
  private HttpServer server;
  private NetServer netServer;

  public void setUp() throws Exception {
    super.setUp();
    client = vertx.createHttpClient(new HttpClientOptions());
  }

  protected void tearDown() throws Exception {
    client.close();
    if (server != null) {
      CountDownLatch latch = new CountDownLatch(1);
      server.close(ar -> {
        assertTrue(ar.succeeded());
        latch.countDown();
      });
      awaitLatch(latch);
    }
    if (netServer != null) {
      CountDownLatch latch = new CountDownLatch(1);
      netServer.close(ar -> {
        assertTrue(ar.succeeded());
        latch.countDown();
      });
      awaitLatch(latch);
    }
    super.tearDown();
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
        "127.0.0.1 localhost\n" +
        "127.0.0.1 host2.com"));
    return options;
  }

  @Test
  public void testRejectHybi00() throws Exception {
    testReject(WebsocketVersion.V00, null, 502);
  }

  @Test
  public void testRejectHybi08() throws Exception {
    testReject(WebsocketVersion.V08, null, 502);
  }

  @Test
  public void testRejectWithStatusCode() throws Exception {
    testReject(WebsocketVersion.V08, 404, 404);
  }

  @Test
  public void testWSBinaryHybi00() throws Exception {
    testWSFrames(true, WebsocketVersion.V00);
  }

  @Test
  public void testWSStringHybi00() throws Exception {
    testWSFrames(false, WebsocketVersion.V00);
  }

  @Test
  public void testWSBinaryHybi08() throws Exception {
    testWSFrames(true, WebsocketVersion.V08);
  }

  @Test
  public void testWSStringHybi08() throws Exception {
    testWSFrames(false, WebsocketVersion.V08);
  }

  @Test
  public void testWSBinaryHybi17() throws Exception {
    testWSFrames(true, WebsocketVersion.V13);
  }

  @Test
  public void testWSStringHybi17() throws Exception {
    testWSFrames(false, WebsocketVersion.V13);
  }

  @Test
  public void testWSStreamsHybi00() throws Exception {
    testWSWriteStream(WebsocketVersion.V00);
  }

  @Test
  public void testWSStreamsHybi08() throws Exception {
    testWSWriteStream(WebsocketVersion.V08);
  }

  @Test
  public void testWSStreamsHybi17() throws Exception {
    testWSWriteStream(WebsocketVersion.V13);
  }

  @Test
  public void testWriteFromConnectHybi00() throws Exception {
    testWriteFromConnectHandler(WebsocketVersion.V00);
  }

  @Test
  public void testWriteFromConnectHybi08() throws Exception {
    testWriteFromConnectHandler(WebsocketVersion.V08);
  }

  @Test
  public void testWriteFromConnectHybi17() throws Exception {
    testWriteFromConnectHandler(WebsocketVersion.V13);
  }

  @Test
  public void testContinuationWriteFromConnectHybi08() throws Exception {
    testContinuationWriteFromConnectHandler(WebsocketVersion.V08);
  }

  @Test
  public void testContinuationWriteFromConnectHybi17() throws Exception {
    testContinuationWriteFromConnectHandler(WebsocketVersion.V13);
  }

  @Test
  public void testValidSubProtocolHybi00() throws Exception {
    testValidSubProtocol(WebsocketVersion.V00);
  }

  @Test
  public void testValidSubProtocolHybi08() throws Exception {
    testValidSubProtocol(WebsocketVersion.V08);
  }

  @Test
  public void testValidSubProtocolHybi17() throws Exception {
    testValidSubProtocol(WebsocketVersion.V13);
  }

  @Test
  public void testInvalidSubProtocolHybi00() throws Exception {
    testInvalidSubProtocol(WebsocketVersion.V00);
  }

  @Test
  public void testInvalidSubProtocolHybi08() throws Exception {
    testInvalidSubProtocol(WebsocketVersion.V08);
  }

  @Test
  public void testInvalidSubProtocolHybi17() throws Exception {
    testInvalidSubProtocol(WebsocketVersion.V13);
  }

  // TODO close and exception tests
  // TODO pause/resume/drain tests

  @Test
  // Client trusts all server certs
  public void testTLSClientTrustAll() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertWithSNI() throws Exception {
    testTLS(Cert.NONE, Trust.SNI_JKS_HOST2, Cert.SNI_JKS, Trust.NONE, false, false, false, false, true, true, true, true, new String[0],
        client -> client.websocketStream(4043, "host2.com", "/"));
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertPKCS12() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_PKCS12, Trust.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertPEM() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_PEM, Trust.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertPEM_CA() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA, Cert.SERVER_PEM_ROOT_CA, Trust.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustPKCS12ServerCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PKCS12, Cert.SERVER_JKS, Trust.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustPEMServerCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM, Cert.SERVER_JKS, Trust.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, false, false, false);
  }

  @Test
  //Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, false, false, false, false, true);
  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertRequired() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false, true);
  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertRequiredPKCS12() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PKCS12, true, false, false, false, true);
  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertRequiredPEM() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PEM, true, false, false, false, true);
  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertPKCS12Required() throws Exception {
    testTLS(Cert.CLIENT_PKCS12, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false, true);
  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertPEMRequired() throws Exception {
    testTLS(Cert.CLIENT_PEM, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false, true);
  }

  @Test
  //Client specifies cert signed by CA and it is required
  public void testTLSClientCertPEM_CARequired() throws Exception {
    testTLS(Cert.CLIENT_PEM_ROOT_CA, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PEM_ROOT_CA, true, false, false, false, true);
  }

  @Test
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_JKS, true, false, false, false, false);
  }

  @Test
  //Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted() throws Exception {
    testTLS(Cert.CLIENT_JKS, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.NONE, true, false, false, false, false);
  }

  @Test
  // Server specifies cert that the client does not trust via a revoked certificate of the CA
  public void testTLSClientRevokedServerCert() throws Exception {
    testTLS(Cert.NONE, Trust.SERVER_PEM_ROOT_CA, Cert.SERVER_PEM_ROOT_CA, Trust.NONE, false, false, false, true, false);
  }

  @Test
  //Client specifies cert that the server does not trust via a revoked certificate of the CA
  public void testTLSRevokedClientCertServer() throws Exception {
    testTLS(Cert.CLIENT_PEM_ROOT_CA, Trust.SERVER_JKS, Cert.SERVER_JKS, Trust.CLIENT_PEM_ROOT_CA, true, true, false, false, false);
  }

  @Test
  // Test with cipher suites
  public void testTLSCipherSuites() throws Exception {
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true, ENABLED_CIPHER_SUITES);
  }

  // RequestOptions tests

  @Test
  // Client trusts all server certs
  public void testClearClientRequestOptionsSetSSL() throws Exception {
    RequestOptions options = new RequestOptions().setHost(HttpTestBase.DEFAULT_HTTP_HOST).setURI("/").setPort(4043).setSsl(true);
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true, false, true, false, new String[0], client -> client.websocketStream(options));
  }

  @Test
  // Client trusts all server certs
  public void testSSLClientRequestOptionsSetSSL() throws Exception {
    RequestOptions options = new RequestOptions().setHost(HttpTestBase.DEFAULT_HTTP_HOST).setURI("/").setPort(4043).setSsl(true);
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true, true, true, false, new String[0], client -> client.websocketStream(options));
  }

  @Test
  // Client trusts all server certs
  public void testClearClientRequestOptionsSetClear() throws Exception {
    RequestOptions options = new RequestOptions().setHost(HttpTestBase.DEFAULT_HTTP_HOST).setURI("/").setPort(4043).setSsl(false);
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true, false, false, false, new String[0], client -> client.websocketStream(options));
  }

  @Test
  // Client trusts all server certs
  public void testSSLClientRequestOptionsSetClear() throws Exception {
    RequestOptions options = new RequestOptions().setHost(HttpTestBase.DEFAULT_HTTP_HOST).setURI("/").setPort(4043).setSsl(false);
    testTLS(Cert.NONE, Trust.NONE, Cert.SERVER_JKS, Trust.NONE, false, false, true, false, true, true, false, false, new String[0], client -> client.websocketStream(options));
  }

  private void testTLS(Cert<?> clientCert, Trust<?> clientTrust,
                       Cert<?> serverCert, Trust<?> serverTrust,
                       boolean requireClientAuth, boolean serverUsesCrl, boolean clientTrustAll,
                       boolean clientUsesCrl, boolean shouldPass,
                       String... enabledCipherSuites) throws Exception {
    testTLS(clientCert, clientTrust,
        serverCert, serverTrust,
        requireClientAuth, serverUsesCrl, clientTrustAll, clientUsesCrl, shouldPass, true, true, false,
        enabledCipherSuites, client -> client.websocketStream(4043, HttpTestBase.DEFAULT_HTTP_HOST, "/"));
  }

  private void testTLS(Cert<?> clientCert, Trust<?> clientTrust,
                       Cert<?> serverCert, Trust<?> serverTrust,
                       boolean requireClientAuth, boolean serverUsesCrl, boolean clientTrustAll,
                       boolean clientUsesCrl, boolean shouldPass,
                       boolean clientSsl,
                       boolean serverSsl,
                       boolean sni,
                       String[] enabledCipherSuites,
                       Function<HttpClient, ReadStream<WebSocket>> wsProvider) throws Exception {
    HttpClientOptions options = new HttpClientOptions();
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
    client = vertx.createHttpClient(options);
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
    server = vertx.createHttpServer(serverOptions.setPort(4043));
    server.websocketHandler(ws -> {
      ws.handler(ws::write);
    });
    try {
      server.listen(ar -> {
        assertTrue(ar.succeeded());
        Handler<Throwable> errHandler = t -> {
          if (shouldPass) {
            t.printStackTrace();
            fail("Should not throw exception");
          } else {
            testComplete();
          }
        };
        Handler<WebSocket> wsHandler = ws -> {
          if (clientSsl && sni) {
            try {
              X509Certificate clientPeerCert = ws.peerCertificateChain()[0];
              assertEquals("host2.com", cnOf(clientPeerCert));
            } catch (Exception err) {
              fail(err);
            }
          }
          int size = 100;
          Buffer received = Buffer.buffer();
          ws.handler(data -> {
            received.appendBuffer(data);
            if (received.length() == size) {
              ws.close();
              testComplete();
            }
          });
          Buffer buff = Buffer.buffer(TestUtils.randomByteArray(size));
          ws.writeFrame(WebSocketFrame.binaryFrame(buff, true));
        };
        wsProvider.apply(client).
            exceptionHandler(errHandler).
            handler(wsHandler);
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
    await();
  }

  @Test
  // Let's manually handle the websocket handshake and write a frame to the client
  public void testHandleWSManually() throws Exception {
    String path = "/some/path";
    String message = "here is some text data";

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      NetSocket sock = getUpgradedNetSocket(req, path);
      // Let's write a Text frame raw
      Buffer buff = Buffer.buffer();
      buff.appendByte((byte)129); // Text frame
      buff.appendByte((byte)message.length());
      buff.appendString(message);
      sock.write(buff);
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocketStream(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path).
          handler(ws -> {
            ws.handler(buff -> {
              assertEquals(message, buff.toString("UTF-8"));
              testComplete();
            });
          });
    });
    await();
  }

  @Test
  public void testSharedServersRoundRobin() throws Exception {

    int numServers = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE / 2- 1;
    int numConnections = numServers * 100;

    List<HttpServer> servers = new ArrayList<>();
    Set<HttpServer> connectedServers = new ConcurrentHashSet<>();
    Map<HttpServer, Integer> connectCount = new ConcurrentHashMap<>();

    CountDownLatch latchListen = new CountDownLatch(numServers);
    CountDownLatch latchConns = new CountDownLatch(numConnections);
    for (int i = 0; i < numServers; i++) {
      HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
      servers.add(theServer);
      theServer.websocketHandler(ws -> {
        connectedServers.add(theServer);
        Integer cnt = connectCount.get(theServer);
        int icnt = cnt == null ? 0 : cnt;
        icnt++;
        connectCount.put(theServer, icnt);
        latchConns.countDown();
      }).listen(ar -> {
        if (ar.succeeded()) {
          latchListen.countDown();
        } else {
          fail("Failed to bind server");
        }
      });
    }
    assertTrue(latchListen.await(10, TimeUnit.SECONDS));

    // Create a bunch of connections
    CountDownLatch latchClient = new CountDownLatch(numConnections);
    for (int i = 0; i < numConnections; i++) {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/someuri", ws -> {
        ws.closeHandler(v -> latchClient.countDown());
        ws.close();
      });
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

    CountDownLatch closeLatch = new CountDownLatch(numServers);

    for (HttpServer server: servers) {
      server.close(ar -> {
        assertTrue(ar.succeeded());
        closeLatch.countDown();
      });
    }

    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));

    testComplete();
  }

  @Test
  public void testSharedServersRoundRobinWithOtherServerRunningOnDifferentPort() throws Exception {
    // Have a server running on a different port to make sure it doesn't interact
    CountDownLatch latch = new CountDownLatch(1);
    HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(4321));
    theServer.websocketHandler(ws -> {
      fail("Should not connect");
    }).listen(ar -> {
      if (ar.succeeded()) {
        latch.countDown();
      } else {
        fail("Failed to bind server");
      }
    });
    awaitLatch(latch);
    testSharedServersRoundRobin();
  }

  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    // Start and stop a server on the same port/host before hand to make sure it doesn't interact
    CountDownLatch latch = new CountDownLatch(1);
    HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(4321));
    theServer.websocketHandler(ws -> {
      fail("Should not connect");
    }).listen(ar -> {
      if (ar.succeeded()) {
        latch.countDown();
      } else {
        fail("Failed to bind server");
      }
    });
    awaitLatch(latch);
    CountDownLatch closeLatch = new CountDownLatch(1);
    theServer.close(ar -> {
      assertTrue(ar.succeeded());
      closeLatch.countDown();
    });
    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
    testSharedServersRoundRobin();
  }

  @Test
  public void testWebsocketFrameFactoryArguments() throws Exception {
    assertNullPointerException(() -> WebSocketFrame.binaryFrame(null, true));
    assertNullPointerException(() -> WebSocketFrame.textFrame(null, true));
    assertNullPointerException(() -> WebSocketFrame.continuationFrame(null, true));
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


  private NetSocket getUpgradedNetSocket(HttpServerRequest req, String path) {
    assertEquals(path, req.path());
    assertEquals("upgrade", req.headers().get("Connection"));
    NetSocket sock = req.netSocket();
    String secHeader = req.headers().get("Sec-WebSocket-Key");
    String tmp = secHeader + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    String encoded = sha1(tmp);
    sock.write("HTTP/1.1 101 Web Socket Protocol Handshake\r\n" +
        "Upgrade: WebSocket\r\n" +
        "Connection: upgrade\r\n" +
        "Sec-WebSocket-Accept: " + encoded + "\r\n" +
        "\r\n");
    return sock;
  }

  private void testWSWriteStream(WebsocketVersion version) throws Exception {

    String path = "/some/path";
    String query = "handshake=bar&wibble=eek";
    String uri = path + "?" + query;

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      assertEquals(uri, ws.uri());
      assertEquals(path, ws.path());
      assertEquals(query, ws.query());
      assertEquals("upgrade", ws.headers().get("Connection"));
      ws.handler(data -> {
        ws.write(data);
      });
    });

    server.listen(ar -> {
      assertTrue(ar.succeeded());
      int bsize = 100;
      int sends = 10;

      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path + "?" + query, null, version, ws -> {
        final Buffer received = Buffer.buffer();
        ws.handler(data -> {
          received.appendBuffer(data);
          if (received.length() == bsize * sends) {
            ws.close();
            testComplete();
          }
        });
        final Buffer sent = Buffer.buffer();
        for (int i = 0; i < sends; i++) {
          Buffer buff = Buffer.buffer(TestUtils.randomByteArray(bsize));
          ws.write(buff);
          sent.appendBuffer(buff);
        }
      });
    });
    await();
  }

  private void testWSFrames(boolean binary, WebsocketVersion version) throws Exception {

    String path = "/some/path";
    String query = "handshake=bar&wibble=eek";
    String uri = path + "?" + query;

    // version 0 doesn't support continuations so we just send 1 frame per message
    int frames = version == WebsocketVersion.V00 ? 1: 10;

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      assertEquals(uri, ws.uri());
      assertEquals(path, ws.path());
      assertEquals(query, ws.query());
      assertEquals("upgrade", ws.headers().get("Connection"));
      AtomicInteger count = new AtomicInteger();
      ws.frameHandler(frame -> {
        if (frame.isClose()) {
          testComplete();
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

    server.listen(ar -> {
      assertTrue(ar.succeeded());
      int bsize = 100;

      int msgs = 10;

      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path + "?" + query, null,
        version, ws -> {
          final List<Buffer> sent = new ArrayList<>();
          final List<Buffer> received = new ArrayList<>();

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
              WebSocketFrame frame;
              if (binary) {
                buff = Buffer.buffer(TestUtils.randomByteArray(bsize));
                if (j == 0) {
                  frame = WebSocketFrame.binaryFrame(buff, false);
                } else {
                  frame = WebSocketFrame.continuationFrame(buff, j == frames - 1);
                }
              } else {
                String str = TestUtils.randomAlphaString(bsize);
                buff = Buffer.buffer(str);
                if (j == 0) {
                  frame = WebSocketFrame.textFrame(str, false);
                } else {
                  frame = WebSocketFrame.continuationFrame(buff, j == frames - 1);
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
        });
    });
    await();
  }

  @Test
  public void testWriteFinalTextFrame() throws Exception {
    testWriteFinalFrame(false);
  }

  @Test
  public void testWriteFinalBinaryFrame() throws Exception {
    testWriteFinalFrame(true);
  }

  private void testWriteFinalFrame(boolean binary) throws Exception {

    waitFor(2);
    String text = TestUtils.randomUnicodeString(100);
    Buffer data = TestUtils.randomBuffer(100);

    Consumer<WebSocketFrame> frameConsumer = frame -> {
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

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws ->
      ws.frameHandler(frame -> {
        if (frame.isClose()) {
          complete();
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

    server.listen(onSuccess(s ->
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        ws.frameHandler(frame -> {
          if (frame.isClose()) {
            complete();
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
    ));

    await();
  }

  private void testContinuationWriteFromConnectHandler(WebsocketVersion version) throws Exception {
    String path = "/some/path";
    String firstFrame = "AAA";
    String continuationFrame = "BBB";

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      NetSocket sock = getUpgradedNetSocket(req, path);

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
    });

    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version, ws -> {
        AtomicBoolean receivedFirstFrame = new AtomicBoolean();
        ws.frameHandler(received -> {
          Buffer receivedBuffer = Buffer.buffer(received.textData());
          if (!received.isFinal()) {
            assertEquals(firstFrame, receivedBuffer.toString());
            receivedFirstFrame.set(true);
          } else if (receivedFirstFrame.get() && received.isFinal()) {
            assertEquals(continuationFrame, receivedBuffer.toString());
            ws.close();
            testComplete();
          }
        });
      });
    });
    await();
  }

  private void testWriteFromConnectHandler(WebsocketVersion version) throws Exception {

    String path = "/some/path";
    Buffer buff = Buffer.buffer("AAA");

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      assertEquals(path, ws.path());
      ws.writeFrame(WebSocketFrame.binaryFrame(buff, true));
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version, ws -> {
        Buffer received = Buffer.buffer();
        ws.handler(data -> {
          received.appendBuffer(data);
          if (received.length() == buff.length()) {
            assertEquals(buff, received);
            ws.close();
            testComplete();
          }
        });
      });
    });
    await();
  }

  @Test
  // Test normal negotiation of websocket compression
  public void testNormalWSDeflateFrameCompressionNegotiation() throws Exception {
    String path = "/some/path";
    Buffer buff = Buffer.buffer("AAA");

    // Server should have basic compression enabled by default,
    // client needs to ask for it
    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT))
        .websocketHandler(ws -> {
          assertEquals("upgrade", ws.headers().get("Connection"));
          assertEquals("deflate-frame", ws.headers().get("sec-websocket-extensions"));
          ws.writeFrame(WebSocketFrame.binaryFrame(buff, true));
        });

    server.listen(ar -> {
      assertTrue(ar.succeeded());

      HttpClientOptions options = new HttpClientOptions();
      options.setTryUsePerFrameWebsocketCompression(true);
      client = vertx.createHttpClient(options);
      client.websocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, path, ws -> {
        final Buffer received = Buffer.buffer();
        ws.handler(data -> {
          received.appendBuffer(data);
          if (received.length() == buff.length()) {
            assertEquals(buff, received);
            ws.close();
            testComplete();
          }
        });
      });
    });
    await();
  }

  @Test
  // Test normal negotiation of websocket compression
  public void testNormalWSPermessageDeflateCompressionNegotiation() throws Exception {
	  String path = "/some/path";
	  Buffer buff = Buffer.buffer("AAA");

	  // Server should have basic compression enabled by default,
	  // client needs to ask for it
	  server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
		  assertEquals("upgrade", ws.headers().get("Connection"));
		  assertEquals("permessage-deflate;client_max_window_bits", ws.headers().get("sec-websocket-extensions"));
		  ws.writeFrame(WebSocketFrame.binaryFrame(buff,  true));
	  });

	  server.listen(ar -> {
		  assertTrue(ar.succeeded());

		  HttpClientOptions options = new HttpClientOptions();
	      options.setTryUsePerMessageWebsocketCompression(true);
	      client = vertx.createHttpClient(options);
		  client.websocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, path, ws -> {
			  final Buffer received = Buffer.buffer();
			  ws.handler(data -> {
				  received.appendBuffer(data);
		          if (received.length() == buff.length()) {
		            assertEquals(buff, received);
		            ws.close();
		            testComplete();
		          }
			  });
		  });
	  });
	  await();
  }

  @Test
  // Test server accepting no compression
  public void testConnectWithWebsocketComressionDisabled() throws Exception {
	  String path = "/some/path";
	  Buffer buff = Buffer.buffer("AAA");

	  // Server should have basic compression enabled by default,
	  // client needs to ask for it
	  server = vertx.createHttpServer(new HttpServerOptions()
			  .setPort(DEFAULT_HTTP_PORT)
			  .setPerFrameWebsocketCompressionSupported(false)
			  .setPerMessageWebsocketCompressionSupported(false)
			  ).websocketHandler(ws -> {

		  assertEquals("upgrade", ws.headers().get("Connection"));
		  assertNull(ws.headers().get("sec-websocket-extensions"));

		  ws.writeFrame(WebSocketFrame.binaryFrame(buff,  true));
	  });


	  server.listen(ar -> {
		  assertTrue(ar.succeeded());

		  HttpClientOptions options = new HttpClientOptions();

	      client = vertx.createHttpClient(options);

		  client.websocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, path, ws -> {

			  final Buffer received = Buffer.buffer();
			  ws.handler(data -> {
				  received.appendBuffer(data);
		          if (received.length() == buff.length()) {
		            assertEquals(buff, received);
		            ws.close();
		            testComplete();
		          }
			  });
		  });
	  });
	  await();
  }

  private void testValidSubProtocol(WebsocketVersion version) throws Exception {
    String path = "/some/path";
    String clientSubProtocols = "clientproto,commonproto";
    String serverSubProtocols = "serverproto,commonproto";
    Buffer buff = Buffer.buffer("AAA");
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setWebsocketSubProtocols(serverSubProtocols)).websocketHandler(ws -> {
      assertEquals(path, ws.path());
      assertNull(ws.subProtocol());
      ws.accept();
      assertEquals("commonproto", ws.subProtocol());
      ws.writeFrame(WebSocketFrame.binaryFrame(buff, true));
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version, clientSubProtocols, ws -> {
        assertEquals("commonproto", ws.subProtocol());
        final Buffer received = Buffer.buffer();
        ws.handler(data -> {
          received.appendBuffer(data);
          if (received.length() == buff.length()) {
            assertEquals(buff, received);
            ws.close();
            testComplete();
          }
        });
      });
    });
    await();
  }

  private void testInvalidSubProtocol(WebsocketVersion version) throws Exception {
    String path = "/some/path";
    String subProtocol = "myprotocol";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setWebsocketSubProtocols("invalid")).websocketHandler(ws -> {
    });
    server.listen(onSuccess(ar -> {
      client.websocketStream(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version, subProtocol).
          exceptionHandler(t -> {
            // Should fail
            testComplete();
          }).
          handler(ws -> {
          });
    }));
    await();
  }

  Function<Handler<AsyncResult<HttpClientResponse>>, HttpClientRequest> INVALID_MISSING_CONNECTION_HEADER = handler -> client.get(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTPS_HOST, "/some/path", handler)
    .putHeader("Upgrade", "Websocket");

  @Test
  public void testInvalidMissingConnectionHeader() {
    testInvalidHandshake(INVALID_MISSING_CONNECTION_HEADER, false, false,400);
    await();
  }

  @Test
  public void testInvalidMissingConnectionHeaderRequestUpgrade() {
    testInvalidHandshake(INVALID_MISSING_CONNECTION_HEADER, false, true,400);
    await();
  }

  Function<Handler<AsyncResult<HttpClientResponse>>, HttpClientRequest> INVALID_HTTP_METHOD = handler -> client.head(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTPS_HOST, "/some/path", handler)
    .putHeader("Upgrade", "Websocket")
    .putHeader("Connection", "Upgrade");

  @Test
  public void testInvalidMethod() {
    testInvalidHandshake(INVALID_HTTP_METHOD, false, false,405);
    await();
  }

  @Test
  public void testInvalidMethodRequestUpgrade() {
    testInvalidHandshake(INVALID_HTTP_METHOD, false, true,405);
    await();
  }

  Function<Handler<AsyncResult<HttpClientResponse>>, HttpClientRequest> INVALID_URI = handler -> client.get(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTPS_HOST, ":", handler)
    .putHeader("Upgrade", "Websocket")
    .putHeader("Connection", "Upgrade");

  @Test
  public void testInvalidUri() {
    testInvalidHandshake(INVALID_URI, false, false,400);
    await();
  }

  @Test
  public void testInvalidUriRequestUpgrade() {
    testInvalidHandshake(INVALID_URI, false, true,400);
    await();
  }

  Function<Handler<AsyncResult<HttpClientResponse>>, HttpClientRequest> INVALID_WEBSOCKET_VERSION = handler -> client.get(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTPS_HOST, "/some/path", handler)
    .putHeader("Upgrade", "Websocket")
    .putHeader("Sec-Websocket-Version", "15")
    .putHeader("Connection", "Upgrade");

  @Test
  public void testInvalidWebSocketVersion() {
    testInvalidHandshake(INVALID_WEBSOCKET_VERSION, false, false,426);
    await();
  }

  @Test
  public void testInvalidWebSocketVersionRequestUpgrade() {
    testInvalidHandshake(INVALID_WEBSOCKET_VERSION, false, true,426);
    await();
  }

  Function<Handler<AsyncResult<HttpClientResponse>>, HttpClientRequest> HANDSHAKE_EXCEPTION = handler -> client.get(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTPS_HOST, "/some/path", handler)
    .putHeader("Upgrade", "Websocket")
    .putHeader("Sec-Websocket-Version", "13")
    .putHeader("Connection", "Upgrade");

  @Test
  public void testHandshakeException() {
    testInvalidHandshake(HANDSHAKE_EXCEPTION, true, false,400);
    await();
  }

  @Test
  public void testHandshakeExceptionRequestUpgrade() {
    testInvalidHandshake(HANDSHAKE_EXCEPTION, true, true,400);
    await();
  }

  // Check client response with the ws handler
  private void testInvalidHandshake(Function<Handler<AsyncResult<HttpClientResponse>>, HttpClientRequest> requestProvider,
                                    boolean expectEvent,
                                    boolean upgradeRequest,
                                    int expectedStatus) {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setMaxPoolSize(1));
    if (upgradeRequest) {
      server = vertx.createHttpServer()
        .websocketHandler(ws -> {
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
            req.upgrade();
          } catch (Exception e) {
            // Expected
          }
        } else {
          req.response().end();
        }
      });
    }
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(ar -> {
      HttpClientRequest req = requestProvider.apply(onSuccess(resp -> {
        assertEquals(expectedStatus, resp.statusCode());
        resp.endHandler(v1 -> {
          // Make another request to check the connection remains usable
          client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, DEFAULT_TEST_URI, onSuccess(resp2 -> {
            resp2.endHandler(v2 -> {
              testComplete();
            });
          }));
        });
      }));
      req.end();
    }));
  }

  private void testReject(WebsocketVersion version, Integer rejectionStatus, int expectedRejectionStatus) throws Exception {

    String path = "/some/path";

    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      assertEquals(path, ws.path());
      if (rejectionStatus != null) {
        ws.reject(rejectionStatus);
      } else {
        ws.reject();
      }
    });

    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocketStream(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version).
          exceptionHandler(t -> {
            assertTrue(t instanceof WebsocketRejectedException);
            assertEquals(expectedRejectionStatus, ((WebsocketRejectedException)t).getStatus());
            testComplete();
          }).
          handler(ws -> fail("Should not be called"));
    });
    await();
  }

  @Test
  public void testAsyncAccept() {
    AtomicBoolean resolved = new AtomicBoolean();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      Future<Integer> fut = Future.future();
      ws.setHandshake(fut);
      try {
        ws.accept();
        fail();
      } catch (IllegalStateException ignore) {
        // Expected
      }
      try {
        ws.writeTextMessage("hello");
        fail();
      } catch (IllegalStateException ignore) {
        // Expected
      }
      vertx.setTimer(500, id -> {
        resolved.set(true);
        fut.complete(101);
      });
    });
    server.listen(onSuccess(s -> {
      client.websocketStream(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/some/path", null).
        handler(ws -> {
          assertTrue(resolved.get());
          testComplete();
        });
    }));
    await();
  }

  @Test
  public void testCloseAsyncPending() {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      Future<Integer> fut = Future.future();
      ws.setHandshake(fut);
      ws.close();
      assertTrue(fut.isComplete());
      assertEquals(101, (int)fut.result());
    });
    server.listen(onSuccess(s -> {
      client.websocketStream(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/some/path", null).
        handler(ws -> {
          ws.closeHandler(v -> {
            testComplete();
          });
        });
    }));
    await();
  }

  @Test
  public void testClose() throws Exception {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(WebSocketBase::close);
    server.listen(onSuccess(s -> {
      client.websocketStream(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/some/path", null).
        handler(ws -> {
          ws.closeHandler(v -> {
            testComplete();
          });
        });
    }));
    await();
  }

  @Test
  public void testRequestEntityTooLarge() {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> fail());
    server.listen(onSuccess(ar -> {
      client.get(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTPS_HOST, path, onSuccess(resp -> {
        assertEquals(413, resp.statusCode());
        resp.request().connection().closeHandler(v -> {
          testComplete();
        });
      })).putHeader("Upgrade", "Websocket")
        .putHeader("Connection", "Upgrade")
        .end(TestUtils.randomBuffer(8192 + 1));
    }));
    await();
  }

  @Test
  public void testWriteMessageHybi00() {
    testWriteMessage(256, WebsocketVersion.V00);
  }

  @Test
  public void testWriteFragmentedMessage1Hybi00() {
    testWriteMessage(65536 + 256, WebsocketVersion.V00);
  }

  @Test
  public void testWriteFragmentedMessage2Hybi00() {
    testWriteMessage(65536 + 65536 + 256, WebsocketVersion.V00);
  }

  @Test
  public void testWriteMessageHybi08() {
    testWriteMessage(256, WebsocketVersion.V08);
  }

  @Test
  public void testWriteFragmentedMessage1Hybi08() {
    testWriteMessage(65536 + 256, WebsocketVersion.V08);
  }

  @Test
  public void testWriteFragmentedMessage2Hybi08() {
    testWriteMessage(65536 + 65536 + 256, WebsocketVersion.V08);
  }

  @Test
  public void testWriteMessageHybi17() {
    testWriteMessage(256, WebsocketVersion.V13);
  }

  @Test
  public void testWriteFragmentedMessage1Hybi17() {
    testWriteMessage(65536 + 256, WebsocketVersion.V13);
  }

  @Test
  public void testWriteFragmentedMessage2Hybi17() {
    testWriteMessage(65536 + 65536 + 256, WebsocketVersion.V13);
  }

  private void testWriteMessage(int size, WebsocketVersion version) {
    String path = "/some/path";
    byte[] expected = TestUtils.randomByteArray(size);
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      ws.writeBinaryMessage(Buffer.buffer(expected));
      ws.close();
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version, ws -> {
        Buffer actual = Buffer.buffer();
        ws.handler(actual::appendBuffer);
        ws.closeHandler(v -> {
          assertArrayEquals(expected, actual.getBytes(0, actual.length()));
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testNonFragmentedTextMessage2Hybi00() {
      String messageToSend = TestUtils.randomAlphaString(256);
      testWriteSingleTextMessage(messageToSend, WebsocketVersion.V00);
  }

  @Test
  public void testFragmentedTextMessage2Hybi07() {
    String messageToSend = TestUtils.randomAlphaString(65536 + 65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebsocketVersion.V07);
  }

  @Test
  public void testFragmentedTextMessage2Hybi08() {
    String messageToSend = TestUtils.randomAlphaString(65536 + 65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebsocketVersion.V08);
  }

  @Test
  public void testFragmentedTextMessage2Hybi13() {
    String messageToSend = TestUtils.randomAlphaString(65536 + 65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebsocketVersion.V13);
  }

  @Test
  public void testMaxLengthFragmentedTextMessage() {
    String messageToSend = TestUtils.randomAlphaString(HttpServerOptions.DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE);
    testWriteSingleTextMessage(messageToSend, WebsocketVersion.V13);
  }

  @Test
  public void testFragmentedUnicodeTextMessage2Hybi07() {
    String messageToSend = TestUtils.randomUnicodeString(65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebsocketVersion.V07);
  }

  @Test
  public void testFragmentedUnicodeTextMessage2Hybi08() {
    String messageToSend = TestUtils.randomUnicodeString(65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebsocketVersion.V08);
  }

  @Test
  public void testFragmentedUnicodeTextMessage2Hybi13() {
    String messageToSend = TestUtils.randomUnicodeString(65536 + 256);
    testWriteSingleTextMessage(messageToSend, WebsocketVersion.V13);
  }

  @Test
  public void testTooLargeMessage() {
    String messageToSend = TestUtils.randomAlphaString(HttpClientOptions.DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE + 1);
    SocketMessages socketMessages = testWriteTextMessages(Collections.singletonList(messageToSend), WebsocketVersion.V13);
    List<String> receivedMessages = socketMessages.getReceivedMessages();
    List<String> expectedMessages = Collections.emptyList();
    assertEquals("Should not have received any messages", expectedMessages, receivedMessages);
    List<Throwable> receivedExceptions = socketMessages.getReceivedExceptions();
    assertEquals("Should have received a single exception", 1, receivedExceptions.size());
    assertTrue("Should have received IllegalStateException",
            receivedExceptions.get(0) instanceof IllegalStateException);
  }

  @Test
  public void testContinueAfterTooLargeMessage() {
    int shortMessageLength = HttpClientOptions.DEFAULT_MAX_WEBSOCKET_FRAME_SIZE;
    String shortFirstMessage = TestUtils.randomAlphaString(shortMessageLength);
    String tooLongMiddleMessage = TestUtils.randomAlphaString(HttpClientOptions.DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE * 2);
    String shortLastMessage = TestUtils.randomAlphaString(shortMessageLength);
    List<String> messagesToSend = Arrays.asList(shortFirstMessage, tooLongMiddleMessage, shortLastMessage);

    SocketMessages socketMessages = testWriteTextMessages(messagesToSend, WebsocketVersion.V13);
    List<String> receivedMessages = socketMessages.getReceivedMessages();
    List<String> expectedMessages = Arrays.asList(shortFirstMessage, shortLastMessage);
    assertEquals("Incorrect received messages", expectedMessages, receivedMessages);
  }

  private void testWriteSingleTextMessage(String messageToSend, WebsocketVersion version) {
    List<String> messagesToSend = Collections.singletonList(messageToSend);
    SocketMessages socketMessages = testWriteTextMessages(messagesToSend, version);
    assertEquals("Did not receive all messages", messagesToSend, socketMessages.getReceivedMessages());
    List<Throwable> expectedExceptions = Collections.emptyList();
    assertEquals("Should not have received any exceptions", expectedExceptions, socketMessages.getReceivedExceptions());
  }

  private SocketMessages testWriteTextMessages(List<String> messagesToSend, WebsocketVersion version) {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      for (String messageToSend : messagesToSend) {
        ws.writeTextMessage(messageToSend);
      }
      ws.close();
    });

    List<String> receivedMessages = new ArrayList<>();
    List<Throwable> receivedExceptions = new ArrayList<>();
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version, ws -> {
        ws.textMessageHandler(receivedMessages::add);
        ws.exceptionHandler(receivedExceptions::add);
        ws.closeHandler(v -> testComplete());
      });
    });
    await();
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
  public void testWebsocketPauseAndResume() {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setConnectTimeout(1000));
    this.server = vertx.createHttpServer(new HttpServerOptions().setAcceptBacklog(1).setPort(DEFAULT_HTTP_PORT));
    AtomicBoolean paused = new AtomicBoolean();
    ReadStream<ServerWebSocket> stream = server.websocketStream();
    stream.handler(ws -> {
      assertFalse(paused.get());
      ws.writeBinaryMessage(Buffer.buffer("whatever"));
      ws.close();
    });
    server.listen(listenAR -> {
      assertTrue(listenAR.succeeded());
      stream.pause();
      paused.set(true);
      connectUntilWebsocketHandshakeException(client, 0, res -> {
        if (!res.succeeded()) {
          fail(new AssertionError("Was expecting error to be WebSocketHandshakeException", res.cause()));
        }
        assertTrue(paused.get());
        paused.set(false);
        stream.resume();
        client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/some/path", ws -> {
          ws.handler(buffer -> {
            assertEquals("whatever", buffer.toString("UTF-8"));
            ws.closeHandler(v2 -> {
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  private void connectUntilWebsocketHandshakeException(HttpClient client, int count, Handler<AsyncResult<Void>> doneHandler) {
    vertx.runOnContext(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/some/path", ws -> {
        if (count < 100) {
          connectUntilWebsocketHandshakeException(client, count + 1, doneHandler);
        } else {
          doneHandler.handle(Future.failedFuture(new AssertionError()));
        }
      }, err -> {
        if (err instanceof WebSocketHandshakeException || err instanceof IOException) {
          doneHandler.handle(Future.succeededFuture());
        } else if (count < 100) {
          connectUntilWebsocketHandshakeException(client, count + 1, doneHandler);
        } else {
          doneHandler.handle(Future.failedFuture(err));
        }
      });
    });
  }

  @Test
  public void testClosingServerClosesWebSocketStreamEndHandler() {
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    ReadStream<ServerWebSocket> stream = server.websocketStream();
    AtomicBoolean closed = new AtomicBoolean();
    stream.endHandler(v -> closed.set(true));
    stream.handler(ws -> {
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      assertFalse(closed.get());
      server.close(v -> {
        assertTrue(ar.succeeded());
        assertTrue(closed.get());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testWebsocketStreamCallbackAsynchronously() {
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    AtomicInteger done = new AtomicInteger();
    ReadStream<ServerWebSocket> stream = server.websocketStream();
    stream.handler(req -> { });
    ThreadLocal<Object> stack = new ThreadLocal<>();
    stack.set(true);
    stream.endHandler(v -> {
      assertTrue(Vertx.currentContext().isEventLoopContext());
      assertNull(stack.get());
      if (done.incrementAndGet() == 2) {
        testComplete();
      }
    });
    server.listen(ar -> {
      assertTrue(Vertx.currentContext().isEventLoopContext());
      assertNull(stack.get());
      ThreadLocal<Object> stack2 = new ThreadLocal<>();
      stack2.set(true);
      server.close(v -> {
        assertTrue(Vertx.currentContext().isEventLoopContext());
        assertNull(stack2.get());
        if (done.incrementAndGet() == 2) {
          testComplete();
        }
      });
      stack2.set(null);
    });
    await();
  }

  @Test
  public void testMultipleServerClose() {
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    AtomicInteger times = new AtomicInteger();
    // We assume the endHandler and the close completion handler are invoked in the same context task
    ThreadLocal stack = new ThreadLocal();
    stack.set(true);
    server.websocketStream().endHandler(v -> {
      assertNull(stack.get());
      assertTrue(Vertx.currentContext().isEventLoopContext());
      times.incrementAndGet();
    });
    server.close(ar1 -> {
      assertNull(stack.get());
      assertTrue(Vertx.currentContext().isEventLoopContext());
      server.close(ar2 -> {
        server.close(ar3 -> {
          assertEquals(1, times.get());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testRemoteCloseCallHandlers() {
    testCloseCallHandlers(false);
  }

  @Test
  public void testLocalCloseCallHandlers() {
    testCloseCallHandlers(true);
  }

  private void testCloseCallHandlers(boolean local) {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      if (!local) {
        ws.close();
      }
    });
    AtomicInteger doneCount = new AtomicInteger();
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocketStream(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null).
        endHandler(done -> doneCount.incrementAndGet()).
        handler(ws -> {
          assertEquals(0, doneCount.get());
          boolean[] closed = new boolean[1];
          ws.closeHandler(v -> {
            closed[0] = true;
            assertEquals(1, doneCount.get());
            testComplete();
          });
          if (local) {
            vertx.runOnContext(v -> {
              ws.close();
            });
          }
        });
    });
    await();
  }

  @Test
  public void testClearClientHandlersOnEnd() {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(WebSocketBase::close);
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocketStream(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null).
          handler(ws -> {
            ws.endHandler(v -> {
              try {
                ws.endHandler(null);
                ws.exceptionHandler(null);
                ws.handler(null);
              } catch (Exception e) {
                fail("Was expecting to set to null the handlers when the socket is closed");
                return;
              }
              testComplete();
            });
          });
    });
    await();
  }

  @Test
  public void testUpgrade() {
    testUpgrade(false);
  }

  @Test
  public void testUpgradeDelayed() {
    testUpgrade(true);
  }

  private void testUpgrade(boolean delayed) {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    server.requestHandler(request -> {
      Runnable runner = () -> {
        ServerWebSocket ws = request.upgrade();
        ws.handler(buff -> {
          ws.write(Buffer.buffer("helloworld"));
          ws.close();
        });
      };
      if (delayed) {
        // This tests the case where the last http content comes of the request (its not full) comes in
        // before the upgrade has happened and before HttpServerImpl.expectWebsockets is true
        vertx.runOnContext(v -> {
          runner.run();
        });
      } else {
        runner.run();
      }
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocketStream(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null).
        handler(ws -> {
          Buffer buff = Buffer.buffer();
          ws.handler(b -> {
            buff.appendBuffer(b);
          });
          ws.endHandler(v -> {
            // Last two bytes are status code payload
            assertEquals("helloworld", buff.toString("UTF-8"));
            testComplete();
          });
          ws.write(Buffer.buffer("foo"));
        });
    });
    await();
  }

  @Test
  public void testUnmaskedFrameRequest(){

    client = vertx.createHttpClient(new HttpClientOptions().setSendUnmaskedFrames(true));
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setAcceptUnmaskedFrames(true));
    server.requestHandler(req -> {
      req.response().setChunked(true).write("connect");
    });
    server.websocketHandler(ws -> {

      ws.handler(new Handler<Buffer>() {
        public void handle(Buffer data) {
          assertEquals(data.toString(), "first unmasked frame");
          testComplete();
        }
      });

    });
    server.listen(onSuccess(server -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        ws.writeFinalTextFrame("first unmasked frame");
      });
    }));
    await();
  }


  @Test
  public void testInvalidUnmaskedFrameRequest(){

    client = vertx.createHttpClient(new HttpClientOptions().setSendUnmaskedFrames(true));
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    server.requestHandler(req -> {
      req.response().setChunked(true).write("connect");
    });
    server.websocketHandler(ws -> {

      ws.exceptionHandler(exception -> {
        testComplete();
      });

      ws.handler(result -> {
        fail("Cannot decode unmasked message because I require masked frame as configured");
      });
    });

    server.listen(onSuccess(server -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        ws.writeFinalTextFrame("first unmasked frame");
      });
    }));

    await();
  }

  @Test
  public void testUpgradeInvalidRequest() {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    server.requestHandler(request -> {
      try {
        request.upgrade();
        fail("Should throw exception");
      } catch (IllegalStateException e) {
        // OK
      }
      testComplete();
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.request(HttpMethod.GET, DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", resp -> {
      }).end();
    });
    await();
  }

  @Test
  public void testRaceConditionWithWebsocketClientEventLoop() {
    testRaceConditionWithWebsocketClient(vertx.getOrCreateContext());
  }

  @Test
  public void testRaceConditionWithWebsocketClientWorker() throws Exception {
    CompletableFuture<Context> fut = new CompletableFuture<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        fut.complete(context);
      }
    }, new DeploymentOptions().setWorker(true), ar -> {
      if (ar.failed()) {
        fut.completeExceptionally(ar.cause());
      }
    });
    testRaceConditionWithWebsocketClient(fut.get());
  }

  private NetSocket handshake(HttpServerRequest req) {
    NetSocket so = req.netSocket();
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
      return so;
    } catch (NoSuchAlgorithmException e) {
      req.response().setStatusCode(500).end();
      fail(e.getMessage());
      return null;
    }
  }

  private void handshake(Handler<NetSocket> handler) {
    HttpClientRequest request = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/",
      onSuccess(resp -> {
        assertEquals(101, resp.statusCode());
        handler.handle(resp.netSocket());
      })
    );
    request
      .putHeader("Upgrade", "websocket")
      .putHeader("Connection", "Upgrade")
      .putHeader("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
      .putHeader("Sec-WebSocket-Protocol", "chat")
      .putHeader("Sec-WebSocket-Version", "13")
      .putHeader("Origin", "http://example.com");
    request.end();
  }

  private void testRaceConditionWithWebsocketClient(Context context) {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    // Handcrafted websocket handshake for sending a frame immediatly after the handshake
    server.requestHandler(req -> {
      NetSocket so = handshake(req);
      if (so != null) {
        so.write(Buffer.buffer(new byte[]{
          (byte) 0x82,
          0x05,
          0x68,
          0x65,
          0x6c,
          0x6c,
          0x6f,
        }));
      }
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      context.runOnContext(v -> {
        client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
          ws.handler(buf -> {
            assertEquals("hello", buf.toString());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testRaceConditionWithWebsocketClientWorker2() throws Exception {
    int size = getOptions().getWorkerPoolSize() - 4;
    List<Context> workers = createWorkers(size + 1);
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    server.websocketHandler(ws -> {
      ws.write(Buffer.buffer("hello"));
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      workers.get(0).runOnContext(v -> {
        ReadStream<WebSocket> webSocketStream = client.websocketStream(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/");
        webSocketStream.handler(ws -> {
          ws.handler(buf -> {
            assertEquals("hello", buf.toString());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void httpClientWebsocketConnectionFailureHandlerShouldBeCalled() throws Exception {
    int port = 7867;
    HttpClient client = vertx.createHttpClient();
    client.websocket(port, "localhost", "", websocket -> {
      websocket.handler(data -> {
        fail("connection should not succeed");
      });
    }, throwable -> testComplete());
    await();
  }

  @Test
  public void testClientWebsocketWithHttp2Client() throws Exception {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setHttp2ClearTextUpgrade(false).setProtocolVersion(HttpVersion.HTTP_2));
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT));
    server.requestHandler(req -> {
      req.response().setChunked(true).write("connect");
    });
    server.websocketHandler(ws -> {
      ws.writeFinalTextFrame("ok");
    });
    server.listen(onSuccess(server -> {
      client.getNow(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", resp -> {
        client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
          ws.handler(buff -> {
            assertEquals("ok", buff.toString());
            testComplete();
          });
        });
      });
    }));
    await();
  }

  @Test
  public void testClientWebsocketConnectionCloseOnBadResponseWithKeepalive() throws Throwable {
    // issue #1757
    doTestClientWebsocketConnectionCloseOnBadResponse(true);
  }

  @Test
  public void testClientWebsocketConnectionCloseOnBadResponseWithoutKeepalive() throws Throwable {
    doTestClientWebsocketConnectionCloseOnBadResponse(false);
  }

  final BlockingQueue<Throwable> resultQueue = new ArrayBlockingQueue<Throwable>(10);

  void addResult(Throwable result) {
    try {
      resultQueue.put(result);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void doTestClientWebsocketConnectionCloseOnBadResponse(boolean keepAliveInOptions) throws Throwable {
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
    }).listen(ar -> {
      if (ar.failed()) {
        addResult(ar.cause());
        return;
      }
      NetServer server = ar.result();
      int port = server.actualPort();

      HttpClientOptions opts = new HttpClientOptions().setKeepAlive(keepAliveInOptions);
      client.close();
      client = vertx.createHttpClient(opts).websocket(port, "localhost", "/", ws -> {
        addResult(new AssertionError("Websocket unexpectedly connected"));
        ws.close();
      }, t -> {
        addResult(t);
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
      } else if (result instanceof WebsocketRejectedException
              && ((WebsocketRejectedException)result).getStatus() == 200) {
        clientGotCorrectException = true;
      } else {
        throw result;
      }
    }
  }

  @Test
  public void testClearClientSslOptions() {
    SelfSignedCertificate certificate = SelfSignedCertificate.create();
    HttpServerOptions serverOptions = new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTPS_PORT)
      .setSsl(true)
      .setKeyCertOptions(certificate.keyCertOptions());
    HttpClientOptions clientOptions = new HttpClientOptions()
      .setTrustAll(true)
      .setVerifyHost(false);
    client = vertx.createHttpClient(clientOptions);
    server = vertx.createHttpServer(serverOptions).websocketHandler(WebSocketBase::close).listen(onSuccess(server -> {
      RequestOptions requestOptions = new RequestOptions().setPort(HttpTestBase.DEFAULT_HTTPS_PORT).setSsl(true);
      client.websocket(requestOptions, ws -> {
        ws.closeHandler(v -> {
          testComplete();
        });
      });
    }));
    await();
  }

  @Test
  public void testServerWebsocketPingPong() {
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST));
    server.websocketHandler(ws -> {
      ws.pongHandler(buff -> {
        assertEquals("ping", buff.toString());
        testComplete();
      });
      ws.writePing(Buffer.buffer("ping"));
    }).listen(onSuccess(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {});
    }));
    await();
  }

  @Test
  public void testServerWebsocketPingExceeds125Bytes() {
    testServerWebsocketPingPongCheck(255, ws -> {
      try {
        ws.writePing(Buffer.buffer(randomAlphaString(126)));
      } catch(Throwable expected) {
        assertEquals("Ping cannot exceed maxWebSocketFrameSize or 125 bytes", expected.getMessage());
        ws.close();
        testComplete();
      }
    });
  }

  @Test
  public void testServerWebsocketPongExceeds125Bytes() {
    testServerWebsocketPingPongCheck(255, ws -> {
      try {
        ws.writePong(Buffer.buffer(randomAlphaString(126)));
      } catch(Throwable expected) {
        assertEquals("Pong cannot exceed maxWebSocketFrameSize or 125 bytes", expected.getMessage());
        ws.close();
        testComplete();
      }
    });
  }

  @Test
  public void testServerWebsocketPingExceedsMaxFrameSize() {
    testServerWebsocketPingPongCheck(100, ws -> {
      try {
        ws.writePing(Buffer.buffer(randomAlphaString(101)));
      } catch(Throwable expected) {
        assertEquals("Ping cannot exceed maxWebSocketFrameSize or 125 bytes", expected.getMessage());
        ws.close();
        testComplete();
      }
    });
  }

  @Test
  public void testServerWebsocketPongExceedsMaxFrameSize() {
    testServerWebsocketPingPongCheck(100, ws -> {
      try {
        ws.writePong(Buffer.buffer(randomAlphaString(101)));
      } catch(Throwable expected) {
        assertEquals("Pong cannot exceed maxWebSocketFrameSize or 125 bytes", expected.getMessage());
        ws.close();
        testComplete();
      }
    });
  }

  private void testServerWebsocketPingPongCheck(int maxFrameSize, Consumer<ServerWebSocket> check) {
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebsocketFrameSize(maxFrameSize));
    server.websocketHandler(ws -> {
      ws.pongHandler(buff -> fail());
      check.accept(ws);
    }).listen(onSuccess(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {});
    }));
    await();
  }

  @Test
  public void testServerWebsocketSendPingExceeds125Bytes() {
    //Netty will prevent us from encoding a pingBody greater than 126 bytes by silently throwing an error in the background
    String pingBody = randomAlphaString(126);
    Integer maxFrameSize = 256;
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebsocketFrameSize(maxFrameSize));
    server.websocketHandler(ws -> {
      ws.pongHandler(buff -> fail());
      ws.writeFrame(WebSocketFrame.pingFrame(Buffer.buffer(pingBody)));
      vertx.setTimer(2000, id -> testComplete());
    }).listen(onSuccess(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {});
    }));
    await();
  }

  @Test
  public void testClientWebsocketSendPingExceeds125Bytes() {
    //Netty will prevent us from encoding a pingBody greater than 126 bytes by silently throwing an error in the background
    String pingBody = randomAlphaString(126);
    Integer maxFrameSize = 256;
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebsocketFrameSize(maxFrameSize));
    server.websocketHandler(ws -> { }).listen(onSuccess(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        ws.pongHandler(buffer -> fail());
        ws.writeFrame(WebSocketFrame.pingFrame(Buffer.buffer(pingBody)));
        vertx.setTimer(2000, id -> testComplete());
      });
    }));
    await();
  }

  @Test
  public void testServerWebsocketSendPongExceeds125Bytes() {
    //Netty will prevent us from encoding a pingBody greater than 126 bytes by silently throwing an error in the background
    String pingBody = randomAlphaString(126);
    Integer maxFrameSize = 256;
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebsocketFrameSize(maxFrameSize));
    server.websocketHandler(ws -> {
      ws.writeFrame(WebSocketFrame.pongFrame(Buffer.buffer(pingBody)));
    }).listen(onSuccess(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        ws.pongHandler(buff -> fail());
        vertx.setTimer(2000, id -> testComplete());
      });
    }));
    await();
  }

  @Test
  public void testClientWebsocketSendPongExceeds125Bytes() {
    //Netty will prevent us from encoding a pingBody greater than 126 bytes by silently throwing an error in the background
    String pingBody = randomAlphaString(126);
    Integer maxFrameSize = 256;
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebsocketFrameSize(maxFrameSize));
    server.websocketHandler(ws -> {
      ws.pongHandler(buff -> fail());
      vertx.setTimer(2000, id -> testComplete());
    }).listen(onSuccess(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        ws.writeFrame(WebSocketFrame.pongFrame(Buffer.buffer(pingBody)));
      });
    }));
    await();
  }

  @Test
  public void testServerWebsocketReceivePongExceedsMaxFrameSize() {
    String pingBody = randomAlphaString(113);
    Integer maxFrameSize = 64;
    Buffer ping1 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(0, maxFrameSize));
    Buffer ping2 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(maxFrameSize, pingBody.length()));

    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebsocketFrameSize(maxFrameSize));
    server.websocketHandler(ws -> {
      List<Buffer> pongs = new ArrayList<>();
      ws.pongHandler(pong -> {
        pongs.add(pong);
        if (pongs.size() == 2) {
          assertEquals(pongs, Arrays.asList(ping1, ping2));
          testComplete();
        }
      });
    }).listen(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        try {
          ws.writeFrame(new WebSocketFrameImpl(FrameType.PONG, ping1.copy().getByteBuf(), false));
          ws.writeFrame(new WebSocketFrameImpl(FrameType.PONG, ping2.copy().getByteBuf(), true));
        } catch(Throwable t) {
          fail(t);
        }
      });
    });
    await();
  }

  @Test
  public void testClientWebsocketReceivePongExceedsMaxFrameSize() {
    String pingBody = randomAlphaString(113);
    Integer maxFrameSize = 64;
    Buffer ping1 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(0, maxFrameSize));
    Buffer ping2 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(maxFrameSize, pingBody.length()));

    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebsocketFrameSize(maxFrameSize));
    server.websocketHandler(ws -> {
      try {
        ws.writeFrame(new WebSocketFrameImpl(FrameType.PONG, ping1.copy().getByteBuf(), false));
        ws.writeFrame(new WebSocketFrameImpl(FrameType.PONG, ping2.copy().getByteBuf(), true));
      } catch(Throwable t) {
        fail(t);
      }
    }).listen(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        List<Buffer> pongs = new ArrayList<>();
        ws.pongHandler(pong -> {
          pongs.add(pong);
          if (pongs.size() == 2) {
            assertEquals(pongs, Arrays.asList(ping1, ping2));
            testComplete();
          }
        });
      });
    });
    await();
  }

  @Test
  public void testServerWebsocketReceivePingExceedsMaxFrameSize() {
    String pingBody = randomAlphaString(113);
    Integer maxFrameSize = 64;
    Buffer ping1 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(0, maxFrameSize));
    Buffer ping2 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(maxFrameSize, pingBody.length()));

    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebsocketFrameSize(maxFrameSize));
    server.websocketHandler(ws -> {

    }).listen(onSuccess(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        List<Buffer> pongs = new ArrayList<>();
        ws.pongHandler(pong -> {
          pongs.add(pong);
          if (pongs.size() == 2) {
            assertEquals(pongs, Arrays.asList(ping1, ping2));
            testComplete();
          }
        });
        try {
          ws.writeFrame(new WebSocketFrameImpl(FrameType.PING, ping1.copy().getByteBuf(), false));
          ws.writeFrame(new WebSocketFrameImpl(FrameType.PING, ping2.copy().getByteBuf(), true));
        } catch(Throwable t) {
          fail(t);
        }
      });
    }));
    await();
  }

  @Test
  public void testClientWebsocketReceivePingExceedsMaxFrameSize() {
    String pingBody = randomAlphaString(113);
    Integer maxFrameSize = 64;
    Buffer ping1 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(0, maxFrameSize));
    Buffer ping2 = Buffer.buffer(Buffer.buffer(pingBody.getBytes()).getBytes(maxFrameSize, pingBody.length()));

    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST).setMaxWebsocketFrameSize(maxFrameSize));
    server.websocketHandler(ws -> {

    }).listen(onSuccess(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        List<Buffer> pongs = new ArrayList<>();
        ws.pongHandler(pong -> {
          pongs.add(pong);
          if (pongs.size() == 2) {
            assertEquals(pongs, Arrays.asList(ping1, ping2));
            testComplete();
          }
        });
        try {
          ws.writeFrame(new WebSocketFrameImpl(FrameType.PING, ping1.copy().getByteBuf(), false));
          ws.writeFrame(new WebSocketFrameImpl(FrameType.PING, ping2.copy().getByteBuf(), true));
        } catch(Throwable t) {
          fail(t);
        }
      });
    }));
    await();
  }

  @Test
  public void testClientWebsocketPingPong() {
    server = vertx.createHttpServer(new HttpServerOptions().setIdleTimeout(1).setPort(DEFAULT_HTTP_PORT).setHost(HttpTestBase.DEFAULT_HTTP_HOST));
    server.websocketHandler(ws -> {
    }).listen(onSuccess(v -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
        ws.pongHandler( pong -> {
          assertEquals("ping", pong.toString());
          testComplete();
        });
        ws.writePing(Buffer.buffer("ping"));
      });
    }));
    await();
  }

  @Test
  public void testWebsocketAbs() {
    SelfSignedCertificate certificate = SelfSignedCertificate.create();
    HttpServerOptions serverOptions = new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTPS_PORT)
      .setSsl(true)
      .setKeyCertOptions(certificate.keyCertOptions());
    HttpClientOptions clientOptions = new HttpClientOptions()
      .setTrustAll(true)
      .setVerifyHost(false);
    client = vertx.createHttpClient(clientOptions);
    server = vertx.createHttpServer(serverOptions).requestHandler(request -> {
      if ("/test".equals(request.path())) {
        request.upgrade().close();
      } else {
        request.response().end();
      }
    }).listen(onSuccess(server -> {
      String url = "wss://" + clientOptions.getDefaultHost() + ":" + HttpTestBase.DEFAULT_HTTPS_PORT + "/test";
      client.websocketAbs(url, null, null, null, ws -> {
        ws.closeHandler(v -> {
          testComplete();
        });
      }, null);
    }));
    await();
  }

  @Test
  public void testCloseStatusCodeFromServer() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);
    client = vertx.createHttpClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .websocketHandler(socket -> {
        socket.closeHandler(a -> {
          latch.countDown();
        });
        vertx.setTimer(1000, (ar) -> socket.close());
      })
      .listen(ar -> {
        client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
          ws.frameHandler(frame -> {
            assertEquals(1000, frame.binaryData().getByteBuf().getShort(0));
            assertEquals(1000, frame.closeStatusCode());
            assertNull(frame.closeReason());
            latch.countDown();
          });
        });
    });
    awaitLatch(latch);
  }

  @Test
  public void testCloseStatusCodeFromClient() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);
    client = vertx.createHttpClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .websocketHandler(socket -> {
        socket.closeHandler(a -> {
          latch.countDown();
        });
        socket.frameHandler(frame -> {
          assertEquals(1000, frame.binaryData().getByteBuf().getShort(0));
          assertEquals(1000, frame.closeStatusCode());
          assertNull(frame.closeReason());
          latch.countDown();
        });
      })
      .listen(ar -> {
        client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
          ws.close();
        });
      });
    awaitLatch(latch);
  }

  @Test
  public void testCloseFrame() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);
    client = vertx.createHttpClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .websocketHandler(socket -> {
        socket.closeHandler(a -> {
          latch.countDown();
        });
        socket.frameHandler(frame -> {
          if (frame.isText()) {
            assertIllegalStateException(frame::closeStatusCode);
          } else {
            assertEquals(frame.closeReason(), "It was a good talk");
            assertEquals(frame.closeStatusCode(), 1001);
          }
          latch.countDown();
        });
      })
      .listen(ar -> {
        client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
          ws.writeTextMessage("Hello");
          ws.close((short)1001, "It was a good talk");
        });
      });
    awaitLatch(latch);
  }

  @Test
  public void testCloseCustomPayloadFromServer() throws InterruptedException {
    final String REASON = "I'm moving away!";
    final short STATUS_CODE = (short)1001;

    CountDownLatch latch = new CountDownLatch(2);
    client = vertx.createHttpClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .websocketHandler(socket -> {
        socket.closeHandler(a -> {
          latch.countDown();
        });
        vertx.setTimer(1000, (ar) -> socket.close(STATUS_CODE, REASON));
      })
      .listen(ar -> {
        client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
          ws.frameHandler(frame -> {
            assertEquals(REASON, frame.binaryData().getByteBuf().readerIndex(2).toString(StandardCharsets.UTF_8));
            assertEquals(STATUS_CODE, frame.binaryData().getByteBuf().getShort(0));
            assertEquals(REASON, frame.closeReason());
            assertEquals(STATUS_CODE, frame.closeStatusCode());
            latch.countDown();
          });
        });
      });
    awaitLatch(latch);
  }

  @Test
  public void testCloseCustomPayloadFromClient() throws InterruptedException {
    final String REASON = "I'm moving away!";
    final short STATUS_CODE = (short)1001;

    CountDownLatch latch = new CountDownLatch(2);
    client = vertx.createHttpClient();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .websocketHandler(socket -> {
        socket.closeHandler(a -> {
          latch.countDown();
        });
        socket.frameHandler(frame -> {
          assertEquals(REASON, frame.binaryData().getByteBuf().readerIndex(2).toString(StandardCharsets.UTF_8));
          assertEquals(STATUS_CODE, frame.binaryData().getByteBuf().getShort(0));
          assertEquals(REASON, frame.closeReason());
          assertEquals(STATUS_CODE, frame.closeStatusCode());
          latch.countDown();
        });
      })
      .listen(ar -> {
        client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", ws -> {
          ws.close(STATUS_CODE, REASON);
        });
      });
    awaitLatch(latch);
  }

  @Test
  public void testCleanServerClose() {
    waitFor(2);
    server = vertx.createHttpServer();
    server.websocketHandler(ws -> {
      ws.closeHandler(v -> {
        complete();
      });
    });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(v1 -> {
      client = vertx.createHttpClient();
      handshake(res -> {
        NetSocketInternal so = (NetSocketInternal) res;
        so.channelHandlerContext().pipeline().addBefore("handler", "encoder", new WebSocket13FrameEncoder(true));
        so.channelHandlerContext().pipeline().addBefore("handler", "decoder", new WebSocket13FrameDecoder(false, false, 1000));
        int status = 4000 + TestUtils.randomPositiveInt() % 100;
        String reason = TestUtils.randomAlphaString(10);
        so.writeMessage(new CloseWebSocketFrame(status, reason));
        Deque<Object> received = new ArrayDeque<>();
        so.messageHandler(received::add);
        so.closeHandler(v2 -> {
          assertEquals(1, received.size());
          Object msg = received.getFirst();
          assertEquals(msg.getClass(), CloseWebSocketFrame.class);
          CloseWebSocketFrame frame = (CloseWebSocketFrame) msg;
          assertEquals(status, frame.statusCode());
          assertEquals(reason, frame.reasonText());
          complete();
        });
      });
    }));
    await();
  }

  @Test
  public void testCleanClientClose() {
    waitFor(2);
    server = vertx.createHttpServer();
    server.requestHandler(req -> {
      NetSocketInternal so = (NetSocketInternal) handshake(req);
      if (so != null) {
        so.channelHandlerContext().pipeline().addBefore("handler", "encoder", new WebSocket13FrameEncoder(false));
        so.channelHandlerContext().pipeline().addBefore("handler", "decoder", new WebSocket13FrameDecoder(true, false, 1000));
        Deque<Object> received = new ArrayDeque<>();
        so.messageHandler(received::add);
        int status = 4000 + TestUtils.randomPositiveInt() % 100;
        String reason = TestUtils.randomAlphaString(10);
        so.writeMessage(new CloseWebSocketFrame(status, reason));
        so.closeHandler(v -> {
          assertEquals(1, received.size());
          Object msg = received.getFirst();
          assertEquals(msg.getClass(), CloseWebSocketFrame.class);
          CloseWebSocketFrame frame = (CloseWebSocketFrame) msg;
          assertEquals(status, frame.statusCode());
          assertEquals(reason, frame.reasonText());
          complete();
        });
      }
    });
    server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(v1 -> {
      client = vertx.createHttpClient();
      client.websocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/chat", ws -> {
        ws.closeHandler(v -> {
          complete();
        });
      });
    }));
    await();
  }

  @Test
  public void testReportProtocolViolationOnClient() {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).requestHandler(req -> {
      NetSocket sock = getUpgradedNetSocket(req, "/some/path");
      // Let's write an invalid frame
      Buffer buff = Buffer.buffer();
      buff.appendByte((byte)(0x8)).appendByte((byte)0); // Violates protocol with V13 (final control frame)
      sock.write(buff);
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocketStream(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/some/path", null, WebsocketVersion.V13).
        handler(ws -> {
          AtomicReference<Throwable> failure = new AtomicReference<>();
          ws.closeHandler(v -> {
            assertNotNull(failure.get());
            testComplete();
          });
          ws.exceptionHandler(failure::set);
        });
    });
    await();
  }

  @Test
  public void testReportProtocolViolationOnServer() {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      AtomicReference<Throwable> failure = new AtomicReference<>();
      ws.closeHandler(v -> {
        assertNotNull(failure.get());
        testComplete();
      });
      ws.exceptionHandler(failure::set);
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      handshake(sock -> {
        // Let's write an invalid frame
        Buffer buff = Buffer.buffer();
        buff.appendByte((byte)(0x8)).appendByte((byte)0); // Violates protocol with V13 (final control frame)
        sock.write(buff);
      });
    });
    await();
  }

  @Test
  public void testServerWebSocketShouldBeClosedWhenTheClosedHandlerIsCalled() {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), ws);
      sender.send();
      ws.closeHandler(v -> {
        Throwable failure = sender.close();
        if (failure != null) {
          fail(failure);
        } else {
          testComplete();
        }
      });
    });
    server.listen(onSuccess(s -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/someuri", ws -> {
        vertx.setTimer(1000, id -> {
          ws.close();
        });
      });
    }));
    await();
  }

  @Test
  public void testClientWebSocketShouldBeClosedWhenTheClosedHandlerIsCalled() {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      vertx.setTimer(1000, id -> {
        ws.close();
      });
    });
    server.listen(onSuccess(s -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/someuri", ws -> {
        CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), ws);
        sender.send();
        ws.closeHandler(v -> {
          Throwable failure = sender.close();
          if (failure != null) {
            fail(failure);
          } else {
            testComplete();
          }
        });
      });
    }));
    await();
  }

  @Test
  public void testDontReceiveMessagerAfterCloseHandlerCalled() {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
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
          testComplete();
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
    server.listen(onSuccess(s -> {
      client.websocket(DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/someuri", ws -> {
        CheckingSender sender = new CheckingSender(vertx.getOrCreateContext(), ws);
        ws.closeHandler(v -> sender.close());
        sender.send();
      });
    }));
    await();
  }

  @Test
  public void testNoRequestHandler() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    vertx.createHttpServer()
      .websocketHandler(ws -> fail())
      .listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(v -> latch.countDown()));
    awaitLatch(latch);
    client.getNow(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/", onSuccess(resp -> {
      resp.endHandler(v -> {
        assertEquals(400, resp.statusCode());
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testPausedDuringLastChunk() {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT))
      .websocketHandler(ws -> {
        AtomicBoolean paused = new AtomicBoolean(true);
        ws.pause();
        ws.closeHandler(v -> {
          paused.set(false);
          ws.resume();
        });
        ws.endHandler(v -> {
          assertFalse(paused.get());
          testComplete();
        });
      })
      .listen(onSuccess(v -> {
        client.websocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri", ws -> {
          ws.close();
        });
      }));
    await();
  }

  @Test
  public void testContext() throws Exception {
    waitFor(2);
    Context serverCtx = vertx.getOrCreateContext();
    server = vertx.createHttpServer()
      .websocketHandler(ws -> {
        Context current = Vertx.currentContext();
        assertSameEventLoop(serverCtx, current);
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
          complete();
        });
      });
    CountDownLatch latch = new CountDownLatch(1);
    serverCtx.runOnContext(v -> {
      server.listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, onSuccess(s -> latch.countDown()));
    });
    awaitLatch(latch);
    Context clientCtx = vertx.getOrCreateContext();
    clientCtx.runOnContext(v -> {
      client.websocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri", ws -> {
        assertEquals(clientCtx, Vertx.currentContext());
        ws.write(Buffer.buffer("data"));
        ws.pongHandler(pong -> {
          assertEquals(clientCtx, Vertx.currentContext());
          complete();
          ws.close();
        });
        ws.writePing(Buffer.buffer("ping"));
      });
    });
    await();
  }

  @Test
  public void testDrainServerWebSocket() {
    Future<Void> resume = Future.future();
    server = vertx.createHttpServer()
      .websocketHandler(ws -> {
        while (!ws.writeQueueFull()) {
          ws.writeFrame(WebSocketFrame.textFrame(randomAlphaString(512), true));
        }
        ws.drainHandler(v -> {
          testComplete();
        });
        resume.complete();
      }).listen(DEFAULT_HTTP_PORT, onSuccess(v1 -> {
        client.websocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri", ws -> {
          ws.pause();
          resume.setHandler(onSuccess(v2 -> {
            ws.resume();
          }));
        });
      }));
    await();
  }

  @Test
  public void testDrainClientWebSocket() {
    Future<Void> resume = Future.future();
    server = vertx.createHttpServer()
      .websocketHandler(ws -> {
        ws.pause();
        resume.setHandler(onSuccess(v2 -> {
          ws.resume();
        }));
      }).listen(DEFAULT_HTTP_PORT, onSuccess(v1 -> {
        client.websocket(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/someuri", ws -> {
          while (!ws.writeQueueFull()) {
            ws.writeFrame(WebSocketFrame.textFrame(randomAlphaString(512), true));
          }
          ws.drainHandler(v -> {
            testComplete();
          });
          resume.complete();
        });
      }));
    await();
  }
}
