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

package org.vertx.java.tests.newtests.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.impl.SocketDefaults;
import org.vertx.java.testframework.TestUtils;
import org.vertx.java.tests.newtests.JUnitAsyncHelper;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.vertx.java.tests.newtests.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaNetTest {

  private Vertx vertx;
  private JUnitAsyncHelper helper;
  private NetServer server;
  private NetClient client;

  @Before
  public void before() {
    vertx = VertxFactory.newVertx();
    helper = new JUnitAsyncHelper();
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
    vertx.stop();
    helper.close();
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
    helper.testComplete();
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

    helper.testComplete();
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
    helper.testComplete();
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

    helper.testComplete();
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
            helper.testComplete();
          }
          if (buff.length() > length) {
            helper.doAssert(() -> {
              fail("Too many bytes received");
            });
          }
        });
        writer.accept(sock);
      } else {
        helper.doAssert(() -> {
          fail("failed to connect");
        });
      }
    };
    startEchoServer(s -> client.connect(1234, "localhost", clientHandler) );
    helper.await();
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
        AsyncResultHandler<NetSocket> handler =  res -> {
          if (res.succeeded()) {
            res.result().close();
            if (connCount.incrementAndGet() == numConnections) {
              helper.testComplete();
            }
          }
        };
        if (host == null) {
          client.connect(port, handler);
        } else  {
          client.connect(port, host, handler);
        }
      }
    });
    helper.await();
  }

  @Test
  public void testConnectInvalidPort() {
    client.connect(9998, res -> {
      helper.doAssert(() -> {
        assertTrue(res.failed());
        assertFalse(res.succeeded());
        assertNotNull(res.cause());
        helper.testComplete();
      });
    });
    helper.await();
  }

  @Test
  public void testConnectInvalidHost() {
    client.setConnectTimeout(1000);
    client.connect(1234, "127.0.0.2", res -> {
      helper.doAssert( () -> {
        assertTrue(res.failed());
        assertFalse(res.succeeded());
        assertNotNull(res.cause());
        helper.testComplete();
      });
    });
    helper.await();
  }

  @Test
  public void testListenInvalidPort() {
    server.connectHandler((netSocket) -> {}).listen(80, ar -> {
      helper.doAssert(() -> {
        assertTrue(ar.failed());
        assertFalse(ar.succeeded());
        assertNotNull(ar.cause());
        helper.testComplete();
      });
    });
    helper.await();
  }

  @Test
  public void testListenInvalidHost() {
    server.connectHandler(netSocket -> {}).listen(80, "uhqwduhqwudhqwuidhqwiudhqwudqwiuhd", ar -> {
      helper.doAssert(() -> {
        assertTrue(ar.failed());
        assertFalse(ar.succeeded());
        assertNotNull(ar.cause());
        helper.testComplete();
      });
    });
    helper.await();
  }

  @Test
  public void testListenOnWildcardPort() {
    server.connectHandler((netSocket) -> {}).listen(0, ar -> {
      helper.doAssert(() -> {
        assertFalse(ar.failed());
        assertTrue(ar.succeeded());
        assertNull(ar.cause());
        assertTrue(server.port() > 1024);
        assertEquals(server, ar.result());
        helper.testComplete();
      });
    });
    helper.await();
  }

  @Test
  public void testClientCloseHandlersCloseFromClient() {
    startEchoServer(s -> clientCloseHandlers(true));
    helper.await();
  }

  @Test
  public void testClientCloseHandlersCloseFromServer() {
    server.connectHandler((netSocket) -> netSocket.close() ).listen(1234, (s) -> clientCloseHandlers(false));
    helper.await();
  }

  void clientCloseHandlers(boolean closeFromClient) {
    client.connect(1234, ar -> {
      AtomicInteger counter = new AtomicInteger(0);
      ar.result().endHandler(v -> helper.doAssert(() -> assertEquals(1, counter.incrementAndGet())));
      ar.result().closeHandler(v -> {
        helper.doAssert(() -> {
          assertEquals(2, counter.incrementAndGet());
          helper.testComplete();
        });
      });
      if (closeFromClient) {
        ar.result().close();
      }
    });
  }

  @Test
  public void testServerCloseHandlersCloseFromClient() {
    serverCloseHandlers(false, s -> client.connect(1234, ar -> ar.result().close()));
    helper.await();
  }

  @Test
  public void testServerCloseHandlersCloseFromServer() {
    serverCloseHandlers(true, s -> client.connect(1234, ar -> {}));
    helper.await();
  }

  void serverCloseHandlers(boolean closeFromServer, Handler<AsyncResult<NetServer>> listenHandler) {
    server.connectHandler((sock) -> {
      AtomicInteger counter = new AtomicInteger(0);
      sock.endHandler(v ->  helper.doAssert(() ->  assertEquals(1, counter.incrementAndGet())));
      sock.closeHandler(v -> {
        helper.doAssert(() -> {
          assertEquals(2, counter.incrementAndGet());
          helper.testComplete();
        });
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
        helper.doAssert(() -> assertFalse(sock.writeQueueFull()));
        sock.setWriteQueueMaxSize(1000);
        Buffer buff = TestUtils.generateRandomBuffer(10000);
        vertx.setPeriodic(1, id -> {
          sock.write(buff.copy());
          if (sock.writeQueueFull()) {
            vertx.cancelTimer(id);
            sock.drainHandler(v -> {
              helper.doAssert(() -> assertFalse(sock.writeQueueFull()));
              helper.testComplete();
            });
            // Tell the server to resume
            vertx.eventBus().send("server_resume", "");
          }
        });
      });
    });
    helper.await();
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
        sock.dataHandler(buf -> {});
      });
    });
    helper.await();
  }

  void setHandlers(NetSocket sock) {
    Handler<Message<Buffer>> resumeHandler = m -> sock.resume();
    vertx.eventBus().registerHandler("client_resume", resumeHandler);
    sock.closeHandler(v -> vertx.eventBus().unregisterHandler("client_resume", resumeHandler));
  }

  void drainingServer(Handler<AsyncResult<NetServer>> listenHandler) {
    server.connectHandler(sock -> {
      helper.doAssert(() -> assertFalse(sock.writeQueueFull()));
      sock.setWriteQueueMaxSize(1000);

      Buffer buff = TestUtils.generateRandomBuffer(10000);
      //Send data until the buffer is full
      vertx.setPeriodic(1, id -> {
        sock.write(buff.copy());
        if (sock.writeQueueFull()) {
          vertx.cancelTimer(id);
          sock.drainHandler(v -> {
            helper.doAssert(() -> assertFalse(sock.writeQueueFull()));
            // End test after a short delay to give the client some time to read the data
            vertx.setTimer(100, id2 -> helper.testComplete());
          });

          // Tell the client to resume
          vertx.eventBus().send("client_resume", "");
        }
      });
    }).listen(1234, listenHandler);
  }

}
