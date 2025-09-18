/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.quic.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.AbstractNioChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicCodecBuilder;
import io.netty.handler.codec.quic.QuicCodecDispatcher;
import io.netty.handler.codec.quic.QuicConnectionIdGenerator;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.util.Mapping;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.quic.QuicConnection;
import io.vertx.core.quic.QuicServer;
import io.vertx.core.quic.QuicServerOptions;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Shareable;
import io.vertx.core.spi.metrics.QuicEndpointMetrics;

import java.net.StandardSocketOptions;
import java.nio.channels.DatagramChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicServerImpl extends QuicEndpointImpl implements QuicServer {

  public static final String QUIC_SERVER_MAP_KEY = "__vertx.shared.quicServers";

  public static QuicServerImpl create(VertxInternal vertx, QuicServerOptions options) {
    return new QuicServerImpl(vertx, new QuicServerOptions(options));
  }

  private final QuicServerOptions options;
  private Handler<QuicConnection> handler;

  public QuicServerImpl(VertxInternal vertx, QuicServerOptions options) {
    super(vertx, options);
    this.options = options;
  }

  @Override
  public QuicServer handler(Handler<QuicConnection> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  protected ChannelHandler channelHandler(ContextInternal context, SocketAddress bindAddr, SslContextProvider sslContextProvider, QuicEndpointMetrics<?, ?> metrics) {
    if (options.isLoadBalanced()) {
      ServerID serverID = new ServerID(bindAddr.port(), bindAddr.host());
      LocalMap<String, QuicDispatcher> map = vertx.sharedData().getLocalMap(QUIC_SERVER_MAP_KEY);
      QuicDispatcher dispatcher;
      synchronized (map) {
        QuicDispatcher attempt = map.get(serverID.toString());
        if (attempt == null) {
          attempt = new QuicDispatcher(serverID, sslContextProvider);
          map.put(serverID.toString(), attempt);
        }
        dispatcher = attempt;
      }
      return new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel ch) {
          dispatcher.register(ch, context, QuicServerImpl.this, metrics);
          ch.pipeline().addLast(dispatcher);
        }
      };
    } else {
      return super.channelHandler(context, bindAddr, sslContextProvider, metrics);
    }
  }

  @Override
  protected QuicCodecBuilder<?> codecBuilder(ContextInternal context, SslContextProvider sslContextProvider, QuicEndpointMetrics<?, ?> metrics) {
    Mapping<? super String, ? extends SslContext> mapping = sslContextProvider.serverNameMapping(true);
    QuicSslContext sslContext = QuicSslContextBuilder.buildForServerWithSni(name -> (QuicSslContext) mapping.map(name));
    return new QuicServerCodecBuilder().sslContext(sslContext)
      .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
      // Setup a token handler. In a production system you would want to implement and provide your custom
      // one.
      .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
      // ChannelHandler that is added into QuicChannel pipeline.
      .handler(new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel ch) {
          QuicChannel channel = (QuicChannel) ch;
          QuicConnectionHandler handler = new QuicConnectionHandler(context, metrics, QuicServerImpl.this.handler);
          ChannelPipeline pipeline = channel.pipeline();
          pipeline.addLast(handler);
        }

/*

        public void channelInactive(ChannelHandlerContext ctx) {
          // OK
          ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
//            if (f.isSuccess()) {
//              System.out.println("Connection closed: " + f.getNow());
//            }
            //only test for first connection
//            ctx.channel().parent().close();
          });
        }
*/
      });
  }

  @Override
  protected Future<SslContextProvider> createSslContextProvider(SslContextManager manager, ContextInternal context) {
    return manager.resolveSslContextProvider(options.getSslOptions(), context);
  }

  private class QuicDispatcher extends QuicCodecDispatcher implements Shareable {

    private final ServerID serverID;
    private final SslContextProvider sslContextProvider;
    private Map<Channel, ServerRegistration> registrations;

    public QuicDispatcher(ServerID serverID, SslContextProvider sslContextProvider) {
      this.serverID = serverID;
      this.sslContextProvider = sslContextProvider;
      this.registrations = new ConcurrentHashMap<>();
    }

    private class ServerRegistration {

      final ContextInternal context;
      final QuicServerImpl server;
      final QuicEndpointMetrics<?, ?> metrics;

      ServerRegistration(ContextInternal context, QuicServerImpl server, QuicEndpointMetrics<?, ?> metrics) {
        this.context = context;
        this.server = server;
        this.metrics = metrics;
      }
    }

    void register(Channel ch, ContextInternal context, QuicServerImpl server, QuicEndpointMetrics<?, ?> metrics) {
      registrations.put(ch, new ServerRegistration(context, server, metrics));
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
      Channel ch = ctx.channel();
      if (ch instanceof NioDatagramChannel) {
        // Hack since Netty only supports REUSEPORT for Unix transports
        AbstractNioChannel.NioUnsafe unsafe = (AbstractNioChannel.NioUnsafe) ch.unsafe();
        DatagramChannel ch1 = (DatagramChannel) unsafe.ch();
        ch1.setOption(StandardSocketOptions.SO_REUSEPORT, true);
      } else {
        ch.setOption(UnixChannelOption.SO_REUSEPORT, true);
      }
      super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
      registrations.remove(ctx.channel());
      if (registrations.isEmpty()) {
        LocalMap<String, QuicDispatcher> map = vertx.sharedData().getLocalMap(QUIC_SERVER_MAP_KEY);
        map.remove(serverID.toString());
      }
      super.channelUnregistered(ctx);
    }

    @Override
    protected void initChannel(Channel channel, int localConnectionIdLength, QuicConnectionIdGenerator idGenerator) throws Exception {
      for (Map.Entry<Channel, ServerRegistration> entry : registrations.entrySet()) {
        if (entry.getKey() == channel) {
          ServerRegistration registration = entry.getValue();
          QuicCodecBuilder<?> builder = registration.server.initQuicCodecBuilder(registration.context, sslContextProvider, registration.metrics);
          channel.pipeline().addLast(builder.build());
          return;
        }
      }
      channel.close();
    }
  }
}
