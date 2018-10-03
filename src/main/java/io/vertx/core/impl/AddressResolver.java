/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.netty.resolver.AddressResolverGroup;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.impl.launcher.commands.ExecUtils;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.resolver.ResolverProvider;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class AddressResolver {

  private static final Logger log = LoggerFactory.getLogger(AddressResolver.class);

  private static Pattern resolvOption(String regex) {
    return Pattern.compile("^[ \\t\\f]*options[^\n]+" + regex + "(?=$|\\s)", Pattern.MULTILINE);
  }

  private static final Pattern NDOTS_OPTIONS_PATTERN = resolvOption("ndots:[ \\t\\f]*(\\d)+");
  private static final Pattern ROTATE_OPTIONS_PATTERN = resolvOption("rotate");
  public static final int DEFAULT_NDOTS_RESOLV_OPTION;
  public static final boolean DEFAULT_ROTATE_RESOLV_OPTION;

  static {
    int ndots = 1;
    boolean rotate = false;
    if (ExecUtils.isLinux()) {
      File f = new File("/etc/resolv.conf");
      try {
        if (f.exists() && f.isFile()) {
          String conf = new String(Files.readAllBytes(f.toPath()));
          int ndotsOption = parseNdotsOptionFromResolvConf(conf);
          if (ndotsOption != -1) {
            ndots = ndotsOption;
          }
          rotate = parseRotateOptionFromResolvConf(conf);
        }
      } catch (Throwable t) {
        log.debug("Failed to load options from /etc/resolv/.conf", t);
      }
    }
    DEFAULT_NDOTS_RESOLV_OPTION = ndots;
    DEFAULT_ROTATE_RESOLV_OPTION = rotate;
  }

  private final Vertx vertx;
  private final AddressResolverGroup<InetSocketAddress> resolverGroup;
  private final ResolverProvider provider;

  public AddressResolver(Vertx vertx, AddressResolverOptions options) {
    this.provider = ResolverProvider.factory(vertx, options);
    this.resolverGroup = provider.resolver(options);
    this.vertx = vertx;
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
    provider.close(doneHandler);
  }

  public static int parseNdotsOptionFromResolvConf(String s) {
    int ndots = -1;
    Matcher matcher = NDOTS_OPTIONS_PATTERN.matcher(s);
    while (matcher.find()) {
      ndots = Integer.parseInt(matcher.group(1));
    }
    return ndots;
  }

  public static boolean parseRotateOptionFromResolvConf(String s) {
    Matcher matcher = ROTATE_OPTIONS_PATTERN.matcher(s);
    return matcher.find();
  }
}
