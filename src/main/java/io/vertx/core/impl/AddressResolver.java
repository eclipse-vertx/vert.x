/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.HostsFileParser;
import io.netty.resolver.NameResolver;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.DnsServerAddressStream;
import io.netty.resolver.dns.DnsServerAddresses;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.impl.launcher.commands.ExecUtils;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class AddressResolver {

  private static final int DEFAULT_NDOTS;
  private static final Pattern NDOTS_OPTIONS_PATTERN = Pattern.compile("^[ \\t\\f]*options[ \\t\\f]+ndots:[ \\t\\f]*(\\d)+(?=$|\\s)", Pattern.MULTILINE);
  private static final String DISABLE_DNS_RESOLVER_PROP_NAME = "vertx.disableDnsResolver";
  private static final boolean DISABLE_DNS_RESOLVER = Boolean.getBoolean(DISABLE_DNS_RESOLVER_PROP_NAME);

  static {
    int ndots = 1;
    if (ExecUtils.isLinux()) {
      File f = new File("/etc/resolv.conf");
      if (f.exists() && f.isFile()) {
        try {
          String conf = new String(Files.readAllBytes(f.toPath()));
          int ndotsOption = parseNdotsOptionFromResolvConf(conf);
          if (ndotsOption != -1) {
            ndots = ndotsOption;
          }
        } catch (IOException ignore) {
        }
      }
    }
    DEFAULT_NDOTS = ndots;
  }

  private final Vertx vertx;
  private final AddressResolverGroup<InetSocketAddress> resolverGroup;

  public AddressResolver(VertxImpl vertx, AddressResolverOptions options) {

    if (!DISABLE_DNS_RESOLVER) {

      List<String> dnsServers = options.getServers();
      List<InetSocketAddress> serverList = new ArrayList<>();
      if (dnsServers != null && dnsServers.size() > 0) {
        for (String dnsServer : dnsServers) {
          int sep = dnsServer.indexOf(':');
          String ipAddress;
          int port;
          if (sep != -1) {
            ipAddress = dnsServer.substring(0, sep);
            port = Integer.parseInt(dnsServer.substring(sep + 1));
          } else {
            ipAddress = dnsServer;
            port = 53;
          }
          try {
            serverList.add(new InetSocketAddress(InetAddress.getByAddress(NetUtil.createByteArrayFromIpAddressString(ipAddress)), port));
          } catch (UnknownHostException e) {
            throw new VertxException(e);
          }
        }
      } else {
        DnsServerAddressStream stream = DnsServerAddresses.defaultAddresses().stream();
        Set<InetSocketAddress> all = new HashSet<>();
        while (true) {
          InetSocketAddress address = stream.next();
          if (all.contains(address)) {
            break;
          }
          serverList.add(address);
          all.add(address);
        }
      }
      DnsServerAddresses nameServerAddresses = options.isRoundRobin() ? DnsServerAddresses.rotational(serverList) : DnsServerAddresses.sequential(serverList);

      Map<String, InetAddress> entries;
      if (options.getHostsPath() != null) {
        File file = vertx.resolveFile(options.getHostsPath()).getAbsoluteFile();
        try {
          if (!file.exists() || !file.isFile()) {
            throw new IOException();
          }
          entries = HostsFileParser.parse(file);
        } catch (IOException e) {
          throw new VertxException("Cannot read hosts file " + file.getAbsolutePath());
        }
      } else if (options.getHostsValue() != null) {
        try {
          entries = HostsFileParser.parse(new StringReader(options.getHostsValue().toString()));
        } catch (IOException e) {
          throw new VertxException("Cannot read hosts config ", e);
        }
      } else {
        entries = HostsFileParser.parseSilently();
      }

      resolverGroup = new AsyncCloseableResolverGroup() {

        private final List<ResolverRegistration> resolvers = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected io.netty.resolver.AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {

          DnsAddressResolverGroup group = new DnsAddressResolverGroup(NioDatagramChannel.class, nameServerAddresses) {
            @Override
            protected NameResolver<InetAddress> newNameResolver(EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory, DnsServerAddresses nameServerAddresses) throws Exception {
              DnsNameResolverBuilder builder = new DnsNameResolverBuilder((EventLoop) executor);
              builder.hostsFileEntriesResolver(inetHost -> {
                InetAddress addr = entries.get(inetHost);
                if (addr == null) {
                  addr = entries.get(inetHost.toLowerCase(Locale.ENGLISH));
                }
                return addr;
              });
              builder.channelType(NioDatagramChannel.class);
              builder.nameServerAddresses(nameServerAddresses);
              builder.optResourceEnabled(options.isOptResourceEnabled());
              builder.ttl(options.getCacheMinTimeToLive(), options.getCacheMaxTimeToLive());
              builder.negativeTtl(options.getCacheNegativeTimeToLive());
              builder.queryTimeoutMillis(options.getQueryTimeout());
              builder.maxQueriesPerResolve(options.getMaxQueries());
              builder.recursionDesired(options.getRdFlag());
              if (options.getSearchDomains() != null) {
                builder.searchDomains(options.getSearchDomains());
                int ndots = options.getNdots();
                if (ndots == -1) {
                  ndots = DEFAULT_NDOTS;
                }
                builder.ndots(ndots);
              }
              return builder.build();
            }
          };

          io.netty.resolver.AddressResolver<InetSocketAddress> resolver = group.getResolver(executor);
          resolvers.add(new ResolverRegistration(resolver, (EventLoop) executor));

          return resolver;
        }

        @Override
        void close(Handler<Void> doneHandler) {
          Context context = vertx.getOrCreateContext();
          ResolverRegistration[] registrations = this.resolvers.toArray(new ResolverRegistration[this.resolvers.size()]);
          if (registrations.length == 0) {
            context.runOnContext(doneHandler);
            return;
          }
          AtomicInteger count = new AtomicInteger(registrations.length);
          for (ResolverRegistration registration : registrations) {
            Runnable task = () -> {
              registration.resolver.close();
              if (count.decrementAndGet() == 0) {
                context.runOnContext(doneHandler);
              }
            };
            if (registration.executor.inEventLoop()) {
              task.run();
            } else {
              registration.executor.execute(task);
            }
          }
        }
      };
    } else {
      resolverGroup = DefaultAddressResolverGroup.INSTANCE;
    }

    this.vertx = vertx;
  }

  private static class ResolverRegistration {
    private final io.netty.resolver.AddressResolver<InetSocketAddress> resolver;
    private final EventLoop executor;
    ResolverRegistration(io.netty.resolver.AddressResolver<InetSocketAddress> resolver, EventLoop executor) {
      this.resolver = resolver;
      this.executor = executor;
    }
  }

  private static abstract class AsyncCloseableResolverGroup extends AddressResolverGroup<InetSocketAddress> {
    abstract void close(Handler<Void> doneHandler);
  }

  public void resolveHostname(String hostname, Handler<AsyncResult<InetAddress>> resultHandler) {
    ContextInternal callback = (ContextInternal) vertx.getOrCreateContext();
    io.netty.resolver.AddressResolver<InetSocketAddress> resolver = resolverGroup.getResolver(callback.nettyEventLoop());
    io.netty.util.concurrent.Future<InetSocketAddress> fut = resolver.resolve(InetSocketAddress.createUnresolved(hostname, 0));
    fut.addListener(a -> {
      callback.runOnContext(v -> {
        if (a.isSuccess()) {
          InetSocketAddress address = fut.getNow();
          resultHandler.handle(Future.succeededFuture(address.getAddress()));
        } else {
          resultHandler.handle(Future.failedFuture(a.cause()));
        }
      });
    });
  }

  AddressResolverGroup<InetSocketAddress> nettyAddressResolverGroup() {
    return resolverGroup;
  }

  public void close(Handler<Void> doneHandler) {
    if (resolverGroup instanceof AsyncCloseableResolverGroup) {
      ((AsyncCloseableResolverGroup) resolverGroup).close(doneHandler);
    } else {
      resolverGroup.close();
      doneHandler.handle(null);
    }
  }

  public static int parseNdotsOptionFromResolvConf(String s) {
    int ndots = -1;
    Matcher matcher = NDOTS_OPTIONS_PATTERN.matcher(s);
    while (matcher.find()) {
      ndots = Integer.parseInt(matcher.group(1));
    }
    return ndots;
  }
}
