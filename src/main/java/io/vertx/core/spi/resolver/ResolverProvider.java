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
  boolean DISABLE_DNS_RESOLVER = Boolean.getBoolean(DISABLE_DNS_RESOLVER_PROP_NAME);

  static ResolverProvider factory(Vertx vertx, AddressResolverOptions options) {
    // For now not really plugable, we just want to not fail when we can't load the async provider
    // that use an unstable API and fallback on the default (blocking) provider
    try {
      if (!DISABLE_DNS_RESOLVER) {
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
