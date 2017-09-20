/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.test.it;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TransportTest extends AsyncTestBase {

  private Vertx vertx;

  @Override
  protected void tearDown() throws Exception {
    close(vertx);
    super.tearDown();
  }

  @Test
  public void testNoNative() {
    ClassLoader classLoader = Vertx.class.getClassLoader();
    try {
      classLoader.loadClass("io.netty.channel.epoll.Epoll");
      fail("Was not expected to load Epoll class");
    } catch (ClassNotFoundException ignore) {
      // Expected
    }
    try {
      classLoader.loadClass("io.netty.channel.kqueue.KQueue");
      fail("Was not expected to load KQueue class");
    } catch (ClassNotFoundException ignore) {
      // Expected
    }
    testNetServer(new VertxOptions());
    assertFalse(vertx.isNativeTransportEnabled());
  }

  @Test
  public void testFallbackOnJDK() {
    testNetServer(new VertxOptions().setPreferNativeTransport(true));
    assertFalse(vertx.isNativeTransportEnabled());
  }

  private void testNetServer(VertxOptions options) {
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
    server.listen(1234, onSuccess(v -> {
      NetClient client = vertx.createNetClient();
      client.connect(1234, "localhost", onSuccess(so -> {
        so.write("ping");
        so.handler(buff -> {
          assertEquals("pong", buff.toString());
          so.close();
        });
      }));
    }));
    await();
  }
}
