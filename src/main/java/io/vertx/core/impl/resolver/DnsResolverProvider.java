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
package io.vertx.core.impl.resolver;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.HostsFileParser;
import io.netty.resolver.NameResolver;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.DnsServerAddressStream;
import io.netty.resolver.dns.DnsServerAddresses;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.impl.AddressResolver;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.spi.resolver.ResolverProvider;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DnsResolverProvider implements ResolverProvider {

  private final Vertx vertx;
  private final List<ResolverRegistration> resolvers = Collections.synchronizedList(new ArrayList<>());
  private AddressResolverGroup<InetSocketAddress> resolverGroup;

  public DnsResolverProvider(VertxImpl vertx, AddressResolverOptions options) {
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
    DnsServerAddresses nameServerAddresses = options.isRotateServers() ? DnsServerAddresses.rotational(serverList) : DnsServerAddresses.sequential(serverList);

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

    this.vertx = vertx;
    this.resolverGroup = new AddressResolverGroup<InetSocketAddress>() {

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
                ndots = AddressResolver.DEFAULT_NDOTS_RESOLV_OPTION;
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
    };
  }

  private static class ResolverRegistration {
    private final io.netty.resolver.AddressResolver<InetSocketAddress> resolver;
    private final EventLoop executor;
    ResolverRegistration(io.netty.resolver.AddressResolver<InetSocketAddress> resolver, EventLoop executor) {
      this.resolver = resolver;
      this.executor = executor;
    }
  }

  @Override
  public AddressResolverGroup<InetSocketAddress> resolver(AddressResolverOptions options) {
    return resolverGroup;
  }

  @Override
  public void close(Handler<Void> doneHandler) {
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
}
