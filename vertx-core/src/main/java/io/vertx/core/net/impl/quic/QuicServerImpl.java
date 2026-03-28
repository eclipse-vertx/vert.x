

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
package io.vertx.core.net.impl.quic;

import io.netty.channel.*;
import io.netty.channel.Channel;
import io.netty.channel.nio.AbstractNioChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QLogConfiguration;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicChannelOption;
import io.netty.handler.codec.quic.QuicCodecBuilder;
import io.netty.handler.codec.quic.QuicCodecDispatcher;
import io.netty.handler.codec.quic.QuicConnectionIdGenerator;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicTokenHandler;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.util.Mapping;
import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.quic.QuicServerInternal;
import io.vertx.core.internal.tls.*;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.SslContextProviderReference;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Shareable;
import io.vertx.core.spi.metrics.TransportMetrics;

import javax.net.ssl.X509ExtendedKeyManager;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.channels.DatagramChannel;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicServerImpl extends QuicEndpointImpl implements QuicServerInternal {

  public static final String QUIC_SERVER_MAP_KEY = "__vertx.shared.quicServers";

  public static QuicServer create(VertxInternal vertx,
                                  QuicServerConfig config,
                                  ServerSSLOptions sslOptions) {
    return new CleanableQuicServer(vertx, new QuicServerConfig(config), sslOptions);
  }

  private final QuicServerConfig config;
  private final ServerSSLOptions sslOptions;
  private SslContextProviderReference sslContextProviderRef;
  private Handler<QuicConnection> handler;
  private Handler<Throwable> exceptionHandler;
  private QuicTokenHandler tokenHandler;
  private final MappingRef<String, QuicSslContext> mapping;

  public QuicServerImpl(VertxInternal vertx, QuicServerConfig config, String protocol, ServerSSLOptions sslOptions) {
    super(vertx, config, protocol);
    this.config = config;
    this.sslOptions = sslOptions;
    this.mapping = new MappingRef<>();
  }

  @Override
  void closeHook() {
    // Clear to the actual mapping: BoringSSLTlsextServernameCallback keeps a reference the mapping
    // and a JNI GC root keeps a reference to BoringSSLTlsextServernameCallback
    mapping.actual = null;
    super.closeHook();
  }

  @Override
  public QuicServer connectHandler(Handler<QuicConnection> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public QuicServer exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public QuicServerImpl tokenHandler(QuicTokenHandler tokenHandler) {
    this.tokenHandler = tokenHandler;
    return this;
  }

  @Override
  ServerSslContextManager sslContextManager(BoringSslEngineOptions engine) {
    return new ServerSslContextManager(engine);
  }

  @Override
  protected Future<ChannelHandler> channelHandler(ContextInternal context, ServerID serverID, TransportMetrics<?> metrics) throws Exception {
    if (config.isLoadBalanced()) {
      LocalMap<String, QuicDispatcher> map = vertx.sharedData().getLocalMap(QUIC_SERVER_MAP_KEY);
      QuicDispatcher dispatcher;
      boolean init;
      synchronized (map) {
        QuicDispatcher attempt = map.get(serverID.toString());
        if (attempt == null) {
          init = true;
          attempt = new QuicDispatcher(serverID, new SslContextProviderReference((ServerSslContextManager)manager));
          map.put(serverID.toString(), attempt);
        } else {
          init = false;
        }
        dispatcher = attempt;
      }
      Future<ServerSslContextProvider> fut;
      if (init) {
        fut = dispatcher.sslContextProviderRef.update(sslOptions, context);
      } else {
        fut = context.succeededFuture();
      }
      sslContextProviderRef = dispatcher.sslContextProviderRef;
      return fut.<ChannelHandler>map(sslContextProvider -> new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel ch) {
          dispatcher.register(ch, context, QuicServerImpl.this, metrics);
          ch.pipeline().addLast(dispatcher);
        }
      });
    } else {
      return super.channelHandler(context, serverID, metrics);
    }
  }

  @Override
  protected Future<QuicCodecBuilder<?>> codecBuilder(ContextInternal context, TransportMetrics<?> metrics) throws Exception {
    SslContextProviderReference ref = new SslContextProviderReference((ServerSslContextManager)manager);
    Future<ServerSslContextProvider> fut = ref.update(sslOptions, context);
    return fut.map(sslContextProvider -> {
      try {
        sslContextProviderRef = ref;
        return createCodecBuilder(context, metrics);
      } catch (Exception e) {
        PlatformDependent.throwException(e);
        throw new AssertionError();
      }
    });
  }

  private QuicServerCodecBuilder createCodecBuilder(ContextInternal context, TransportMetrics<?> metrics) throws Exception {
    List<String> applicationProtocols = sslOptions.getApplicationLayerProtocols();
    boolean sni = sslOptions.isSni();
    mapping.actual = name -> {
      ServerSslContextProvider provider = sslContextProviderRef.get();
      if (sni && name != null) {
        Mapping<? super String, ? extends SslContext> resolved = provider.serverNameMapping(applicationProtocols);
        return (QuicSslContext) resolved.map(name);
      } else {
        return (QuicSslContext) provider.createServerContext(applicationProtocols);
      }
    };
    QuicSslContextBuilder sslContextBuilder = QuicSslContextBuilder
      .forServer(SNI_KEYMANAGER, null)
      .clientAuth(ClientAuth.REQUIRE)
      .sni(mapping);
    sslContextBuilder.keylog(keylog);
    if (sslOptions.getClientAuth() != null) {
      sslContextBuilder.clientAuth(SslContextManager.mapClientAuth(sslOptions.getClientAuth()));
    }
    QuicSslContext sslContext = sslContextBuilder.build();
    QuicTokenHandler qtc = tokenHandler;
    if (qtc == null) {
      switch (config.getClientAddressValidation()) {
        case BASIC:
          qtc = InsecureQuicTokenHandler.INSTANCE;
          break;
        case CRYPTO:
          KeyCertOptions tokenValidationKey = config.getClientAddressValidationKey();
          if (tokenValidationKey == null) {
            throw new IllegalArgumentException("The server must be configured with a token validation key to operate address validation");
          }
          Duration timeWindow = config.getClientAddressValidationTimeWindow();
          TokenManager tokenManager = new TokenManager(vertx, timeWindow);
          tokenManager.init(tokenValidationKey);
          qtc = tokenManager;
          break;
      }
    }
    QuicServerCodecBuilder builder = new QuicServerCodecBuilder().sslContext(sslContext)
      .tokenHandler(qtc)
      .handler(new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel ch) {
          connectionGroup.add(ch);
          QuicChannel channel = (QuicChannel) ch;
          LogConfig logConfig = config.getLogConfig();
          ByteBufFormat activityLogging = logConfig != null && logConfig.isEnabled() ? logConfig.getDataFormat() : null;
          Completable<QuicConnection> adapter = (result, failure) -> {
            if (failure != null) {
              Handler<Throwable> handler = exceptionHandler;
              if (handler != null) {
                context.dispatch(failure, handler);
              }
            } else {
              context.dispatch(result, handler);
            }
          };
          QuicConnectionHandler handler = new QuicConnectionHandler(context, metrics, config.getIdleTimeout(),
            config.getReadIdleTimeout(), config.getWriteIdleTimeout(), activityLogging, config.getMaxStreamBidiRequests(),
            config.getMaxStreamUniRequests(), vertx.transport().convert(channel.remoteSocketAddress()), true,
            adapter);
          ChannelPipeline pipeline = channel.pipeline();
          pipeline.addLast("handler", handler);
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

    QLogConfig qlogCfg = config.getQLogConfig();
    if (qlogCfg != null) {
      if (qlogCfg.getPath() == null) {
        throw new IllegalArgumentException("Missing QLog path configuration");
      }
      if (qlogCfg.getTitle() == null) {
        throw new IllegalArgumentException("Missing QLog title configuration");
      }
      if (qlogCfg.getDescription() == null) {
        throw new IllegalArgumentException("Missing QLog description configuration");
      }
      QLogConfiguration qLogConfiguration = new QLogConfiguration(qlogCfg.getPath(),
        qlogCfg.getTitle(),
        qlogCfg.getDescription());
      builder.option(QuicChannelOption.QLOG, qLogConfiguration);
    }
    return builder;
  }

  private class QuicDispatcher extends QuicCodecDispatcher implements Shareable {

    private final ServerID serverID;
    private final SslContextProviderReference sslContextProviderRef;
    private Map<Channel, ServerRegistration> registrations;

    public QuicDispatcher(ServerID serverID, SslContextProviderReference sslContextProviderRef) {
      this.serverID = serverID;
      this.sslContextProviderRef = sslContextProviderRef;
      this.registrations = new ConcurrentHashMap<>();
    }

    private class ServerRegistration {

      final ContextInternal context;
      final QuicServerImpl server;
      final TransportMetrics<?> metrics;

      ServerRegistration(ContextInternal context, QuicServerImpl server, TransportMetrics<?> metrics) {
        this.context = context;
        this.server = server;
        this.metrics = metrics;
      }
    }

    void register(Channel ch, ContextInternal context, QuicServerImpl server, TransportMetrics<?> metrics) {
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
          QuicServerCodecBuilder codecBuilder = registration.server.createCodecBuilder(registration.context, registration.metrics);
          codecBuilder.localConnectionIdLength(localConnectionIdLength);
          codecBuilder.connectionIdAddressGenerator(idGenerator);
          registration.server.initQuicCodecBuilder(codecBuilder, registration.metrics);
          channel.pipeline().addLast(codecBuilder.build());
          return;
        }
      }
      channel.close();
    }
  }

  @Override
  public Future<SocketAddress> listen() {
    return listen(config.getPort(), config.getHost());
  }

  @Override
  public Future<Boolean> updateSSLOptions(ServerSSLOptions options, boolean force) {
    SslContextProviderReference ref = sslContextProviderRef;
    if (ref == null) {
      return vertx.failedFuture("Server not listening");
    }
    return ref
      .update(options, vertx.getOrCreateContext(), force)
      .map(Objects::nonNull);
  }

  private static final X509ExtendedKeyManager SNI_KEYMANAGER = new X509ExtendedKeyManager() {
    private final X509Certificate[] emptyCerts = new X509Certificate[0];
    private final String[] emptyStrings = new String[0];

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
      return emptyStrings;
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
      return null;
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
      return emptyStrings;
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
      return null;
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
      return emptyCerts;
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
      return null;
    }
  };
}
