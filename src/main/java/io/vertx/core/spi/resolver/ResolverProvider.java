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

package io.vertx.core.spi.resolver;

import io.netty.resolver.AddressResolverGroup;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.impl.resolver.DnsResolverProvider;
import io.vertx.core.impl.resolver.DefaultResolverProvider;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ResolverProvider {

  String DISABLE_DNS_RESOLVER_PROP_NAME = "vertx.disableDnsResolver";

  static ResolverProvider factory(Vertx vertx, AddressResolverOptions options) {
    // For now not really plugable, we just want to not fail when we can't load the async provider
    // that use an unstable API and fallback on the default (blocking) provider
    try {
      if (!Boolean.getBoolean(DISABLE_DNS_RESOLVER_PROP_NAME)) {
        return new DnsResolverProvider((VertxImpl) vertx, options);
      }
    } catch (Throwable e) {
      if (e instanceof VertxException) {
        throw e;
      }
      Logger logger = LoggerFactory.getLogger(ResolverProvider.class);
      logger.info("Using the default address resolver as the dns resolver could not be loaded");
    }
    return new DefaultResolverProvider();
  }

  AddressResolverGroup<InetSocketAddress> resolver(AddressResolverOptions options);

  void close(Handler<Void> doneHandler);

}
