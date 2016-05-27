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

import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.HostsFileParser;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.DnsServerAddresses;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.json.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class AddressResolver {

  private final Vertx vertx;
  private final AddressResolverGroup<InetSocketAddress> resolverGroup;

  public AddressResolver(VertxImpl vertx, AddressResolverOptions options) {
    DnsNameResolverBuilder builder = new DnsNameResolverBuilder(vertx.createEventLoopContext(null, null, new JsonObject(), Thread.currentThread().getContextClassLoader()).nettyEventLoop());
    builder.channelFactory(NioDatagramChannel::new);
    if (options != null) {
      List<String> dnsServers = options.getServers();
      if (dnsServers != null && dnsServers.size() > 0) {
        List<InetSocketAddress> serverList = new ArrayList<>();
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
        DnsServerAddresses nameServerAddresses = DnsServerAddresses.sequential(serverList);
        builder.nameServerAddresses(nameServerAddresses);
      }


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
        entries = null;
      }
      if (entries != null) {
        builder.hostsFileEntriesResolver(entries::get);
      }

      builder.optResourceEnabled(options.isOptResourceEnabled());
      builder.ttl(options.getCacheMinTimeToLive(), options.getCacheMaxTimeToLive());
      builder.negativeTtl(options.getCacheNegativeTimeToLive());
      builder.queryTimeoutMillis(options.getQueryTimeout());
      builder.maxQueriesPerResolve(options.getMaxQueries());
      builder.recursionDesired(options.getRdFlag());
    }

    this.resolverGroup = new AddressResolverGroup<InetSocketAddress>() {
      @Override
      protected io.netty.resolver.AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        DnsNameResolver resolver = builder.build();
        return new InetSocketAddressResolver(executor, resolver) {
          @Override
          public void close() {
            // Workaround for bug https://github.com/netty/netty/issues/2545
            resolver.close();
          }
        };
      }
    };
    this.vertx = vertx;
  }

  public void resolveHostname(String hostname, Handler<AsyncResult<InetAddress>> resultHandler) {
    ContextInternal callback = (ContextInternal) vertx.getOrCreateContext();
    io.netty.resolver.AddressResolver<InetSocketAddress> resolver = resolverGroup.getResolver(callback.nettyEventLoop());
    io.netty.util.concurrent.Future<InetSocketAddress> fut = resolver.resolve(InetSocketAddress.createUnresolved(hostname, 80));
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

  public AddressResolverGroup<InetSocketAddress> nettyAddressResolverGroup() {
    return resolverGroup;
  }

  public void close() {
    resolverGroup.close();
  }
}
