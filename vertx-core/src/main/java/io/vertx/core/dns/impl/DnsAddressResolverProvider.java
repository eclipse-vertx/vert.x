/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.dns.impl;

import io.netty.channel.EventLoop;
import io.netty.channel.socket.SocketChannel;
import io.netty.resolver.*;
import io.netty.resolver.dns.*;
import io.netty.util.NetUtil;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.spi.dns.AddressResolverProvider;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.netty.util.internal.ObjectUtil.intValue;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DnsAddressResolverProvider implements AddressResolverProvider, HostsFileEntriesResolver {

  public static DnsAddressResolverProvider create(VertxInternal vertx, AddressResolverOptions options) {
    DnsAddressResolverProvider provider = new DnsAddressResolverProvider(vertx, options);
    provider.refresh();
    return provider;
  }

  private final VertxInternal vertx;
  private final List<ResolverRegistration> resolvers = Collections.synchronizedList(new ArrayList<>());
  private final DnsNameResolverBuilder dnsNameResolverBuilder;
  private AddressResolverGroup<InetSocketAddress> resolverGroup;
  private final List<InetSocketAddress> serverList = new ArrayList<>();
  private final String hostsPath;
  private final Buffer hostsValue;
  private final AtomicLong refreshTimestamp = new AtomicLong();
  private final long hostsRefreshPeriodNanos;
  private volatile HostsFileEntries parsedHostsFile = new HostsFileEntries(Collections.emptyMap(), Collections.emptyMap());

  private DnsAddressResolverProvider(VertxInternal vertx, AddressResolverOptions options) {
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


    int minTtl = intValue(options.getCacheMinTimeToLive(), 0);
    int maxTtl = intValue(options.getCacheMaxTimeToLive(), Integer.MAX_VALUE);
    int negativeTtl = intValue(options.getCacheNegativeTimeToLive(), 0);
    DnsCache resolveCache = new DefaultDnsCache(minTtl, maxTtl, negativeTtl);
    DnsCache authoritativeDnsServerCache = new DefaultDnsCache(minTtl, maxTtl, negativeTtl);

    this.vertx = vertx;
    this.hostsPath = options.getHostsPath();
    this.hostsValue = options.getHostsValue();
    this.hostsRefreshPeriodNanos = options.getHostsRefreshPeriod();

    DnsNameResolverBuilder builder = new DnsNameResolverBuilder();
    builder.hostsFileEntriesResolver(this);
    builder.channelFactory(() -> vertx.transport().datagramChannel());
    builder.socketChannelFactory(() -> (SocketChannel) vertx.transport().channelFactory(false).newChannel());
    builder.nameServerProvider(nameServerAddressProvider);
    builder.queryServerAddressStream(new ThreadLocalNameServerAddressStream(nameServerAddressProvider, ""));
    builder.optResourceEnabled(options.isOptResourceEnabled());
    builder.resolveCache(resolveCache);
    builder.authoritativeDnsServerCache(authoritativeDnsServerCache);
    builder.queryTimeoutMillis(options.getQueryTimeout());
    builder.maxQueriesPerResolve(options.getMaxQueries());
    builder.recursionDesired(options.getRdFlag());
    builder.completeOncePreferredResolved(true);
    builder.consolidateCacheSize(1024);
    builder.ndots(1);
    if (options.getSearchDomains() != null) {
      builder.searchDomains(options.getSearchDomains());
      int ndots = options.getNdots();
      if (ndots == -1) {
        ndots = AddressResolverOptions.DEFAULT_NDOTS;
      }
      builder.ndots(ndots);
    }

    this.dnsNameResolverBuilder = builder;
    this.resolverGroup = new DnsAddressResolverGroup(builder) {
      @Override
      protected io.netty.resolver.AddressResolver<InetSocketAddress> newAddressResolver(EventLoop eventLoop, io.netty.resolver.NameResolver<InetAddress> resolver) throws Exception {
        io.netty.resolver.AddressResolver<InetSocketAddress> addressResolver;
        if (options.isRoundRobinInetAddress()) {
          addressResolver = new RoundRobinInetAddressResolver(eventLoop, resolver).asAddressResolver();
        } else {
          addressResolver = super.newAddressResolver(eventLoop, resolver);
        }
        resolvers.add(new ResolverRegistration(addressResolver, eventLoop));
        return addressResolver;
      }
    };
  }

  @Override
  public InetAddress address(String inetHost, ResolvedAddressTypes resolvedAddressTypes) {
    if (inetHost.endsWith(".")) {
      inetHost = inetHost.substring(0, inetHost.length() - 1);
    }
    if (hostsRefreshPeriodNanos > 0) {
      ensureHostsFileFresh(hostsRefreshPeriodNanos);
    }
    InetAddress address = lookup(inetHost, resolvedAddressTypes);
    if (address == null) {
      address = lookup(inetHost.toLowerCase(Locale.ENGLISH), resolvedAddressTypes);
    }
    return address;
  }
  InetAddress lookup(String inetHost, ResolvedAddressTypes resolvedAddressTypes) {
    switch (resolvedAddressTypes) {
      case IPV4_ONLY:
        return parsedHostsFile.inet4Entries().get(inetHost);
      case IPV6_ONLY:
        return parsedHostsFile.inet6Entries().get(inetHost);
      case IPV4_PREFERRED:
        Inet4Address inet4Address = parsedHostsFile.inet4Entries().get(inetHost);
        return inet4Address != null? inet4Address : parsedHostsFile.inet6Entries().get(inetHost);
      case IPV6_PREFERRED:
        Inet6Address inet6Address = parsedHostsFile.inet6Entries().get(inetHost);
        return inet6Address != null? inet6Address : parsedHostsFile.inet4Entries().get(inetHost);
      default:
        throw new IllegalArgumentException("Unknown ResolvedAddressTypes " + resolvedAddressTypes);
    }
  }

  public DnsNameResolverBuilder getDnsNameResolverBuilder() {
    return dnsNameResolverBuilder;
  }

  /**
   * @return a list of DNS servers available to use
   */
  public List<InetSocketAddress> nameServerAddresses() {
    return serverList;
  }

  @Override
  public AddressResolverGroup<InetSocketAddress> resolver(AddressResolverOptions options) {
    return resolverGroup;
  }

  @Override
  public Future<Void> close() {
    ContextInternal context = vertx.getOrCreateContext();
    ResolverRegistration[] registrations = this.resolvers.toArray(new ResolverRegistration[0]);
    if (registrations.length == 0) {
      return context.succeededFuture();
    }
    Promise<Void> promise = context.promise();
    AtomicInteger count = new AtomicInteger(registrations.length);
    for (ResolverRegistration registration : registrations) {
      Runnable task = () -> {
        registration.resolver.close();
        if (count.decrementAndGet() == 0) {
          promise.complete();
        }
      };
      if (registration.executor.inEventLoop()) {
        task.run();
      } else {
        registration.executor.execute(task);
      }
    }
    return promise.future();
  }

  public void refresh() {
    ensureHostsFileFresh(0);
  }

  private void ensureHostsFileFresh(long refreshPeriodNanos) {
    long prev = refreshTimestamp.get();
    long now = System.nanoTime();
    if ((now - prev) >= refreshPeriodNanos && refreshTimestamp.compareAndSet(prev, now)) {
      refreshHostsFile();
    }
  }

  private void refreshHostsFile() {
    HostsFileEntries entries;
    if (hostsPath != null) {
      File file = vertx.fileResolver().resolve(hostsPath).getAbsoluteFile();
      try {
        if (!file.exists() || !file.isFile()) {
          throw new IOException();
        }
        entries = HostsFileParser.parse(file);
      } catch (IOException e) {
        throw new VertxException("Cannot read hosts file " + file.getAbsolutePath());
      }
    } else if (hostsValue != null) {
      try {
        entries = HostsFileParser.parse(new StringReader(hostsValue.toString()));
      } catch (IOException e) {
        throw new VertxException("Cannot read hosts config ", e);
      }
    } else {
      entries = HostsFileParser.parseSilently();
    }
    parsedHostsFile = entries;
  }

  private static class ResolverRegistration {
    private final io.netty.resolver.AddressResolver<InetSocketAddress> resolver;
    private final EventLoop executor;
    ResolverRegistration(io.netty.resolver.AddressResolver<InetSocketAddress> resolver, EventLoop executor) {
      this.resolver = resolver;
      this.executor = executor;
    }
  }

  // Avoid FastThreadLocal query server address stream default implementation
  private static class ThreadLocalNameServerAddressStream implements DnsServerAddressStream {

    private final String hostname;
    private final DnsServerAddressStreamProvider dnsServerAddressStreamProvider;
    private final ThreadLocal<DnsServerAddressStream> threadLocal = new ThreadLocal<>() {
      @Override
      protected DnsServerAddressStream initialValue() {
        return dnsServerAddressStreamProvider.nameServerAddressStream(hostname);
      }
    };

    ThreadLocalNameServerAddressStream(DnsServerAddressStreamProvider dnsServerAddressStreamProvider, String hostname) {
      this.dnsServerAddressStreamProvider = dnsServerAddressStreamProvider;
      this.hostname = hostname;
    }

    @Override
    public InetSocketAddress next() {
      return threadLocal.get().next();
    }

    @Override
    public DnsServerAddressStream duplicate() {
      return new ThreadLocalNameServerAddressStream(dnsServerAddressStreamProvider, hostname);
    }

    @Override
    public int size() {
      return threadLocal.get().size();
    }
  }
}
