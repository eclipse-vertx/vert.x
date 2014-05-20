/*
 *
 *  * Copyright 2014 Red Hat, Inc.
 *  *
 *  * All rights reserved. This program and the accompanying materials
 *  * are made available under the terms of the Eclipse Public License v1.0
 *  * and Apache License v2.0 which accompanies this distribution.
 *  *
 *  *     The Eclipse Public License is available at
 *  *     http://www.eclipse.org/legal/epl-v10.html
 *  *
 *  *     The Apache License v2.0 is available at
 *  *     http://www.opensource.org/licenses/apache2.0.php
 *  *
 *  * You may elect to redistribute this code under either of these licenses.
 *  *
 *
 */

package org.vertx.java.tests.newtests.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.testframework.TestUtils;
import org.vertx.java.tests.newtests.JUnitAsyncHelper;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.vertx.java.tests.newtests.TestUtils.*;

public class JavaNetTest {

  private Vertx vertx;
  private JUnitAsyncHelper helper;
  private NetServer server;
  private NetClient client;

  @Before
  public void before() {
    vertx = VertxFactory.newVertx();
    helper = new JUnitAsyncHelper();
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
  }

  @Test
  public void testClientDefaults() {

    client = vertx.createNetClient();
    assertFalse(client.isSSL());
    assertNull(client.getKeyStorePassword());
    assertNull(client.getKeyStorePath());
    assertNull(client.getTrustStorePassword());
    assertNull(client.getTrustStorePath());
    assertEquals(0, client.getReconnectAttempts());
    assertEquals(1000, client.getReconnectInterval());
    assertEquals(60000, client.getConnectTimeout());
    assertEquals(-1, client.getReceiveBufferSize());
    assertEquals(-1, client.getSendBufferSize());
    // TODO rest of defaults
  }

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
    assertEquals(client, client.setTrafficClass(trafficClass) == client);
    assertEquals(trafficClass, client.getTrafficClass());

  }

  @Test
  public void testEchoBytes() {
    Buffer sent = randomBuffer(100);
    testEcho((sock) -> sock.write(sent), (buff) -> buffersEqual(sent, buff) ,sent.length());
  }

  @Test
  public void testEchoString() {
    String sent = randomUnicodeString(100);
    Buffer buffSent = new Buffer(sent);
    testEcho((sock) -> sock.write(sent), (buff) -> buffersEqual(buffSent, buff), buffSent.length());
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
    testEcho((sock) -> sock.write(sent, encoding), (buff) -> buffersEqual(buffSent, buff), buffSent.length());
  }

  void testEcho(Consumer<NetSocket> writer, Consumer<Buffer> dataChecker, int length) {
    Handler<NetSocket> serverHandler = (socket) -> socket.dataHandler(socket::write);
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
    server = vertx.createNetServer().connectHandler(serverHandler).listen(1234, "localhost", (s) -> {
      client = vertx.createNetClient().connect(1234, "localhost", clientHandler);
    });
    helper.await();
  }

}
