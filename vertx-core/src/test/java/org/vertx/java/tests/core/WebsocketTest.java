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

package org.vertx.java.tests.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.net.NetSocket;
import static org.vertx.java.tests.core.TestUtils.*;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebsocketTest extends VertxTestBase {

  private HttpClient client;
  private HttpServer server;

  @Before
  public void before() {
    client = vertx.createHttpClient().setHost("localhost").setPort(8080);
  }

  @After
  public void after() throws Exception {
    client.close();
    if (server != null) {
      CountDownLatch latch = new CountDownLatch(1);
      server.close(ar -> {
        assertTrue(ar.succeeded());
        latch.countDown();
      });
      assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
  }

  @Test
  public void testRejectHybi00() throws Exception {
    testReject(WebSocketVersion.HYBI_00);
  }

  @Test
  public void testRejectHybi08() throws Exception {
    testReject(WebSocketVersion.HYBI_08);
  }

  @Test
  public void testWSBinaryHybi00() throws Exception {
    testWS(true, WebSocketVersion.HYBI_00);
  }

  @Test
  public void testWSStringHybi00() throws Exception {
    testWS(false, WebSocketVersion.HYBI_00);
  }

  @Test
  public void testWSBinaryHybi08() throws Exception {
    testWS(true, WebSocketVersion.HYBI_08);
  }

  @Test
  public void testWSStringHybi08() throws Exception {
    testWS(false, WebSocketVersion.HYBI_08);
  }

  @Test
  public void testWSBinaryHybi17() throws Exception {
    testWS(true, WebSocketVersion.RFC6455);
  }

  @Test
  public void testWSStringHybi17() throws Exception {
    testWS(false, WebSocketVersion.RFC6455);
  }

  @Test
  public void testWriteFromConnectHybi00() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.HYBI_00);
  }

  @Test
  public void testWriteFromConnectHybi08() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.HYBI_08);
  }

  @Test
  public void testWriteFromConnectHybi17() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.RFC6455);
  }

  @Test
  public void testContinuationWriteFromConnectHybi08() throws Exception {
    testContinuationWriteFromConnectHandler(WebSocketVersion.HYBI_08);
  }

  @Test
  public void testContinuationWriteFromConnectHybi17() throws Exception {
    testContinuationWriteFromConnectHandler(WebSocketVersion.RFC6455);
  }

  @Test
  public void testValidSubProtocolHybi00() throws Exception {
    testValidSubProtocol(WebSocketVersion.HYBI_00);
  }

  @Test
  public void testValidSubProtocolHybi08() throws Exception {
    testValidSubProtocol(WebSocketVersion.HYBI_08);
  }

  @Test
  public void testValidSubProtocolHybi17() throws Exception {
    testValidSubProtocol(WebSocketVersion.RFC6455);
  }

  @Test
  public void testInvalidSubProtocolHybi00() throws Exception {
    testInvalidSubProtocol(WebSocketVersion.HYBI_00);
  }

  @Test
  public void testInvalidSubProtocolHybi08() throws Exception {
    testInvalidSubProtocol(WebSocketVersion.HYBI_08);
  }

  @Test
  public void testInvalidSubProtocolHybi17() throws Exception {
    testInvalidSubProtocol(WebSocketVersion.RFC6455);
  }

  // TODO close and exception tests
  // TODO pause/resume/drain tests
  // TODO websockets over HTTPS tests

  @Test
  // Let's manually handle the websocket handshake and write a frame to the client
  public void testHandleWSManually() throws Exception {
    String path = "/some/path";
    String message = "here is some text data";

    server = vertx.createHttpServer().requestHandler(req -> {
      NetSocket sock = getUpgradedNetSocket(req, path);
      // Let's write a Text frame raw
      Buffer buff = new Buffer();
      buff.appendByte((byte)129); // Text frame
      buff.appendByte((byte)message.length());
      buff.appendString(message);
      sock.write(buff);
    });
    server.listen(8080, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connectWebsocket(path, ws -> {
        ws.dataHandler(buff -> {
          assertEquals(message, buff.toString("UTF-8"));
          testComplete();
        });
      });
      client.exceptionHandler(t-> fail(t.getMessage()));
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
      HttpServer theServer = vertx.createHttpServer();
      servers.add(theServer);
      theServer.websocketHandler(ws -> {
        connectedServers.add(theServer);
        Integer cnt = connectCount.get(theServer);
        int icnt = cnt == null ? 0 : cnt;
        icnt++;
        connectCount.put(theServer, icnt);
        latchConns.countDown();
      }).listen(8080, "localhost", ar -> {
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
      client.connectWebsocket("/someuri", ws -> {
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
    HttpServer theServer = vertx.createHttpServer();
    theServer.websocketHandler(ws -> {
      fail("Should not connect");
    }).listen(4321, "localhost", ar -> {
      if (ar.succeeded()) {
        latch.countDown();
      } else {
        fail("Failed to bind server");
      }
    });
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    testSharedServersRoundRobin();
  }

  @Test
  public void testSharedServersRoundRobinButFirstStartAndStopServer() throws Exception {
    // Start and stop a server on the same port/host before hand to make sure it doesn't interact
    CountDownLatch latch = new CountDownLatch(1);
    HttpServer theServer = vertx.createHttpServer();
    theServer.websocketHandler(ws -> {
      fail("Should not connect");
    }).listen(4321, "localhost", ar -> {
      if (ar.succeeded()) {
        latch.countDown();
      } else {
        fail("Failed to bind server");
      }
    });
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    CountDownLatch closeLatch = new CountDownLatch(1);
    theServer.close(ar -> {
      assertTrue(ar.succeeded());
      closeLatch.countDown();
    });
    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
    testSharedServersRoundRobin();
  }

  private String sha1(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA1");
      //Hash the data
      byte[] bytes = md.digest(s.getBytes("UTF-8"));
      return Base64.encodeBytes(bytes);
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

  private void testWS(final boolean binary, final WebSocketVersion version) throws Exception {

    String path = "/some/path";
    String query = "foo=bar&wibble=eek";
    String uri = path + "?" + query;

    server = vertx.createHttpServer().websocketHandler(ws -> {
      assertEquals(uri, ws.uri());
      assertEquals(path, ws.path());
      assertEquals(query, ws.query());
      assertEquals("Upgrade", ws.headers().get("Connection"));
      ws.dataHandler(data -> ws.write(data));
    });

    server.listen(8080, "localhost", ar -> {
      assertTrue(ar.succeeded());
      int bsize = 100;
      int sends = 10;

      client.connectWebsocket(path + "?" + query, version, ws -> {
          final Buffer received = new Buffer();
          ws.dataHandler(data -> {
            received.appendBuffer(data);
            if (received.length() == bsize * sends) {
              ws.close();
              testComplete();
            }
          });
          final Buffer sent = new Buffer();
          for (int i = 0; i < sends; i++) {
            if (binary) {
              Buffer buff = new Buffer(randomByteArray(bsize));
              ws.writeBinaryFrame(buff);
              sent.appendBuffer(buff);
            } else {
              String str = randomAlphaString(bsize);
              ws.writeTextFrame(str);
              sent.appendBuffer(new Buffer(str, "UTF-8"));
            }
          }
        });
    });
    await();
  }

  private void testContinuationWriteFromConnectHandler(final WebSocketVersion version) throws Exception {
    String path = "/some/path";
    String firstFrame = "AAA";
    String continuationFrame = "BBB";

    server = vertx.createHttpServer().requestHandler(req -> {
      NetSocket sock = getUpgradedNetSocket(req, path);

      // Let's write a Text frame raw
      Buffer buff = new Buffer();
      buff.appendByte((byte) 0x01); // Incomplete Text frame
      buff.appendByte((byte) firstFrame.length());
      buff.appendString(firstFrame);
      sock.write(buff);

      buff = new Buffer();
      buff.appendByte((byte) (0x00 | 0x80)); // Complete continuation frame
      buff.appendByte((byte) continuationFrame.length());
      buff.appendString(continuationFrame);
      sock.write(buff);
    });

    server.listen(8080, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connectWebsocket(path, version, ws -> {
        AtomicBoolean receivedFirstFrame = new AtomicBoolean();
        ws.frameHandler(received -> {
          Buffer receivedBuffer = new Buffer(received.textData());
          if (!received.isFinalFrame()) {
            assertEquals(firstFrame, receivedBuffer.toString());
            receivedFirstFrame.set(true);
          } else if (receivedFirstFrame.get() && received.isFinalFrame()) {
            assertEquals(continuationFrame, receivedBuffer.toString());
            ws.close();
            testComplete();
          }
        });
      });
    });
    await();
  }

  private void testWriteFromConnectHandler(final WebSocketVersion version) throws Exception {

    String path = "/some/path";
    Buffer buff = new Buffer("AAA");

    server = vertx.createHttpServer().websocketHandler(ws -> {
      assertEquals(path, ws.path());
      ws.writeBinaryFrame(buff);
    });
    server.listen(8080, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connectWebsocket(path, version, ws -> {
        Buffer received = new Buffer();
        ws.dataHandler(data -> {
          received.appendBuffer(data);
          if (received.length() == buff.length()) {
            assertTrue(buffersEqual(buff, received));
            ws.close();
            testComplete();
          }
        });
      });
    });
    await();
  }

  private void testValidSubProtocol(final WebSocketVersion version) throws Exception {
    String path = "/some/path";
    String subProtocol = "myprotocol";
    Buffer buff = new Buffer("AAA");
    server = vertx.createHttpServer().setWebSocketSubProtocols(subProtocol).websocketHandler(ws -> {
      assertEquals(path, ws.path());
      ws.writeBinaryFrame(buff);
    });
    server.listen(8080, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connectWebsocket(path, version, null, Collections.singleton(subProtocol), ws -> {
        final Buffer received = new Buffer();
        ws.dataHandler(data -> {
          received.appendBuffer(data);
          if (received.length() == buff.length()) {
            assertTrue(buffersEqual(buff, received));
            ws.close();
            testComplete();
          }
        });
      });
    });
    await();
  }

  private void testInvalidSubProtocol(final WebSocketVersion version) throws Exception {
    String path = "/some/path";
    String subProtocol = "myprotocol";
    Buffer buff = new Buffer("AAA");

    server = vertx.createHttpServer().setWebSocketSubProtocols("invalid").websocketHandler(ws -> {
      assertEquals(path, ws.path());
      ws.writeBinaryFrame(buff);
    });
    server.listen(8080, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.connectWebsocket(path, version, null, Collections.singleton(subProtocol), ws -> {
        final Buffer received = new Buffer();
        ws.dataHandler(data -> {
          received.appendBuffer(data);
          if (received.length() == buff.length()) {
            assertTrue(buffersEqual(buff, received));
            ws.close();
            testComplete();
          }
        });
      });
    });
    await();
  }

  private void testReject(final WebSocketVersion version) throws Exception {

    String path = "/some/path";

    server = vertx.createHttpServer().websocketHandler(ws -> {
      assertEquals(path, ws.path());
      ws.reject();
    });

    server.listen(8080, "localhost", ar -> {
      assertTrue(ar.succeeded());
      client.exceptionHandler(t -> testComplete());
      client.connectWebsocket(path, version, ws -> fail("Should not be called"));
    });
    await();
  }
}
