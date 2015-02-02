/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;


import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.ServerWebSocketStream;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.http.WebsocketVersion;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.ReadStream;
import org.junit.Test;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebsocketTest extends VertxTestBase {

  private HttpClient client;
  private HttpServer server;

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
    super.tearDown();
  }

  @Test
  public void testRejectHybi00() throws Exception {
    testReject(WebsocketVersion.V00);
  }

  @Test
  public void testRejectHybi08() throws Exception {
    testReject(WebsocketVersion.V08);
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
    testTLS(KS.NONE, TS.NONE, KS.JKS, TS.NONE, false, false, true, false, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert() throws Exception {
    testTLS(KS.NONE, TS.JKS, KS.JKS, TS.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertPKCS12() throws Exception {
    testTLS(KS.NONE, TS.JKS, KS.PKCS12, TS.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCertPEM() throws Exception {
    testTLS(KS.NONE, TS.JKS, KS.PEM, TS.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client trusts via a CA (not trust all)
  public void testTLSClientTrustServerCertPEM_CA() throws Exception {
    testTLS(KS.NONE, TS.PEM_CA, KS.PEM_CA, TS.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustPKCS12ServerCert() throws Exception {
    testTLS(KS.NONE, TS.PKCS12, KS.JKS, TS.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustPEMServerCert() throws Exception {
    testTLS(KS.NONE, TS.PEM, KS.JKS, TS.NONE, false, false, false, false, true);
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer() throws Exception {
    testTLS(KS.NONE, TS.NONE, KS.JKS, TS.NONE, false, false, false, false, false);
  }

  @Test
  //Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired() throws Exception {
    testTLS(KS.JKS, TS.JKS, KS.JKS, TS.JKS, false, false, false, false, true);
  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertRequired() throws Exception {
    testTLS(KS.JKS, TS.JKS, KS.JKS, TS.JKS, true, false, false, false, true);
  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertRequiredPKCS12() throws Exception {
    testTLS(KS.JKS, TS.JKS, KS.JKS, TS.PKCS12, true, false, false, false, true);
  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertRequiredPEM() throws Exception {
    testTLS(KS.JKS, TS.JKS, KS.JKS, TS.PEM, true, false, false, false, true);
  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertPKCS12Required() throws Exception {
    testTLS(KS.PKCS12, TS.JKS, KS.JKS, TS.JKS, true, false, false, false, true);
  }

  @Test
  //Client specifies cert and it is required
  public void testTLSClientCertPEMRequired() throws Exception {
    testTLS(KS.PEM, TS.JKS, KS.JKS, TS.JKS, true, false, false, false, true);
  }

  @Test
  //Client specifies cert signed by CA and it is required
  public void testTLSClientCertPEM_CARequired() throws Exception {
    testTLS(KS.PEM_CA, TS.JKS, KS.JKS, TS.PEM_CA, true, false, false, false, true);
  }

  @Test
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    testTLS(KS.NONE, TS.JKS, KS.JKS, TS.JKS, true, false, false, false, false);
  }

  @Test
  //Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted() throws Exception {
    testTLS(KS.JKS, TS.JKS, KS.JKS, TS.NONE, true, false, false, false, false);
  }

  @Test
  // Server specifies cert that the client does not trust via a revoked certificate of the CA
  public void testTLSClientRevokedServerCert() throws Exception {
    testTLS(KS.NONE, TS.PEM_CA, KS.PEM_CA, TS.NONE, false, false, false, true, false);
  }

  @Test
  //Client specifies cert that the server does not trust via a revoked certificate of the CA
  public void testTLSRevokedClientCertServer() throws Exception {
    testTLS(KS.PEM_CA, TS.JKS, KS.JKS, TS.PEM_CA, true, true, false, false, false);
  }

  @Test
  // Test with cipher suites
  public void testTLSCipherSuites() throws Exception {
    testTLS(KS.NONE, TS.NONE, KS.JKS, TS.NONE, false, false, true, false, true, ENABLED_CIPHER_SUITES);
  }

  private void testTLS(KS clientCert, TS clientTrust,
                       KS serverCert, TS serverTrust,
                       boolean requireClientAuth, boolean serverUsesCrl, boolean clientTrustAll,
                       boolean clientUsesCrl, boolean shouldPass,
                       String... enabledCipherSuites) throws Exception {
    HttpClientOptions options = new HttpClientOptions();
    options.setSsl(true);
    if (clientTrustAll) {
      options.setTrustAll(true);
    }
    if (clientUsesCrl) {
      options.addCrlPath(findFileOnClasspath("tls/ca/crl.pem"));
    }
    setOptions(options, getClientTrustOptions(clientTrust));
    setOptions(options, getClientCertOptions(clientCert));
    for (String suite: enabledCipherSuites) {
      options.addEnabledCipherSuite(suite);
    }
    client = vertx.createHttpClient(options);
    HttpServerOptions serverOptions = new HttpServerOptions();
    serverOptions.setSsl(true);
    setOptions(serverOptions, getServerTrustOptions(serverTrust));
    setOptions(serverOptions, getServerCertOptions(serverCert));
    if (requireClientAuth) {
      serverOptions.setClientAuthRequired(true);
    }
    if (serverUsesCrl) {
      serverOptions.addCrlPath(findFileOnClasspath("tls/ca/crl.pem"));
    }
    for (String suite: enabledCipherSuites) {
      serverOptions.addEnabledCipherSuite(suite);
    }
    server = vertx.createHttpServer(serverOptions.setPort(4043));
    server.websocketHandler(ws -> {
      ws.handler(ws::write);
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocketStream(4043, HttpTestBase.DEFAULT_HTTP_HOST, "/").
          exceptionHandler(t -> {
            if (shouldPass) {
              t.printStackTrace();
              fail("Should not throw exception");
            } else {
              testComplete();
            }
          }).
          handler(ws -> {
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
      });
    });
    await();
  }

  @Test
  // Let's manually handle the websocket handshake and write a frame to the client
  public void testHandleWSManually() throws Exception {
    String path = "/some/path";
    String message = "here is some text data";

    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT)).requestHandler(req -> {
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
      client.websocketStream(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path).
          exceptionHandler(t -> fail(t.getMessage())).
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

    int numServers = 5;
    int numConnections = numServers * 100;

    List<HttpServer> servers = new ArrayList<>();
    Set<HttpServer> connectedServers = new ConcurrentHashSet<>();
    Map<HttpServer, Integer> connectCount = new ConcurrentHashMap<>();

    CountDownLatch latchListen = new CountDownLatch(numServers);
    CountDownLatch latchConns = new CountDownLatch(numConnections);
    for (int i = 0; i < numServers; i++) {
      HttpServer theServer = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));
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
      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/someuri", ws -> {
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
    assertEquals("Upgrade", req.headers().get("Connection"));
    NetSocket sock = req.netSocket();
    String secHeader = req.headers().get("Sec-WebSocket-Key");
    String tmp = secHeader + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    String encoded = sha1(tmp);
    sock.write("HTTP/1.1 101 Web Socket Protocol Handshake\r\n" +
      "Upgrade: WebSocket\r\n" +
      "Connection: Upgrade\r\n" +
      "Sec-WebSocket-Accept: " + encoded + "\r\n" +
      "\r\n");
    return sock;
  }

  private void testWSWriteStream(WebsocketVersion version) throws Exception {

    String path = "/some/path";
    String query = "foo=bar&wibble=eek";
    String uri = path + "?" + query;

    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      assertEquals(uri, ws.uri());
      assertEquals(path, ws.path());
      assertEquals(query, ws.query());
      assertEquals("Upgrade", ws.headers().get("Connection"));
      ws.handler(data -> ws.write(data));
    });

    server.listen(ar -> {
      assertTrue(ar.succeeded());
      int bsize = 100;
      int sends = 10;

      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path + "?" + query, null, version, ws -> {
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
    String query = "foo=bar&wibble=eek";
    String uri = path + "?" + query;

    // version 0 doesn't support continuations so we just send 1 frame per message
    int frames = version == WebsocketVersion.V00 ? 1: 10;

    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      assertEquals(uri, ws.uri());
      assertEquals(path, ws.path());
      assertEquals(query, ws.query());
      assertEquals("Upgrade", ws.headers().get("Connection"));
      AtomicInteger count = new AtomicInteger();
      ws.frameHandler(frame -> {
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
      });
    });

    server.listen(ar -> {
      assertTrue(ar.succeeded());
      int bsize = 100;

      int msgs = 10;

      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path + "?" + query, null,
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
              testComplete();
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

  private void testContinuationWriteFromConnectHandler(WebsocketVersion version) throws Exception {
    String path = "/some/path";
    String firstFrame = "AAA";
    String continuationFrame = "BBB";

    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT)).requestHandler(req -> {
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
      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version, ws -> {
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

    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      assertEquals(path, ws.path());
      ws.writeFrame(WebSocketFrame.binaryFrame(buff, true));
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version, ws -> {
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

  private void testValidSubProtocol(WebsocketVersion version) throws Exception {
    String path = "/some/path";
    String subProtocol = "myprotocol";
    Buffer buff = Buffer.buffer("AAA");
    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT).setWebsocketSubProtocol(subProtocol)).websocketHandler(ws -> {
      assertEquals(path, ws.path());
      ws.writeFrame(WebSocketFrame.binaryFrame(buff, true));
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version, subProtocol, ws -> {
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
    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT).setWebsocketSubProtocol("invalid")).websocketHandler(ws -> {
    });
    server.listen(onSuccess(ar -> {
      client.websocketStream(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version, subProtocol).
          exceptionHandler(t -> {
            // Should fail
            testComplete();
          }).
          handler(ws -> {
          });
    }));
    await();
  }

  private void testReject(WebsocketVersion version) throws Exception {

    String path = "/some/path";

    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      assertEquals(path, ws.path());
      ws.reject();
    });

    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocketStream(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version).
          exceptionHandler(t -> testComplete()).
          handler(ws -> fail("Should not be called"));
    });
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
    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT)).websocketHandler(ws -> {
      ws.writeMessage(Buffer.buffer(expected));
      ws.close();
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null, version, ws -> {
        Buffer actual = Buffer.buffer();
        ws.handler(actual::appendBuffer);
        ws.closeHandler(v -> {
          assertArrayEquals(expected, actual.getBytes());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testWebsocketPauseAndResume() {
    client.close();
    client = vertx.createHttpClient(new HttpClientOptions().setConnectTimeout(1000));
    String path = "/some/path";
    this.server = vertx.createHttpServer(new HttpServerOptions().setAcceptBacklog(1).setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    AtomicBoolean paused = new AtomicBoolean();
    ReadStream<ServerWebSocket> stream = server.websocketStream();
    stream.handler(ws -> {
      assertFalse(paused.get());
      ws.writeMessage(Buffer.buffer("whatever"));
      ws.close();
    });
    server.listen(listenAR -> {
      assertTrue(listenAR.succeeded());
      stream.pause();
      paused.set(true);
      client.websocketStream(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path).
          exceptionHandler(err -> {
            assertTrue(paused.get());
            assertTrue(err instanceof WebSocketHandshakeException);
            paused.set(false);
            stream.resume();
            client.websocket(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, ws -> {
              ws.handler(buffer -> {
                assertEquals("whatever", buffer.toString("UTF-8"));
                ws.closeHandler(v2 -> {
                  testComplete();
                });
              });
            });
          }).
          handler(ws -> fail());
    });
    await();
  }

  @Test
  public void testClosingServerClosesWebSocketStreamEndHandler() {
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    ReadStream<ServerWebSocket> stream = server.websocketStream();
    AtomicBoolean closed = new AtomicBoolean();
    stream.endHandler(v -> closed.set(true));
    stream.handler(ws -> {});
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
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    AtomicInteger done = new AtomicInteger();
    ServerWebSocketStream stream = server.websocketStream();
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
    this.server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));
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
  public void testEndHandlerCalled() {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT)).websocketHandler(WebSocketBase::close);
    AtomicInteger doneCount = new AtomicInteger();
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocketStream(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null).
          endHandler(done -> doneCount.incrementAndGet()).
          handler(ws -> {
            assertEquals(0, doneCount.get());
            ws.closeHandler(v -> {
              assertEquals(1, doneCount.get());
              testComplete();
            });
          });
    });
    await();
  }

  @Test
  public void testClearClientHandlersOnEnd() {
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT)).websocketHandler(WebSocketBase::close);
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocketStream(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null).
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
    String path = "/some/path";
    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));
    server.requestHandler(request -> {
      ServerWebSocket ws = request.upgrade();
      ws.write(Buffer.buffer("helloworld"));
      ws.close();
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.websocketStream(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, path, null).
        handler(ws -> {
          System.out.println("Connected");
          Buffer buff = Buffer.buffer();
          ws.handler(b -> {
            System.out.println("Got data");
            buff.appendBuffer(b);
          });
          ws.endHandler(v -> {
            assertEquals("helloworld", buff.toString());
            testComplete();
          });
        });
    });
    await();
  }

  @Test
  public void testUpgradeInvalidRequest() {
    server = vertx.createHttpServer(new HttpServerOptions().setPort(HttpTestBase.DEFAULT_HTTP_PORT));
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
      client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST, "/", resp -> {
      }).end();
    });
    await();
  }
}
