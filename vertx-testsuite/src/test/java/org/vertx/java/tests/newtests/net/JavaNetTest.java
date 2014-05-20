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

import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.tests.newtests.ATest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class JavaNetTest {

  private Vertx vertx;
  private ATest atest;
  private NetServer server;
  private NetClient client;

  @Before
  public void before() {
    vertx = VertxFactory.newVertx();
    atest = new ATest();
  }

  @After
  public void after() throws Exception {
    client.close();
    CountDownLatch latch = new CountDownLatch(1);
    server.close((asyncResult) -> {
      latch.countDown();
    });
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    vertx.stop();
  }

  @Test
  public void testEchoBytes() {
    Buffer sent = new Buffer("ijoqwijqwodijqwiojdqwd");
    server = vertx.createNetServer().connectHandler((socket) -> {
      socket.dataHandler(socket::write);
    }).listen(1234, "localhost", (s) -> {
      client = vertx.createNetClient().connect(1234, "localhost", (asyncResult) -> {
        if (asyncResult.succeeded()) {
          NetSocket sock = asyncResult.result();
          Buffer buff = new Buffer();
          sock.dataHandler((buffer) -> {
            buff.appendBuffer(buffer);
            if (buff.length() == sent.length()) {
              atest.testComplete();
            }
            if (buff.length() > sent.length()) {
              atest.assertBlock(() -> {
                fail("Too many bytes received");
              });
            }
          });
          sock.write(sent);
        } else {
          atest.assertBlock(() -> {
            fail("failed to connect");
          });
        }
      });
    });
    atest.await();
  }
}
