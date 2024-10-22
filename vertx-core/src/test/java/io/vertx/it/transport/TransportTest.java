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

package io.vertx.it.transport;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.io.File;

import static org.junit.Assume.assumeTrue;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TransportTest extends AsyncTestBase {

  private Vertx vertx;

  @Override
  protected void tearDown() throws Exception {
    if (vertx != null) {
      close(vertx);
    }
    super.tearDown();
  }

  @Test
  public void testNoNative() throws Exception {
    ClassLoader classLoader = Vertx.class.getClassLoader();
    try {
      Class<?> clazz = classLoader.loadClass("io.netty.channel.epoll.Epoll");
      fail("Was not expected to load Epoll class from " + clazz.getProtectionDomain().getCodeSource().getLocation());
    } catch (ClassNotFoundException ignore) {
      // Expected
    }
    try {
      Class<?> clazz = classLoader.loadClass("io.netty.channel.kqueue.KQueue");
      fail("Was not expected to load KQueue class from " + clazz.getProtectionDomain().getCodeSource().getLocation());
    } catch (ClassNotFoundException ignore) {
      // Expected
    }
    testNetServer(new VertxOptions());
    assertFalse(vertx.isNativeTransportEnabled());
  }

  @Test
  public void testFallbackOnJDK() throws Exception {
    testNetServer(new VertxOptions().setPreferNativeTransport(true));
    assertFalse(vertx.isNativeTransportEnabled());
  }

  private void testNetServer(VertxOptions options) throws InterruptedException {
    vertx = Vertx.vertx(options);
    NetServer server = vertx.createNetServer();
    server.connectHandler(so -> {
      so.handler(buff -> {
        assertEquals("ping", buff.toString());
        so.write("pong");
      });
      so.closeHandler(v -> {
        testComplete();
      });
    });
    awaitFuture(server.listen(1234));
    NetClient client = vertx.createNetClient();
    client.connect(1234, "localhost").onComplete(onSuccess(so -> {
      so.write("ping");
      so.handler(buff -> {
        assertEquals("pong", buff.toString());
        so.close();
      });
    }));
    await();
  }

  @Test
  public void testDomainSocketServer() throws Exception {
    assumeTrue(PlatformDependent.javaVersion() < 16);
    File sock = TestUtils.tmpFile(".sock");
    vertx = Vertx.vertx();
    NetServer server = vertx.createNetServer();
    server.connectHandler(so -> {});
    server
      .listen(SocketAddress.domainSocketAddress(sock.getAbsolutePath()))
      .onComplete(onFailure(err -> {
        assertEquals(IllegalArgumentException.class, err.getClass());
        testComplete();
      }));
    await();
  }

  @Test
  public void testDomainSocketClient() throws Exception {
    assumeTrue(PlatformDependent.javaVersion() < 16);
    File sock = TestUtils.tmpFile(".sock");
    vertx = Vertx.vertx();
    NetClient client = vertx.createNetClient();
    client.connect(SocketAddress.domainSocketAddress(sock.getAbsolutePath()))
      .onComplete(onFailure(err -> {
        assertEquals(IllegalArgumentException.class, err.getClass());
        testComplete();
      }));
    await();
  }
}
