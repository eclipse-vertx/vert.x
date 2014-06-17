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
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.core.net.*;
import org.vertx.java.core.net.impl.SocketDefaults;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.vertx.java.tests.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetTest extends VertxTestBase {

  private NetServer server;
  private NetClient client;

  @Before
  public void before() throws Exception {
    client = vertx.createNetClient(new NetClientOptions().setConnectTimeout(1000));
    server = vertx.createNetServer(new NetServerOptions().setPort(1234).setHost("localhost"));
  }

  protected void awaitClose(NetServer server) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    server.close((asyncResult) -> {
      latch.countDown();
    });
    awaitLatch(latch);
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
  public void testClientOptions() {
    NetClientOptions options = new NetClientOptions();

    assertEquals(-1, options.getSendBufferSize());
    int rand = randomPositiveInt();
    assertEquals(options, options.setSendBufferSize(rand));
    assertEquals(rand, options.getSendBufferSize());
    try {
      options.setSendBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setSendBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertEquals(-1, options.getReceiveBufferSize());
    rand = randomPositiveInt();
    assertEquals(options, options.setReceiveBufferSize(rand));
    assertEquals(rand, options.getReceiveBufferSize());
    try {
      options.setReceiveBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setReceiveBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isReuseAddress());
    assertEquals(options, options.setReuseAddress(false));
    assertFalse(options.isReuseAddress());

    assertEquals(-1, options.getTrafficClass());
    rand = 23;
    assertEquals(options, options.setTrafficClass(rand));
    assertEquals(rand, options.getTrafficClass());
    try {
      options.setTrafficClass(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setTrafficClass(256);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isTcpNoDelay());
    assertEquals(options, options.setTcpNoDelay(false));
    assertFalse(options.isTcpNoDelay());

    boolean tcpKeepAlive = SocketDefaults.instance.isTcpKeepAlive();
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(options, options.setTcpKeepAlive(!tcpKeepAlive));
    assertEquals(!tcpKeepAlive, options.isTcpKeepAlive());

    int soLinger = SocketDefaults.instance.getSoLinger();
    assertEquals(soLinger, options.getSoLinger());
    rand = randomPositiveInt();
    assertEquals(options, options.setSoLinger(rand));
    assertEquals(rand, options.getSoLinger());
    try {
      options.setSoLinger(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertFalse(options.isUsePooledBuffers());
    assertEquals(options, options.setUsePooledBuffers(true));
    assertTrue(options.isUsePooledBuffers());

    assertFalse(options.isSsl());
    assertEquals(options, options.setSsl(true));
    assertTrue(options.isSsl());

    assertNull(options.getKeyStorePath());
    String randString = randomAlphaString(100);
    assertEquals(options, options.setKeyStorePath(randString));
    assertEquals(randString, options.getKeyStorePath());

    assertNull(options.getKeyStorePassword());
    randString = randomAlphaString(100);
    assertEquals(options, options.setKeyStorePassword(randString));
    assertEquals(randString, options.getKeyStorePassword());

    assertNull(options.getTrustStorePath());
    randString = randomAlphaString(100);
    assertEquals(options, options.setTrustStorePath(randString));
    assertEquals(randString, options.getTrustStorePath());

    assertNull(options.getTrustStorePassword());
    randString = randomAlphaString(100);
    assertEquals(options, options.setTrustStorePassword(randString));
    assertEquals(randString, options.getTrustStorePassword());

    assertFalse(options.isTrustAll());
    assertEquals(options, options.setTrustAll(true));
    assertTrue(options.isTrustAll());

    assertEquals(0, options.getReconnectAttempts());
    rand = randomPositiveInt();
    assertEquals(options, options.setReconnectAttempts(rand));
    assertEquals(rand, options.getReconnectAttempts());

    assertEquals(1000, options.getReconnectInterval());
    rand = randomPositiveInt();
    assertEquals(options, options.setReconnectInterval(rand));
    assertEquals(rand, options.getReconnectInterval());


    testComplete();
  }

  @Test
  public void testServerOptions() {
    NetServerOptions options = new NetServerOptions();

    assertEquals(-1, options.getSendBufferSize());
    int rand = randomPositiveInt();
    assertEquals(options, options.setSendBufferSize(rand));
    assertEquals(rand, options.getSendBufferSize());
    try {
      options.setSendBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setSendBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertEquals(-1, options.getReceiveBufferSize());
    rand = randomPositiveInt();
    assertEquals(options, options.setReceiveBufferSize(rand));
    assertEquals(rand, options.getReceiveBufferSize());
    try {
      options.setReceiveBufferSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setReceiveBufferSize(-123);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isReuseAddress());
    assertEquals(options, options.setReuseAddress(false));
    assertFalse(options.isReuseAddress());

    assertEquals(-1, options.getTrafficClass());
    rand = 23;
    assertEquals(options, options.setTrafficClass(rand));
    assertEquals(rand, options.getTrafficClass());
    try {
      options.setTrafficClass(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setTrafficClass(256);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertTrue(options.isTcpNoDelay());
    assertEquals(options, options.setTcpNoDelay(false));
    assertFalse(options.isTcpNoDelay());

    boolean tcpKeepAlive = SocketDefaults.instance.isTcpKeepAlive();
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(options, options.setTcpKeepAlive(!tcpKeepAlive));
    assertEquals(!tcpKeepAlive, options.isTcpKeepAlive());

    int soLinger = SocketDefaults.instance.getSoLinger();
    assertEquals(soLinger, options.getSoLinger());
    rand = randomPositiveInt();
    assertEquals(options, options.setSoLinger(rand));
    assertEquals(rand, options.getSoLinger());
    try {
      options.setSoLinger(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }

    assertFalse(options.isUsePooledBuffers());
    assertEquals(options, options.setUsePooledBuffers(true));
    assertTrue(options.isUsePooledBuffers());

    assertFalse(options.isSsl());
    assertEquals(options, options.setSsl(true));
    assertTrue(options.isSsl());

    assertNull(options.getKeyStorePath());
    String randString = randomAlphaString(100);
    assertEquals(options, options.setKeyStorePath(randString));
    assertEquals(randString, options.getKeyStorePath());

    assertNull(options.getKeyStorePassword());
    randString = randomAlphaString(100);
    assertEquals(options, options.setKeyStorePassword(randString));
    assertEquals(randString, options.getKeyStorePassword());

    assertNull(options.getTrustStorePath());
    randString = randomAlphaString(100);
    assertEquals(options, options.setTrustStorePath(randString));
    assertEquals(randString, options.getTrustStorePath());

    assertNull(options.getTrustStorePassword());
    randString = randomAlphaString(100);
    assertEquals(options, options.setTrustStorePassword(randString));
    assertEquals(randString, options.getTrustStorePassword());

    assertEquals(1024, options.getAcceptBacklog());
    rand = randomPositiveInt();
    assertEquals(options, options.setAcceptBacklog(rand));
    assertEquals(rand, options.getAcceptBacklog());

    assertEquals(0, options.getPort());
    rand = randomPositiveInt();
    assertEquals(options, options.setPort(rand));
    assertEquals(rand, options.getPort());

    assertEquals("0.0.0.0", options.getHost());
    randString = randomUnicodeString(100);
    assertEquals(options, options.setHost(randString));
    assertEquals(randString, options.getHost());
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
    server.connectHandler(serverHandler).listen(listenHandler);
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
    server.close();
    server = vertx.createNetServer(new NetServerOptions().setPort(80));
    server.connectHandler((netSocket) -> {}).listen(ar -> {
      assertTrue(ar.failed());
      assertFalse(ar.succeeded());
      assertNotNull(ar.cause());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenInvalidHost() {
    server.close();
    server = vertx.createNetServer(new NetServerOptions().setPort(1234).setHost("uhqwduhqwudhqwuidhqwiudhqwudqwiuhd"));
    server.connectHandler(netSocket -> {}).listen(ar -> {
      assertTrue(ar.failed());
      assertFalse(ar.succeeded());
      assertNotNull(ar.cause());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenOnWildcardPort() {
    server.close();
    server = vertx.createNetServer(new NetServerOptions().setPort(0));
    server.connectHandler((netSocket) -> {}).listen(ar -> {
      assertFalse(ar.failed());
      assertTrue(ar.succeeded());
      assertNull(ar.cause());
      assertTrue(server.actualPort() > 1024);
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
    server.connectHandler((netSocket) -> netSocket.close() ).listen((s) -> clientCloseHandlers(false));
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
    }).listen(listenHandler);
  }

  @Test
  public void testClientDrainHandler() {
    pausingServer((s) -> {
      client.connect(1234, ar -> {
        NetSocket sock = ar.result();
        assertFalse(sock.writeQueueFull());
        sock.setWriteQueueMaxSize(1000);
        Buffer buff = randomBuffer(10000);
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
      vertx.eventBus().registerHandler("server_resume", resumeHandler, ar -> {
        assertTrue(ar.succeeded());
        sock.closeHandler(v -> ar.result().unregister());
      });

    }).listen(listenHandler);
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
    vertx.eventBus().registerHandler("client_resume", resumeHandler, ar -> {
      assertTrue(ar.succeeded());
      sock.closeHandler(v -> ar.result().unregister());
    });
  }

  void drainingServer(Handler<AsyncResult<NetServer>> listenHandler) {
    server.connectHandler(sock -> {
      assertFalse(sock.writeQueueFull());
      sock.setWriteQueueMaxSize(1000);

      Buffer buff = randomBuffer(10000);
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
    }).listen( listenHandler);
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
    client.close();
    client = vertx.createNetClient(new NetClientOptions().setReconnectAttempts(attempts).setReconnectInterval(10));

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
    client.close();
    client = vertx.createNetClient(new NetClientOptions().setReconnectAttempts(100).setReconnectInterval(10));

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

  void testTLS(boolean clientCert, boolean clientTrust,
                boolean serverCert, boolean serverTrust,
                boolean requireClientAuth, boolean clientTrustAll,
                boolean shouldPass, boolean startTLS) throws Exception {
    server.close();
    NetServerOptions options = new NetServerOptions();
    if (!startTLS) {
      options.setSsl(true);
    }
    if (serverTrust) {
      options.setTrustStorePath(findFileOnClasspath("tls/server-truststore.jks")).setTrustStorePassword("wibble");
    }
    if (serverCert) {
      options.setKeyStorePath(findFileOnClasspath("tls/server-keystore.jks")).setKeyStorePassword("wibble");
    }
    if (requireClientAuth) {
      options.setClientAuthRequired(true);
    }
    options.setPort(4043);
    server = vertx.createNetServer(options);
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
    server.connectHandler(serverHandler).listen(ar -> {
      client.close();
      NetClientOptions clientOptions = new NetClientOptions();
      if (!startTLS) {
        clientOptions.setSsl(true);
        if (clientTrustAll) {
          clientOptions.setTrustAll(true);
        }
        if (clientTrust) {
          clientOptions.setTrustStorePath(findFileOnClasspath("tls/client-truststore.jks")).setTrustStorePassword("wibble");
        }
        if (clientCert) {
          clientOptions.setKeyStorePath(findFileOnClasspath("tls/client-keystore.jks")).setKeyStorePassword("wibble");
        }
      }
      client = vertx.createNetClient(clientOptions);
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
    Buffer buff = randomBuffer(chunkSize);
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
      NetServer theServer = vertx.createNetServer(new NetServerOptions().setHost("localhost").setPort(1234));
      servers.add(theServer);
      theServer.connectHandler(sock -> {
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
    client.close();
    client = vertx.createNetClient(new NetClientOptions());
    CountDownLatch latchClient = new CountDownLatch(numConnections);
    for (int i = 0; i < numConnections; i++) {
      client.connect(1234, "localhost", res -> {
        if (res.succeeded()) {
          res.result().closeHandler(v -> {
            latchClient.countDown();
          });
          res.result().close();
        } else {
          res.cause().printStackTrace();
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
    CountDownLatch latch = new CountDownLatch(1);
    // Have a server running on a different port to make sure it doesn't interact
    server.close();
    server = vertx.createNetServer(new NetServerOptions().setPort(4321));
    server.connectHandler(sock -> {
      fail("Should not connect");
    }).listen(ar2 -> {
      if (ar2.succeeded()) {
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
    server.close();
    CountDownLatch latch = new CountDownLatch(1);
    server = vertx.createNetServer(new NetServerOptions().setPort(1234));
    server.connectHandler(sock -> {
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
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      latch.countDown();
    });
    awaitLatch(latch);

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
      SocketAddress addr = socket.remoteAddress();
      assertEquals("127.0.0.1", addr.getHostAddress());
    }).listen(ar -> {
      assertTrue(ar.succeeded());
      vertx.createNetClient(new NetClientOptions()).connect(1234, result -> {
        NetSocket socket = result.result();
        SocketAddress addr = socket.remoteAddress();
        assertEquals("127.0.0.1", addr.getHostAddress());
        assertEquals(addr.getPort(), 1234);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testWriteSameBufferMoreThanOnce() throws Exception {
    server.connectHandler(socket -> {
      final Buffer received = new Buffer();
      socket.dataHandler(buff -> {
        received.appendBuffer(buff);
        if (received.toString().equals("foofoo")) {
          testComplete();
        }
      });
    }).listen(ar -> {
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
    server.connectHandler(socket -> {
      SocketAddress addr = socket.remoteAddress();
      assertEquals("127.0.0.1", addr.getHostAddress());
    }).listen(ar -> {
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

  @Test
  public void testServerOptionsCopiedBeforeUse() {
    server.close();
    NetServerOptions options = new NetServerOptions().setPort(1234);
    NetServer server = vertx.createNetServer(options);
    // Now change something - but server should still listen at previous port
    options.setPort(1235);
    server.connectHandler(sock -> {
      testComplete();
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, ar2 -> {
        assertTrue(ar2.succeeded());
      });
    });
    await();
  }

  @Test
  public void testClientOptionsCopiedBeforeUse() {
    client.close();
    NetClientOptions options = new NetClientOptions();
    client = vertx.createNetClient(options);
    options.setSsl(true);
    // Now change something - but server should ignore this
    server.connectHandler(sock -> {
      testComplete();
    });
    server.listen(ar -> {
      assertTrue(ar.succeeded());
      client.connect(1234, ar2 -> {
        assertTrue(ar2.succeeded());
      });
    });
    await();
  }

}
