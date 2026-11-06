/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.vertx.core.Vertx;
import io.vertx.core.impl.transports.NioTransport;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.net.*;
import io.vertx.core.transport.Transport;
import io.vertx.test.core.ProvidedBy;
import io.vertx.test.core.VertxProvider;
import io.vertx.test.core.VertxRunner;
import jdk.net.ExtendedSocketOptions;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Tests the TCP keep-alive probe options ({@code TCP_KEEPIDLE}, {@code TCP_KEEPINTVL} and {@code TCP_KEEPCNT})
 * for the NIO transport.
 */
@RunWith(VertxRunner.class)
public class TcpKeepAliveProbeOptionsTest {

  /**
   * The probe options tested here are specific to the NIO transport, so the Vert.x instance must use it
   * regardless of the transport configured for the test run.
   */
  public static class NioVertxProvider implements VertxProvider {
    @Override
    public Vertx call() {
      return Vertx.builder().withTransport(Transport.NIO).build();
    }
  }

  // Netty channel options compare by identity, and NioChannelOption.of() returns a fresh instance every call,
  // so the bootstrap option maps are keyed by option name here instead.
  private static final String TCP_KEEPIDLE = "TCP_KEEPIDLE";
  private static final String TCP_KEEPINTVL = "TCP_KEEPINTERVAL";
  private static final String TCP_KEEPCNT = "TCP_KEEPCOUNT";

  private static final Set<SocketOption<?>> SUPPORTED = supportedOptions();

  private static Set<SocketOption<?>> supportedOptions() {
    try (SocketChannel channel = SocketChannel.open()) {
      return channel.supportedOptions();
    } catch (IOException | RuntimeException e) {
      return Collections.emptySet();
    }
  }

  private static TcpConfig config(int idle, int intvl, int cnt) {
    return new TcpConfig()
      .setOption(TcpOption.KEEPIDLE, idle)
      .setOption(TcpOption.KEEPINTVL, intvl)
      .setOption(TcpOption.KEEPCNT, cnt);
  }

  private static Map<String, Object> clientOptions(TcpConfig config, boolean domainSocket) {
    Bootstrap bootstrap = new Bootstrap();
    NioTransport.INSTANCE.configure(config, domainSocket, bootstrap);
    return byName(bootstrap.config().options());
  }

  private static Map<String, Object> serverChildOptions(TcpConfig config, boolean domainSocket) {
    ServerBootstrap bootstrap = new ServerBootstrap();
    NioTransport.INSTANCE.configure(config, domainSocket, bootstrap);
    return byName(bootstrap.config().childOptions());
  }

  private static Map<String, Object> byName(Map<ChannelOption<?>, Object> options) {
    Map<String, Object> byName = new HashMap<>();
    options.forEach((option, value) -> byName.put(option.name(), value));
    return byName;
  }

  @Test
  public void testProbeOptionsAppliedToClientBootstrap() {
    assumeTrue(SUPPORTED.containsAll(Set.of(
      ExtendedSocketOptions.TCP_KEEPIDLE, ExtendedSocketOptions.TCP_KEEPINTERVAL, ExtendedSocketOptions.TCP_KEEPCOUNT)));
    Map<String, Object> options = clientOptions(config(30, 10, 5), false);
    assertEquals(30, options.get(TCP_KEEPIDLE));
    assertEquals(10, options.get(TCP_KEEPINTVL));
    assertEquals(5, options.get(TCP_KEEPCNT));
  }

  @Test
  public void testProbeOptionsAppliedToServerBootstrap() {
    assumeTrue(SUPPORTED.containsAll(Set.of(
      ExtendedSocketOptions.TCP_KEEPIDLE, ExtendedSocketOptions.TCP_KEEPINTERVAL, ExtendedSocketOptions.TCP_KEEPCOUNT)));
    Map<String, Object> options = serverChildOptions(config(30, 10, 5), false);
    assertEquals(30, options.get(TCP_KEEPIDLE));
    assertEquals(10, options.get(TCP_KEEPINTVL));
    assertEquals(5, options.get(TCP_KEEPCNT));
  }

  /**
   * {@code 0} is the sentinel meaning "leave the OS default alone". The options setters store it as an actual
   * {@code 0}, so the transport must not forward it to the channel: on Linux a {@code 0} is rejected by the kernel.
   */
  @Test
  public void testZeroedProbeOptionsAreNotApplied() {
    TcpConfig zeroed = config(0, 0, 0);
    assertNoProbeOptions(clientOptions(zeroed, false));
    assertNoProbeOptions(serverChildOptions(zeroed, false));
  }

  @Test
  public void testUnsetProbeOptionsAreNotApplied() {
    TcpConfig unset = new TcpConfig();
    assertNoProbeOptions(clientOptions(unset, false));
    assertNoProbeOptions(serverChildOptions(unset, false));
  }

  @Test
  public void testProbeOptionsNotAppliedToDomainSockets() {
    TcpConfig config = config(30, 10, 5);
    assertNoProbeOptions(clientOptions(config, true));
    assertNoProbeOptions(serverChildOptions(config, true));
  }

  private void assertNoProbeOptions(Map<String, Object> options) {
    assertFalse(options.containsKey(TCP_KEEPIDLE));
    assertFalse(options.containsKey(TCP_KEEPINTVL));
    assertFalse(options.containsKey(TCP_KEEPCNT));
  }

  @Test
  public void testProbeOptionsSetOnConnectedSockets(@ProvidedBy(NioVertxProvider.class) Vertx vertx) throws Exception {
    assumeTrue("TCP keep-alive probe options unsupported on this platform", SUPPORTED.containsAll(Set.of(
      ExtendedSocketOptions.TCP_KEEPIDLE, ExtendedSocketOptions.TCP_KEEPINTERVAL)));

    int idle = 97, intvl = 13;

    NetServerOptions serverOptions = new NetServerOptions();
    serverOptions.setTcpKeepAlive(true);
    serverOptions.setTcpKeepAliveIdleSeconds(idle);
    serverOptions.setTcpKeepAliveIntervalSeconds(intvl);

    NetClientOptions clientOptions = new NetClientOptions();
    clientOptions.setTcpKeepAlive(true);
    clientOptions.setTcpKeepAliveIdleSeconds(idle);
    clientOptions.setTcpKeepAliveIntervalSeconds(intvl);

    CompletableFuture<NetSocket> accepted = new CompletableFuture<>();
    NetServer server = vertx
      .createNetServer(serverOptions)
      .connectHandler(accepted::complete)
      .listen(0, "localhost")
      .await();

    NetSocket client = vertx
      .createNetClient(clientOptions)
      .connect(server.actualPort(), "localhost")
      .await();

    assertProbeOptions(client, idle, intvl);
    assertProbeOptions(accepted.get(10, TimeUnit.SECONDS), idle, intvl);
  }

  private void assertProbeOptions(NetSocket so, int idle, int intvl) {
    Channel channel = ((NetSocketInternal) so).channelHandlerContext().channel();
    assertTrue("Expected a NIO channel but got " + channel.getClass(), channel instanceof NioSocketChannel);
    assertEquals(Integer.valueOf(idle),
      channel.config().getOption(NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPIDLE)));
    assertEquals(Integer.valueOf(intvl),
      channel.config().getOption(NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPINTERVAL)));
  }
}
