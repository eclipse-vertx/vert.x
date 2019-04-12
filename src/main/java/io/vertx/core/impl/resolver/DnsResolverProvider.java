/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.resolver;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.HostsFileEntries;
import io.netty.resolver.HostsFileEntriesResolver;
import io.netty.resolver.HostsFileParser;
import io.netty.resolver.NameResolver;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.*;
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
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.util.internal.ObjectUtil.intValue;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DnsResolverProvider implements ResolverProvider {

  private final Vertx vertx;
  private final List<ResolverRegistration> resolvers = Collections.synchronizedList(new ArrayList<>());
  private AddressResolverGroup<InetSocketAddress> resolverGroup;
  private final List<InetSocketAddress> serverList = new ArrayList<>();

  /**
   * @return a list of DNS servers available to use
   */
  public List<InetSocketAddress> nameServerAddresses() {
    return serverList;
  }

  public DnsResolverProvider(VertxImpl vertx, AddressResolverOptions options) {
    List<String> dnsServers = options.getServers();
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
      DnsServerAddressStream stream = DefaultDnsServerAddressStreamProvider.defaultAddresses().stream();
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
    DnsServerAddressStreamProvider nameServerAddressProvider = hostname -> nameServerAddresses.stream();

    HostsFileEntries entries;
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

    int minTtl = intValue(options.getCacheMinTimeToLive(), 0);
    int maxTtl = intValue(options.getCacheMaxTimeToLive(), Integer.MAX_VALUE);
    int negativeTtl = intValue(options.getCacheNegativeTimeToLive(), 0);
    DnsCache resolveCache = new DefaultDnsCache(minTtl, maxTtl, negativeTtl);
    DnsCache authoritativeDnsServerCache = new DefaultDnsCache(minTtl, maxTtl, negativeTtl);

    this.vertx = vertx;
    this.resolverGroup = new AddressResolverGroup<InetSocketAddress>() {
      @Override
      protected io.netty.resolver.AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        ChannelFactory<DatagramChannel> channelFactory = () -> vertx.transport().datagramChannel();
        DnsAddressResolverGroup group = new DnsAddressResolverGroup(channelFactory, nameServerAddressProvider) {
          @Override
          protected NameResolver<InetAddress> newNameResolver(EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory, DnsServerAddressStreamProvider nameServerProvider) throws Exception {
            DnsNameResolverBuilder builder = new DnsNameResolverBuilder((EventLoop) executor);
            builder.hostsFileEntriesResolver(new HostsFileEntriesResolver() {
              @Override
              public InetAddress address(String inetHost, ResolvedAddressTypes resolvedAddressTypes) {
                InetAddress address = lookup(inetHost, resolvedAddressTypes);
                if (address == null) {
                  address = lookup(inetHost.toLowerCase(Locale.ENGLISH), resolvedAddressTypes);
                }
                return address;
              }
              InetAddress lookup(String inetHost, ResolvedAddressTypes resolvedAddressTypes) {
                switch (resolvedAddressTypes) {
                  case IPV4_ONLY:
                    return entries.inet4Entries().get(inetHost);
                  case IPV6_ONLY:
                    return entries.inet6Entries().get(inetHost);
                  case IPV4_PREFERRED:
                    Inet4Address inet4Address = entries.inet4Entries().get(inetHost);
                    return inet4Address != null? inet4Address : entries.inet6Entries().get(inetHost);
                  case IPV6_PREFERRED:
                    Inet6Address inet6Address = entries.inet6Entries().get(inetHost);
                    return inet6Address != null? inet6Address : entries.inet4Entries().get(inetHost);
                  default:
                    throw new IllegalArgumentException("Unknown ResolvedAddressTypes " + resolvedAddressTypes);
                }
              }
            });
            builder.channelFactory(channelFactory);
            builder.nameServerProvider(nameServerAddressProvider);
            builder.optResourceEnabled(options.isOptResourceEnabled());
            builder.resolveCache(resolveCache);
            builder.authoritativeDnsServerCache(authoritativeDnsServerCache);
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
