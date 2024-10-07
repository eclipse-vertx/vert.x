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
package io.vertx.tests.vertx;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ServerChannel;
import io.vertx.core.Vertx;
import io.vertx.test.http.HttpTestBase;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.impl.transports.NioTransport;
import io.vertx.test.core.AsyncTestBase;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

public class GlobalEventExecutorNotificationTest extends AsyncTestBase {

  private Vertx vertx;

  @After
  public void after() throws Exception {
    if (vertx != null) {
      CountDownLatch latch = new CountDownLatch(1);
      vertx.close().onComplete(v -> latch.countDown());
      awaitLatch(latch);
    }
  }

  @Test
  public void testConnectError() {
    testConnectErrorNotifiesOnEventLoop(new NetClientOptions());
  }

  @Test
  public void testProxyConnectError() {
    testConnectErrorNotifiesOnEventLoop(new NetClientOptions()
      .setProxyOptions(new ProxyOptions()
        .setPort(1234)
        .setType(ProxyType.SOCKS5)
        .setHost("localhost")));
  }

  private void testConnectErrorNotifiesOnEventLoop(NetClientOptions options) {
    RuntimeException cause = new RuntimeException();
    vertx = VertxBootstrap.create().transport(new NioTransport() {
      @Override
      public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
        return (ChannelFactory<Channel>) () -> {
          throw cause;
        };
      }
    }).init().vertx();

    vertx.createNetServer().connectHandler(so -> {
      fail();
    }).listen(1234, "localhost").onComplete( onSuccess(v -> {
      vertx.createNetClient(options).connect(1234, "localhost").onComplete(onFailure(err -> {
        assertSame(err, cause);
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testNetBindError() {
    RuntimeException cause = new RuntimeException();
    vertx = VertxBootstrap.create().transport(new NioTransport() {
      @Override
      public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
        return (ChannelFactory<ServerChannel>) () -> {
          throw cause;
        };
      }
    }).init().vertx();

    vertx.createNetServer()
      .connectHandler(so -> fail())
      .listen(1234, "localhost").onComplete(onFailure(err -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testHttpBindError() {
    RuntimeException cause = new RuntimeException();
    vertx = VertxBootstrap.create().transport(new NioTransport() {
      @Override
      public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
        return (ChannelFactory<ServerChannel>) () -> {
          throw cause;
        };
      }
    }).init().vertx();

    vertx.createHttpServer()
      .requestHandler(req -> fail())
      .listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost").onComplete(onFailure(err -> {
      testComplete();
    }));
    await();
  }
}
