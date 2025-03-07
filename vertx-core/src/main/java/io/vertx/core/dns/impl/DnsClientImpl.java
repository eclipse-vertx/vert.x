/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.dns.impl;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.*;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.datagram.impl.InternetProtocolFamily;
import io.vertx.core.dns.*;
import io.vertx.core.dns.DnsResponseCode;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class DnsClientImpl implements DnsClient {

  public static final char[] HEX_TABLE = "0123456789abcdef".toCharArray();

  private static final Function<List<String>, @Nullable String> FIRST_OF = lst -> lst.size() > 0 ? lst.get(0) : null;

  private final VertxInternal vertx;
  private boolean closed;
  private final Map<EventLoop, DnsNameResolver> resolvers = new HashMap<>();
  private final Map<EventLoop, DnsNameResolver> ipv4Resolvers = new HashMap<>();
  private final Map<EventLoop, DnsNameResolver> ipv6Resolvers = new HashMap<>();
  private final DnsAddressResolverProvider provider;
  private final DnsClientOptions options;
  private final Set<Promise<?>> inflightRequests = ConcurrentHashMap.newKeySet();

  public DnsClientImpl(VertxInternal vertx, DnsClientOptions options) {

    InetSocketAddress dnsServer = new InetSocketAddress(options.getHost(), options.getPort());
    if (dnsServer.isUnresolved()) {
      throw new IllegalArgumentException("Cannot resolve the host to a valid ip address");
    }

    DnsAddressResolverProvider provider = DnsAddressResolverProvider.create(vertx, vertx.nameResolver().options()
      .setServers(Collections.singletonList(dnsServer.getHostString() + ":" + dnsServer.getPort()))
      .setOptResourceEnabled(false));

    this.options = new DnsClientOptions(options);
    this.provider = provider;
    this.vertx = vertx;
  }

  private DnsNameResolver resolver(ContextInternal ctx, InternetProtocolFamily ipFamily) {
    EventLoop el = ctx.nettyEventLoop();
    DnsNameResolver resolver;
    synchronized (this) {
      if (closed) {
        return null;
      }
      Map<EventLoop, DnsNameResolver> resolversToUse;
      ResolvedAddressTypes resolvedAddressTypes;
      if (ipFamily == null) {
        resolversToUse = resolvers;
        resolvedAddressTypes = ResolvedAddressTypes.IPV4_PREFERRED;
      } else {
        switch (ipFamily) {
          case IPv4:
            resolversToUse = ipv4Resolvers;
            resolvedAddressTypes = ResolvedAddressTypes.IPV4_ONLY;
            break;
          case IPv6:
            resolversToUse = ipv6Resolvers;
            resolvedAddressTypes = ResolvedAddressTypes.IPV6_ONLY;
            break;
          default:
            throw new UnsupportedOperationException();
        }
      }
      resolver = resolversToUse.get(el);
      if (resolver == null) {
        DnsNameResolverBuilder builder = provider.getDnsNameResolverBuilder();
        builder.resolvedAddressTypes(resolvedAddressTypes);
        builder.queryTimeoutMillis(options.getQueryTimeout());
        builder.recursionDesired(options.isRecursionDesired());
        resolver = builder.eventLoop(el).build();
        ipv4Resolvers.put(el, resolver);
      }
    }
    return resolver;
  }

  @Override
  public Future<@Nullable String> lookup(String name) {
    return resolveAll(name, null)
      // The DnsNameResolver does not allow to query with several questions so we need to fallback
      .map(FIRST_OF);
  }

  @Override
  public Future<@Nullable String> lookup4(String name) {
    return resolveAll(name, InternetProtocolFamily.IPv4).map(FIRST_OF);
  }

  @Override
  public Future<@Nullable String> lookup6(String name) {
    return resolveAll(name, InternetProtocolFamily.IPv6).map(FIRST_OF);
  }

  @Override
  public Future<List<String>> resolveA(String name) {
    return queryAll(name, DnsRecordType.A, RecordDecoder.A);
  }

  @Override
  public Future<List<String>> resolveAAAA(String name) {
    return queryAll(name, DnsRecordType.AAAA, RecordDecoder.AAAA);
  }

  @Override
  public Future<List<String>> resolveCNAME(String name) {
    return queryAll(name, DnsRecordType.CNAME, RecordDecoder.DOMAIN);
  }

  @Override
  public Future<List<MxRecord>> resolveMX(String name) {
    return queryAll(name, DnsRecordType.MX, RecordDecoder.MX);
  }

  @Override
  public Future<List<String>> resolveTXT(String name) {
    return queryAll(name, DnsRecordType.TXT, RecordDecoder.TXT).map(lst -> {
      List<String> res = new ArrayList<>();
      for (List<String> r : lst) {
        res.addAll(r);
      }
      return res;
    });
  }

  @Override
  public Future<@Nullable String> resolvePTR(String name) {
    return queryAll(name, DnsRecordType.PTR, RecordDecoder.DOMAIN).map(FIRST_OF);
  }

  @Override
  public Future<List<String>> resolveNS(String name) {
    return queryAll(name, DnsRecordType.NS, RecordDecoder.DOMAIN);
  }

  @Override
  public Future<List<SrvRecord>> resolveSRV(String name) {
    return queryAll(name, DnsRecordType.SRV, RecordDecoder.SRV);
  }

  private Future<List<String>> resolveAll(String name, InternetProtocolFamily ipFamily) {
    Objects.requireNonNull(name);
    ContextInternal ctx = vertx.getOrCreateContext();
    DnsNameResolver resolver = resolver(ctx, ipFamily);
    if (resolver == null) {
      return ctx.failedFuture("DNS client is closed");
    }
    Promise<List<InetAddress>> promise = ctx.promise();
    inflightRequests.add(promise);
    io.netty.util.concurrent.Future<List<InetAddress>> res;
    res = resolver.resolveAll(name);
    res.addListener((GenericFutureListener<io.netty.util.concurrent.Future<List<InetAddress>>>) future -> {
      if (inflightRequests.remove(promise)) {
        if (future.isSuccess()) {
          promise.complete(future.getNow());
        } else {
          promise.fail(future.cause());
        }
      }
    });
    return promise
      .future()
      .map(addresses -> {
        List<String> ret = new ArrayList<>();
        for (InetAddress inetAddress : addresses) {
          ret.add(inetAddress.getHostAddress());
        }
        return ret;
      });
  }

  private <T> Future<List<T>> queryAll(String name, DnsRecordType recordType, Function<DnsRecord, T> mapper) {
    Objects.requireNonNull(name);
    ContextInternal ctx = vertx.getOrCreateContext();
    DnsNameResolver resolver = resolver(ctx, InternetProtocolFamily.IPv4);
    if (resolver == null) {
      return ctx.failedFuture("DNS client is closed");
    }
    Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise = ctx.promise();
    inflightRequests.add(promise);
    io.netty.util.concurrent.Future<AddressedEnvelope<DnsResponse, InetSocketAddress>> res = resolver.query(new DefaultDnsQuestion(name, recordType));
    res.addListener((GenericFutureListener<io.netty.util.concurrent.Future<AddressedEnvelope<DnsResponse, InetSocketAddress>>>) future -> {
      if (inflightRequests.remove(promise)) {
        if (future.isSuccess()) {
          promise.complete(future.getNow());
        } else {
          promise.fail(future.cause());
        }
      }
    });
    return promise
      .future()
      .transform(ar -> {
      if (ar.succeeded()) {
        AddressedEnvelope<DnsResponse, InetSocketAddress> lst = ar.result();
        try {
          DnsResponse content = lst.content();
          DnsResponseCode code = DnsResponseCode.valueOf(content.code().intValue());
          if (code != DnsResponseCode.NOERROR) {
            return Future.failedFuture(new DnsException(code));
          }
          List<T> ret = new ArrayList<>();
          int cnt = content.count(DnsSection.ANSWER);
          String nameToMatch = name.endsWith(".") ? name : name + ".";
          for (int i = 0;i < cnt;i++) {
            DnsRecord record = content.recordAt(DnsSection.ANSWER, i);
            if (record.name().equals(nameToMatch)) {
              T mapped = mapper.apply(record);
              if (mapped != null) {
                ret.add(mapped);
              }
            }
          }
          return Future.succeededFuture(ret);
        } finally {
          lst.release();
        }
      } else {
        return (Future<List<T>>) (Future)ar;
      }
    });
  }

  @Override
  public Future<Void> close() {
    synchronized (this) {
      if (!closed) {
        closed = true;
        for (Map<EventLoop, DnsNameResolver> resolvers : Arrays.asList(resolvers, ipv4Resolvers, ipv6Resolvers)) {
          Collection<DnsNameResolver> values = new ArrayList<>(resolvers.values());
          resolvers.clear();
          for (DnsNameResolver resolver : values) {
            resolver.close();
          }
        }
        for (Promise<?> inflight : inflightRequests) {
          inflight.tryFail(new VertxException("closed"));
        }
      }
    }
    return Future.succeededFuture();
  }
}
