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
import io.netty.resolver.InetNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.DnsServerAddresses;
import io.netty.util.NetUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.dns.HostnameResolverOptions;
import io.vertx.core.json.JsonObject;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HostnameResolver {

  private final Vertx vertx;
  private final InetNameResolver resolver;

  public HostnameResolver(VertxImpl vertx, HostnameResolverOptions options) {
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
      builder.optResourceEnabled(options.isOptResourceEnabled());
      builder.ttl(options.getCacheMinTimeToLive(), options.getCacheMaxTimeToLive());
      builder.negativeTtl(options.getCacheNegativeTimeToLive());
      builder.queryTimeoutMillis(options.getQueryTimeout());
      builder.maxQueriesPerResolve(options.getMaxQueries());
      builder.recursionDesired(options.getRdFlag());
    }
    this.resolver = builder.build();
    this.vertx = vertx;
  }

  public void resolveHostname(String hostname, Handler<AsyncResult<InetAddress>> resultHandler) {
    Context callback = vertx.getOrCreateContext();
    io.netty.util.concurrent.Future<InetAddress> fut = resolver.resolve(hostname);
    fut.addListener(a -> {
      callback.runOnContext(v -> {
        if (a.isSuccess()) {
          InetAddress address = fut.getNow();
          resultHandler.handle(Future.succeededFuture(address));
        } else {
          resultHandler.handle(Future.failedFuture(a.cause()));
        }
      });
    });
  }

  public void close() {
    resolver.close();
  }
}
