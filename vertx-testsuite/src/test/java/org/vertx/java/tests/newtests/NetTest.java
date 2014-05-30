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

package org.vertx.java.tests.newtests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.file.impl.ClasspathPathResolver;
import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.impl.SocketDefaults;
import org.vertx.java.testframework.TestUtils;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.vertx.java.tests.newtests.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetTest extends VertxTestBase {

  private NetServer server;
  private NetClient client;

  @Before
  public void before() throws Exception {
    client = vertx.createNetClient();
    server = vertx.createNetServer();
  }

  protected void awaitClose(NetServer server) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    server.close((asyncResult) -> {
      latch.countDown();
    });
    assertTrue(latch.await(10, TimeUnit.SECONDS));
  }

  @After
  public void after() throws Exception {
    if (client != null) {
      client.close();
    }
    if (server != null) {
      awaitClose(server);
    }
  }

  @Test
  public void testClientDefaults() {
    assertFalse(client.isSSL());
    assertNull(client.getKeyStorePassword());
    assertNull(client.getKeyStorePath());
    assertNull(client.getTrustStorePassword());
    assertNull(client.getTrustStorePath());
    assertFalse(client.isTrustAll());
    assertEquals(0, client.getReconnectAttempts());
    assertEquals(1000, client.getReconnectInterval());
    assertEquals(60000, client.getConnectTimeout());
    assertEquals(-1, client.getReceiveBufferSize());
    assertEquals(-1, client.getSendBufferSize());
    assertEquals(SocketDefaults.instance.isTcpKeepAlive(), client.isTCPKeepAlive());
    assertEquals(true, client.isTCPNoDelay());
    assertEquals(SocketDefaults.instance.getSoLinger(), client.getSoLinger());
    assertEquals(false, client.isUsePooledBuffers());
    assertEquals(SocketDefaults.instance.isReuseAddress(), client.isReuseAddress());
    assertEquals(-1, client.getTrafficClass());
    testComplete();
  }

  @Test
  public void testClientAttributes() {

    assertEquals(client, client.setSSL(false));
    assertFalse(client.isSSL());

    assertEquals(client, client.setSSL(true));
    assertTrue(client.isSSL());

    String pwd = TestUtils.randomUnicodeString(10);
    assertEquals(client, client.setKeyStorePassword(pwd));
    assertEquals(pwd, client.getKeyStorePassword());

    String path = TestUtils.randomUnicodeString(10);
    assertEquals(client, client.setKeyStorePath(path));
    assertEquals(path, client.getKeyStorePath());

    pwd = TestUtils.randomUnicodeString(10);
    assertEquals(client, client.setTrustStorePassword(pwd));
    assertEquals(pwd, client.getTrustStorePassword());

    path = TestUtils.randomUnicodeString(10);
    assertEquals(client, client.setTrustStorePath(path));
    assertEquals(path, client.getTrustStorePath());

    assertEquals(client, client.setReuseAddress(true));
    assertTrue(client.isReuseAddress());
    assertEquals(client, client.setReuseAddress(false));
    assertTrue(!client.isReuseAddress());

    assertEquals(client, client.setSoLinger(10));
    assertEquals(10, client.getSoLinger());

    assertEquals(client, client.setTCPKeepAlive(true));
    assertTrue(client.isTCPKeepAlive());
    assertEquals(client, client.setTCPKeepAlive(false));
    assertFalse(client.isTCPKeepAlive());

    assertEquals(client, client.setTCPNoDelay(true));
    assertTrue(client.isTCPNoDelay());
    assertEquals(client, client.setTCPNoDelay(false));
    assertFalse(client.isTCPNoDelay());

    assertEquals(client, client.setUsePooledBuffers(true));
    assertTrue(client.isUsePooledBuffers());
    assertEquals(client, client.setUsePooledBuffers(false));
    assertFalse(client.isUsePooledBuffers());

    int reconnectAttempts = new Random().nextInt(1000) + 1;
    assertEquals(client, client.setReconnectAttempts(reconnectAttempts));
    assertEquals(reconnectAttempts, client.getReconnectAttempts());

    try {
      client.setReconnectAttempts(-2);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int reconnectDelay = new Random().nextInt(1000) + 1;
    assertEquals(client, client.setReconnectInterval(reconnectDelay));
    assertEquals(reconnectDelay, client.getReconnectInterval());

    try {
      client.setReconnectInterval(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      client.setReconnectInterval(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }


    int rbs = new Random().nextInt(1024 * 1024) + 1;
    assertEquals(client, client.setReceiveBufferSize(rbs));
    assertEquals(rbs, client.getReceiveBufferSize());

    try {
      client.setReceiveBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      client.setReceiveBufferSize(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int sbs = new Random().nextInt(1024 * 1024);
    assertEquals(client, client.setSendBufferSize(sbs));
    assertEquals(sbs, client.getSendBufferSize());

    try {
      client.setSendBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      client.setSendBufferSize(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int trafficClass = new Random().nextInt(10000000);
    assertEquals(client, client.setTrafficClass(trafficClass));
    assertEquals(trafficClass, client.getTrafficClass());

    testComplete();
  }

  @Test
  public void testServerDefaults() {
    assertFalse(server.isSSL());
    assertNull(server.getKeyStorePassword());
    assertNull(server.getKeyStorePath());
    assertNull(server.getTrustStorePassword());
    assertNull(server.getTrustStorePath());
    assertFalse(server.isClientAuthRequired());
    assertEquals(1024, server.getAcceptBacklog());
    assertEquals(-1, server.getReceiveBufferSize());
    assertEquals(-1, server.getSendBufferSize());
    assertEquals(SocketDefaults.instance.isTcpKeepAlive(), server.isTCPKeepAlive());
    assertEquals(true, server.isTCPNoDelay());
    assertEquals(SocketDefaults.instance.getSoLinger(), server.getSoLinger());
    assertEquals(false, server.isUsePooledBuffers());
    assertEquals(true, server.isReuseAddress());
    assertEquals(-1, server.getTrafficClass());
    testComplete();
  }

  @Test
  public void testServerAttributes() {

    assertEquals(server, server.setSSL(false));
    assertFalse(server.isSSL());

    assertEquals(server, server.setSSL(true));
    assertTrue(server.isSSL());

    String pwd = TestUtils.randomUnicodeString(10);
    assertEquals(server, server.setKeyStorePassword(pwd));
    assertEquals(pwd, server.getKeyStorePassword());

    String path = TestUtils.randomUnicodeString(10);
    assertEquals(server, server.setKeyStorePath(path));
    assertEquals(path, server.getKeyStorePath());

    pwd = TestUtils.randomUnicodeString(10);
    assertEquals(server, server.setTrustStorePassword(pwd));
    assertEquals(pwd, server.getTrustStorePassword());

    path = TestUtils.randomUnicodeString(10);
    assertEquals(server, server.setTrustStorePath(path));
    assertEquals(path, server.getTrustStorePath());

    assertEquals(server, server.setClientAuthRequired(true));
    assertTrue(server.isClientAuthRequired());
    assertEquals(server, server.setClientAuthRequired(false));
    assertFalse(server.isClientAuthRequired());

    assertEquals(server, server.setAcceptBacklog(10));
    assertEquals(10, server.getAcceptBacklog());

    assertEquals(server, server.setReuseAddress(true));
    assertTrue(server.isReuseAddress());
    assertEquals(server, server.setReuseAddress(false));
    assertTrue(!server.isReuseAddress());

    assertEquals(server, server.setSoLinger(10));
    assertEquals(10, server.getSoLinger());

    assertEquals(server, server.setTCPKeepAlive(true));
    assertTrue(server.isTCPKeepAlive());
    assertEquals(server, server.setTCPKeepAlive(false));
    assertFalse(server.isTCPKeepAlive());

    assertEquals(server, server.setTCPNoDelay(true));
    assertTrue(server.isTCPNoDelay());
    assertEquals(server, server.setTCPNoDelay(false));
    assertFalse(server.isTCPNoDelay());

    assertEquals(server, server.setUsePooledBuffers(true));
    assertTrue(server.isUsePooledBuffers());
    assertEquals(server, server.setUsePooledBuffers(false));
    assertFalse(server.isUsePooledBuffers());

    int rbs = new Random().nextInt(1024 * 1024) + 1;
    assertEquals(server, server.setReceiveBufferSize(rbs));
    assertEquals(rbs, server.getReceiveBufferSize());

    try {
      server.setReceiveBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      server.setReceiveBufferSize(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int sbs = new Random().nextInt(1024 * 1024);
    assertEquals(server, server.setSendBufferSize(sbs));
    assertEquals(sbs, server.getSendBufferSize());

    try {
      server.setSendBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    try {
      server.setSendBufferSize(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      //OK
    }

    int trafficClass = new Random().nextInt(10000000);
    assertEquals(server, server.setTrafficClass(trafficClass));
    assertEquals(trafficClass, server.getTrafficClass());

    testComplete();
  }

  @Test
  public void testEchoBytes() {
    Buffer sent = randomBuffer(100);
    testEcho(sock -> sock.write(sent), buff -> buffersEqual(sent, buff) ,sent.length());
  }

  @Test
  public void testEchoString() {
    String sent = randomUnicodeString(100);
    Buffer buffSent = new Buffer(sent);
    testEcho(sock -> sock.write(sent), buff -> buffersEqual(buffSent, buff), buffSent.length());
  }

  @Test
  public void testEchoStringUTF8() {
    testEchoStringWithEncoding("UTF-8");
  }

  @Test
  public void testEchoStringUTF16() {
    testEchoStringWithEncoding("UTF-16");
  }

  void testEchoStringWithEncoding(String encoding) {
    String sent = randomUnicodeString(100);
    Buffer buffSent = new Buffer(sent, encoding);
    testEcho(sock -> sock.write(sent, encoding), buff -> buffersEqual(buffSent, buff), buffSent.length());
  }

  void testEcho(Consumer<NetSocket> writer, Consumer<Buffer> dataChecker, int length) {
    Handler<AsyncResult<NetSocket>> clientHandler = (asyncResult) -> {
      if (asyncResult.succeeded()) {
        NetSocket sock = asyncResult.result();
        Buffer buff = new Buffer();
        sock.dataHandler((buffer) -> {
          buff.appendBuffer(buffer);
          if (buff.length() == length) {
            dataChecker.accept(buff);
            testComplete();
          }
          if (buff.length() > length) {
            fail("Too many bytes received");
          }
        });
        writer.accept(sock);
      } else {
        fail("failed to connect");
      }
    };
    startEchoServer(s -> client.connect(1234, "localhost", clientHandler) );
    await();
  }

  void startEchoServer(Handler<AsyncResult<NetServer>> listenHandler ) {
    Handler<NetSocket> serverHandler = socket -> socket.dataHandler(socket::write);
    server.connectHandler(serverHandler).listen(1234, "localhost", listenHandler);
  }

  @Test
  public void testConnectDefaultHost() {
    connect(1234, null);
  }

  @Test
  public void testConnectLocalHost() {
    connect(1234, "localhost");
  }

  void connect(int port, String host) {
    startEchoServer(s -> {
      final int numConnections = 100;
      final AtomicInteger connCount = new AtomicInteger(0);
      for (int i = 0; i < numConnections; i++) {
        AsyncResultHandler<NetSocket> handler = res -> {
          if (res.succeeded()) {
            res.result().close();
            if (connCount.incrementAndGet() == numConnections) {
              testComplete();
            }
          }
        };
        if (host == null) {
          client.connect(port, handler);
        } else {
          client.connect(port, host, handler);
        }
      }
    });
    await();
  }

  @Test
  public void testConnectInvalidPort() {
    client.connect(9998, res -> {
      assertTrue(res.failed());
      assertFalse(res.succeeded());
      assertNotNull(res.cause());
      testComplete();
    });
    await();
  }

  @Test
  public void testConnectInvalidHost() {
    client.setConnectTimeout(1000);
    client.connect(1234, "127.0.0.2", res -> {
      assertTrue(res.failed());
      assertFalse(res.succeeded());
      assertNotNull(res.cause());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenInvalidPort() {
    server.connectHandler((netSocket) -> {}).listen(80, ar -> {
      assertTrue(ar.failed());
      assertFalse(ar.succeeded());
      assertNotNull(ar.cause());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenInvalidHost() {
    server.connectHandler(netSocket -> {}).listen(80, "uhqwduhqwudhqwuidhqwiudhqwudqwiuhd", ar -> {
      assertTrue(ar.failed());
      assertFalse(ar.succeeded());
      assertNotNull(ar.cause());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenOnWildcardPort() {
    server.connectHandler((netSocket) -> {}).listen(0, ar -> {
      assertFalse(ar.failed());
      assertTrue(ar.succeeded());
      assertNull(ar.cause());
      assertTrue(server.port() > 1024);
      assertEquals(server, ar.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testClientCloseHandlersCloseFromClient() {
    startEchoServer(s -> clientCloseHandlers(true));
    await();
  }

  @Test
  public void testClientCloseHandlersCloseFromServer() {
    server.connectHandler((netSocket) -> netSocket.close() ).listen(1234, (s) -> clientCloseHandlers(false));
    await();
  }

  void clientCloseHandlers(boolean closeFromClient) {
    client.connect(1234, ar -> {
      AtomicInteger counter = new AtomicInteger(0);
      ar.result().endHandler(v -> assertEquals(1, counter.incrementAndGet()));
      ar.result().closeHandler(v -> {
        assertEquals(2, counter.incrementAndGet());
        testComplete();
      });
      if (closeFromClient) {
        ar.result().close();
      }
    });
  }

  @Test
  public void testServerCloseHandlersCloseFromClient() {
    serverCloseHandlers(false, s -> client.connect(1234, ar -> ar.result().close()));
    await();
  }

  @Test
  public void testServerCloseHandlersCloseFromServer() {
    serverCloseHandlers(true, s -> client.connect(1234, ar -> {
    }));
    await();
  }

  void serverCloseHandlers(boolean closeFromServer, Handler<AsyncResult<NetServer>> listenHandler) {
    server.connectHandler((sock) -> {
      AtomicInteger counter = new AtomicInteger(0);
      sock.endHandler(v ->  assertEquals(1, counter.incrementAndGet()));
      sock.closeHandler(v -> {
          assertEquals(2, counter.incrementAndGet());
          testComplete();
      });
      if (closeFromServer) {
        sock.close();
      }
    }).listen(1234, listenHandler);
  }

  @Test
  public void testClientDrainHandler() {
    pausingServer((s) -> {
      client.connect(1234, ar -> {
        NetSocket sock = ar.result();
        assertFalse(sock.writeQueueFull());
        sock.setWriteQueueMaxSize(1000);
        Buffer buff = TestUtils.generateRandomBuffer(10000);
        vertx.setPeriodic(1, id -> {
          sock.write(buff.copy());
          if (sock.writeQueueFull()) {
            vertx.cancelTimer(id);
            sock.drainHandler(v -> {
              assertFalse(sock.writeQueueFull());
              testComplete();
            });
            // Tell the server to resume
            vertx.eventBus().send("server_resume", "");
          }
        });
      });
    });
    await();
  }

  void pausingServer(Handler<AsyncResult<NetServer>> listenHandler) {
    server.connectHandler(sock -> {
      sock.pause();
      Handler<Message<Buffer>> resumeHandler = (m) -> sock.resume();
      vertx.eventBus().registerHandler("server_resume", resumeHandler);
      sock.closeHandler(v -> vertx.eventBus().unregisterHandler("server_resume", resumeHandler));
    }).listen(1234, listenHandler);
  }

  @Test
  public void testServerDrainHandler() {
    drainingServer(s -> {
      client.connect(1234, ar -> {
        NetSocket sock = ar.result();
        sock.pause();
        setHandlers(sock);
        sock.dataHandler(buf -> {
        });
      });
    });
    await();
  }

  void setHandlers(NetSocket sock) {
    Handler<Message<Buffer>> resumeHandler = m -> sock.resume();
    vertx.eventBus().registerHandler("client_resume", resumeHandler);
    sock.closeHandler(v -> vertx.eventBus().unregisterHandler("client_resume", resumeHandler));
  }

  void drainingServer(Handler<AsyncResult<NetServer>> listenHandler) {
    server.connectHandler(sock -> {
      assertFalse(sock.writeQueueFull());
      sock.setWriteQueueMaxSize(1000);

      Buffer buff = TestUtils.generateRandomBuffer(10000);
      //Send data until the buffer is full
      vertx.setPeriodic(1, id -> {
        sock.write(buff.copy());
        if (sock.writeQueueFull()) {
          vertx.cancelTimer(id);
          sock.drainHandler(v -> {
            assertFalse(sock.writeQueueFull());
            // End test after a short delay to give the client some time to read the data
            vertx.setTimer(100, id2 -> testComplete());
          });

          // Tell the client to resume
          vertx.eventBus().send("client_resume", "");
        }
      });
    }).listen(1234, listenHandler);
  }

  @Test
  public void testReconnectAttemptsInfinite() {
    reconnectAttempts(-1);
  }

  @Test
  public void testReconnectAttemptsMany() {
    reconnectAttempts(100000);
  }

  void reconnectAttempts(int attempts) {
    client.setReconnectAttempts(attempts);
    client.setReconnectInterval(10);

    //The server delays starting for a a few seconds, but it should still connect
    client.connect(1234, (res) -> {
      assertTrue(res.succeeded());
      assertFalse(res.failed());
      testComplete();
    });

    // Start the server after a delay
    vertx.setTimer(2000, id -> startEchoServer(s -> {}));

    await();
  }

  @Test
  public void testReconnectAttemptsNotEnough() {
    client.setReconnectAttempts(100);
    client.setReconnectInterval(10);

    client.connect(1234, (res) -> {
      assertFalse(res.succeeded());
      assertTrue(res.failed());
      testComplete();
    });

    await();
  }

  @Test
  // StartTLS
  public void testStartTLSClientTrustAll() throws Exception {
    testTLS(false, false, true, false, false, true, true, true);
  }

  @Test
  // Client trusts all server certs
  public void testTLSClientTrustAll() throws Exception {
    testTLS(false, false, true, false, false, true, true, false);
  }

  @Test
  // Server specifies cert that the client trusts (not trust all)
  public void testTLSClientTrustServerCert() throws Exception {
    testTLS(false, true, true, false, false, false, true, false);
  }

  @Test
  // Server specifies cert that the client doesn't trust
  public void testTLSClientUntrustedServer() throws Exception {
    testTLS(false, false, true, false, false, false, false, false);
  }

  @Test
  //Client specifies cert even though it's not required
  public void testTLSClientCertNotRequired() throws Exception {
    testTLS(true, true, true, true, false, false, true, false);
  }

  @Test
  //Client specifies cert and it's not required
  public void testTLSClientCertRequired() throws Exception {
    testTLS(true, true, true, true, true, false, true, false);
  }

  @Test
  //Client doesn't specify cert but it's required
  public void testTLSClientCertRequiredNoClientCert() throws Exception {
    testTLS(false, true, true, true, true, false, false, false);
  }

  @Test
  //Client specifies cert but it's not trusted
  public void testTLSClientCertClientNotTrusted() throws Exception {
    testTLS(true, true, true, false, true, false, false, false);
  }

  String findFileOnClasspath(String fileName) {
    URL url = getClass().getClassLoader().getResource(fileName);
    if (url == null) {
      throw new IllegalArgumentException("Cannot find file " + fileName + " on classpath");
    }
    Path path = ClasspathPathResolver.urlToPath(url).toAbsolutePath();
    return path.toString();
  }

  void testTLS(boolean clientCert, boolean clientTrust,
                boolean serverCert, boolean serverTrust,
                boolean requireClientAuth, boolean clientTrustAll,
                boolean shouldPass, boolean startTLS) throws Exception {

    if (!startTLS) {
      server.setSSL(true);
    }
    if (serverTrust) {
      server.setTrustStorePath(findFileOnClasspath("tls/server-truststore.jks")).setTrustStorePassword("wibble");
    }
    if (serverCert) {
      server.setKeyStorePath(findFileOnClasspath("tls/server-keystore.jks")).setKeyStorePassword("wibble");
    }
    if (requireClientAuth) {
      server.setClientAuthRequired(true);
    }
    Handler<NetSocket> serverHandler = socket -> {
      AtomicBoolean upgradedServer = new AtomicBoolean();
      socket.dataHandler(buff -> {
        socket.write(buff); // echo the data
        if (startTLS && !upgradedServer.get()) {
          assertFalse(socket.isSsl());
          socket.ssl(v -> assertTrue(socket.isSsl()));
          upgradedServer.set(true);
        } else {
          assertTrue(socket.isSsl());
        }
      });
    };
    server.connectHandler(serverHandler).listen(4043, "localhost", ar -> {
      if (!startTLS) {
        client.setSSL(true);
        if (clientTrustAll) {
          client.setTrustAll(true);
        }
        if (clientTrust) {
          client.setTrustStorePath(findFileOnClasspath("tls/client-truststore.jks")).setTrustStorePassword("wibble");
        }
        if (clientCert) {
          client.setKeyStorePath(findFileOnClasspath("tls/client-keystore.jks")).setKeyStorePassword("wibble");
        }
      }
      client.connect(4043, ar2 -> {
        if (ar2.succeeded()) {
          if (!shouldPass) {
            fail("Should not connect");
            return;
          }
          final int numChunks = 100;
          final int chunkSize = 100;
          final Buffer received = new Buffer();
          final Buffer sent = new Buffer();
          final NetSocket socket = ar2.result();

          final AtomicBoolean upgradedClient = new AtomicBoolean();
          socket.dataHandler(buffer -> {
            received.appendBuffer(buffer);
            if (received.length() == sent.length()) {
              TestUtils.buffersEqual(sent, received);
              testComplete();
            }
            if (startTLS && !upgradedClient.get()) {
              assertFalse(socket.isSsl());
              socket.ssl(v -> {
                assertTrue(socket.isSsl());
                // Now send the rest
                for (int i = 1; i < numChunks; i++) {
                  sendBuffer(socket, sent, chunkSize);
                }
              });
            } else {
              assertTrue(socket.isSsl());
            }
          });

          //Now send some data
          int numToSend = startTLS ? 1 : numChunks;
          for (int i = 0; i < numToSend; i++) {
            sendBuffer(socket, sent, chunkSize);
          }
        } else {
          if (shouldPass) {
            fail("Should not fail to connect");
          } else {
            testComplete();
          }
        }
      });
    });
    await();
  }

  void sendBuffer(NetSocket socket, Buffer sent, int chunkSize) {
    Buffer buff = TestUtils.generateRandomBuffer(chunkSize);
    sent.appendBuffer(buff);
    socket.write(buff);
  }

  @Test
  public void testSharedServersRoundRobin() throws Exception {

    int numServers = 5;
    int numConnections = numServers * 100;

    List<NetServer> servers = new ArrayList<>();
    Set<NetServer> connectedServers = new ConcurrentHashSet<>();
    Map<NetServer, Integer> connectCount = new ConcurrentHashMap<>();

    CountDownLatch latchListen = new CountDownLatch(numServers);
    CountDownLatch latchConns = new CountDownLatch(numConnections);
    for (int i = 0; i < numServers; i++) {
      NetServer theServer = vertx.createNetServer();
      servers.add(theServer);
      theServer.connectHandler(sock -> {
        connectedServers.add(theServer);
        Integer cnt = connectCount.get(theServer);
        int icnt = cnt == null ? 0 : cnt;
        icnt++;
        connectCount.put(theServer, icnt);
        latchConns.countDown();
      }).listen(1234, "localhost", ar -> {
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
      client.connect(1234, "localhost", res -> {
        if (res.succeeded()) {
          res.result().closeHandler(v -> {
            latchClient.countDown();
          });
          res.result().close();
        } else {
          fail("Failed to connect");
        }
      });
    }

    assertTrue(latchClient.await(10, TimeUnit.SECONDS));
    assertTrue(latchConns.await(10, TimeUnit.SECONDS));

    assertEquals(numServers, connectedServers.size());
    for (NetServer server: servers) {
      assertTrue(connectedServers.contains(server));
    }
    assertEquals(numServers, connectCount.size());
    for (int cnt: connectCount.values()) {
      assertEquals(numConnections / numServers, cnt);
    }

    CountDownLatch closeLatch = new CountDownLatch(numServers);

    for (NetServer server: servers) {
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
    NetServer theServer = vertx.createNetServer();
    theServer.connectHandler(sock -> {
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
    NetServer theServer = vertx.createNetServer();
    theServer.connectHandler(sock -> {
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
    server.close(ar -> {
      assertTrue(ar.succeeded());
      closeLatch.countDown();
    });
    assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
    testSharedServersRoundRobin();
  }

  @Test
  // This tests using NetSocket.writeHandlerID (on the server side)
  // Send some data and make sure it is fanned out to all connections
  public void testFanout() throws Exception {

    CountDownLatch latch = new CountDownLatch(1);
    Set<String> connections = new ConcurrentHashSet<>();
    server.connectHandler(socket -> {
      connections.add(socket.writeHandlerID());
      socket.dataHandler(buffer -> {
        for (String actorID : connections) {
          vertx.eventBus().publish(actorID, buffer);
        }
      });
      socket.closeHandler(v -> {
        connections.remove(socket.writeHandlerID());
      });
    });
    server.listen(1234, ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    assertTrue(latch.await(10, TimeUnit.SECONDS));

    int numConnections = 10;
    CountDownLatch connectLatch = new CountDownLatch(numConnections);
    CountDownLatch receivedLatch = new CountDownLatch(numConnections);
    for (int i = 0; i < numConnections; i++) {
      client.connect(1234, res -> {
        connectLatch.countDown();
        res.result().dataHandler(data -> {
          receivedLatch.countDown();
        });
      });
    }
    assertTrue(connectLatch.await(10, TimeUnit.SECONDS));

    // Send some data
    client.connect(1234, res -> {
      res.result().write("foo");
    });
    assertTrue(receivedLatch.await(10, TimeUnit.SECONDS));

    testComplete();
  }

  @Test
  public void testRemoteAddress() throws Exception {
    server.connectHandler(socket -> {
      InetSocketAddress addr = socket.remoteAddress();
      assertTrue(addr.getHostName().startsWith("localhost"));
    }).listen(1234, ar -> {
      assertTrue(ar.succeeded());
      vertx.createNetClient().connect(1234, result -> {
        NetSocket socket = result.result();
        InetSocketAddress addr = socket.remoteAddress();
        assertEquals(addr.getHostName(), "localhost");
        assertEquals(addr.getPort(), 1234);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testWriteSameBufferMoreThanOnce() throws Exception {
    vertx.createNetServer().connectHandler(socket -> {
      final Buffer received = new Buffer();
      socket.dataHandler(buff -> {
        received.appendBuffer(buff);
        if (received.toString().equals("foofoo")) {
          testComplete();
        }
      });
    }).listen(1234, ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, result -> {
        NetSocket socket = result.result();
        Buffer buff = new Buffer("foo");
        socket.write(buff);
        socket.write(buff);
      });
    });
    await();
  }

  @Test
  public void testSendFileDirectory() throws Exception {
    final File fDir = Files.createTempDirectory("vertx-test").toFile();
    fDir.deleteOnExit();
    vertx.createNetServer().connectHandler(socket -> {
      InetSocketAddress addr = socket.remoteAddress();
      assertTrue(addr.getHostName().startsWith("localhost"));
    }).listen(1234, ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, result -> {
        assertTrue(result.succeeded());
        NetSocket socket = result.result();
        try {
          socket.sendFile(fDir.getAbsolutePath().toString());
          // should throw exception and never hit the assert
          fail("Should throw exception");
        } catch (IllegalArgumentException e) {
          testComplete();
        }
      });
    });
    await();
  }

}
