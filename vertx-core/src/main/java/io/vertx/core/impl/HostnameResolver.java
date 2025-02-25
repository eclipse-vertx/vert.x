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

package io.vertx.core.impl;

import io.netty.channel.EventLoop;
import io.netty.resolver.AddressResolverGroup;
import io.vertx.core.*;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.endpoint.EndpointBuilder;
import io.vertx.core.spi.dns.AddressResolverProvider;
import io.vertx.core.spi.endpoint.EndpointResolver;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.vertx.core.impl.Utils.isLinux;

/**
 * Resolves host names, using DNS and /etc/hosts config based on {@link AddressResolverOptions}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HostnameResolver implements AddressResolver {

  private static final Logger log = LoggerFactory.getLogger(HostnameResolver.class);

  private static final String NDOTS_LABEL = "ndots:";
  private static final String ROTATE_LABEL = "rotate";
  private static final String OPTIONS_ROW_LABEL = "options";
  public static final int DEFAULT_NDOTS_RESOLV_OPTION;
  public static final boolean DEFAULT_ROTATE_RESOLV_OPTION;


  private static final int DEFAULT_NDOTS = 1;
  private static final boolean DEFAULT_ROTATE = false;

  static {
    if (isLinux()) {
      ResolverOptions options = parseLinux(new File("/etc/resolv.conf"));
      DEFAULT_NDOTS_RESOLV_OPTION = options.effectiveNdots();
      DEFAULT_ROTATE_RESOLV_OPTION = options.isRotate();
    } else {
      DEFAULT_NDOTS_RESOLV_OPTION = DEFAULT_NDOTS;
      DEFAULT_ROTATE_RESOLV_OPTION = DEFAULT_ROTATE;
    }
  }

  private final Vertx vertx;
  private final AddressResolverGroup<InetSocketAddress> resolverGroup;
  private final AddressResolverProvider provider;

  public HostnameResolver(Vertx vertx, AddressResolverOptions options) {
    this.provider = AddressResolverProvider.factory(vertx, options);
    this.resolverGroup = provider.resolver(options);
    this.vertx = vertx;
  }

  @Override
  public EndpointResolver<?, ?, ?, ?> endpointResolver(Vertx vertx) {
    return new Impl();
  }

  public Future<InetAddress> resolveHostname(String hostname) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    io.netty.util.concurrent.Future<InetSocketAddress> fut = resolveHostname(context.nettyEventLoop(), hostname);
    PromiseInternal<InetSocketAddress> promise = context.promise();
    fut.addListener(promise);
    return promise.map(InetSocketAddress::getAddress);
  }

  public io.netty.util.concurrent.Future<InetSocketAddress> resolveHostname(EventLoop eventLoop, String hostname) {
    io.netty.resolver.AddressResolver<InetSocketAddress> resolver = getResolver(eventLoop);
    return resolver.resolve(InetSocketAddress.createUnresolved(hostname, 0));
  }

  public void resolveHostnameAll(String hostname, Handler<AsyncResult<List<InetSocketAddress>>> resultHandler) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    io.netty.util.concurrent.Future<List<InetSocketAddress>> fut = resolveHostnameAll(context.nettyEventLoop(), hostname);
    PromiseInternal<List<InetSocketAddress>> promise = context.promise();
    fut.addListener(promise);
    promise.future().onComplete(resultHandler);
  }

  public io.netty.util.concurrent.Future<List<InetSocketAddress>> resolveHostnameAll(EventLoop eventLoop, String hostname) {
    io.netty.resolver.AddressResolver<InetSocketAddress> resolver = getResolver(eventLoop);
    return resolver.resolveAll(InetSocketAddress.createUnresolved(hostname, 0));
  }

  public io.netty.resolver.AddressResolver<InetSocketAddress> getResolver(EventLoop eventLoop){
    return resolverGroup.getResolver(eventLoop);
  }

  AddressResolverGroup<InetSocketAddress> nettyAddressResolverGroup() {
    return resolverGroup;
  }

  public Future<Void> close() {
    return provider.close();
  }

  // visible for testing
  public static ResolverOptions parseLinux(File f) {
    try {
      if (f.exists() && f.isFile()) {
        return parseLinux(new String(Files.readAllBytes(f.toPath())));
      }
    } catch (Throwable t) {
      log.debug("Failed to load options from /etc/resolv.conf", t);
    }
    return new ResolverOptions(DEFAULT_NDOTS, DEFAULT_ROTATE);
  }

  // exists mainly to facilitate testing
  public static ResolverOptions parseLinux(String input) {
    int ndots = -1;
    boolean rotate = false;
    try {
      int optionsIndex = input.indexOf(OPTIONS_ROW_LABEL);
      if (optionsIndex != -1) {
        boolean isProperOptionsLabel = false;
        if (optionsIndex == 0) {
          isProperOptionsLabel = true;
        } else if (Character.isWhitespace(input.charAt(optionsIndex - 1))) {
            isProperOptionsLabel = true;
        }
        if (isProperOptionsLabel) {
          String afterOptions = input.substring(optionsIndex + OPTIONS_ROW_LABEL.length());
          int rotateIndex = afterOptions.indexOf(ROTATE_LABEL);
          if (rotateIndex != -1) {
            if (!containsNewLine(afterOptions.substring(0, rotateIndex))) {
              rotate = true;
            }
          }
          int ndotsIndex = afterOptions.indexOf(NDOTS_LABEL);
          if (ndotsIndex != -1) {
            if (!containsNewLine(afterOptions.substring(0, ndotsIndex))) {
              Matcher matcher = Holder.NDOTS_PATTERN.matcher(afterOptions.substring(ndotsIndex));
              while (matcher.find()) {
                ndots = Integer.parseInt(matcher.group(1));
              }
            }
          }
        }
      }
    } catch (NumberFormatException e) {
      log.debug("Failed to load options from /etc/resolv.conf", e);
    }
    return new ResolverOptions(ndots, rotate);
  }

  //TODO: this can easily be parallelized if necessary
  private static boolean containsNewLine(String input) {
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '\n') {
        return true;
      }
    }
    return false;
  }

  class Impl<L> implements EndpointResolver<SocketAddress, SocketAddress, L, L> {
    @Override
    public SocketAddress tryCast(Address address) {
      return address instanceof SocketAddress ? (SocketAddress) address : null;
    }

    @Override
    public SocketAddress addressOf(SocketAddress server) {
      return server;
    }

    @Override
    public Future<L> resolve(SocketAddress address, EndpointBuilder<L, SocketAddress> builder) {
      Promise<L> promise = Promise.promise();
      resolveHostnameAll(address.host(), ar -> {
        EndpointBuilder<L, SocketAddress> builder2 = builder;
        if (ar.succeeded()) {
          for (InetSocketAddress addr : ar.result()) {
            builder2 = builder2.addServer(SocketAddress.inetSocketAddress(address.port(), addr.getAddress().getHostAddress()));
          }
          promise.complete(builder2.build());
        } else {
          promise.fail(ar.cause());
        }
      });
      return promise.future();
    }

    @Override
    public L endpoint(L state) {
      return state;
    }

    @Override
    public boolean isValid(L state) {
      // NEED EXPIRATION
      return true;
    }

    @Override
    public void dispose(L data) {
    }

    @Override
    public void close() {
    }
  }

  public static class ResolverOptions {
    private final int ndots;
    private final boolean rotate;

    public ResolverOptions(int ndots, boolean rotate) {
      this.ndots = ndots;
      this.rotate = rotate;
    }

    public int ndots() {
      return ndots;
    }

    public int effectiveNdots() {
      return ndots != -1 ? ndots : 1;
    }

    public boolean isRotate() {
      return rotate;
    }
  }

  // used in order to avoid initialization of the pattern when it's not really needed
  private static class Holder {

    private static final Pattern NDOTS_PATTERN = Pattern.compile("ndots:[ \\t\\f]*(\\d)+(?=$|\\s)");
  }
}
