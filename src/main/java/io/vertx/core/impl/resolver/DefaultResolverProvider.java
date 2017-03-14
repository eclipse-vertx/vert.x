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

import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.vertx.core.Handler;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.spi.resolver.ResolverProvider;

import java.net.InetSocketAddress;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DefaultResolverProvider implements ResolverProvider {

  @Override
  public AddressResolverGroup<InetSocketAddress> resolver(AddressResolverOptions options) {
    return DefaultAddressResolverGroup.INSTANCE;
  }

  @Override
  public void close(Handler<Void> doneHandler) {
    doneHandler.handle(null);
  }
}
