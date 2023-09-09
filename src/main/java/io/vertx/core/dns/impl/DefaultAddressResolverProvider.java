/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.dns.impl;

import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.vertx.core.Future;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.spi.dns.AddressResolverProvider;

import java.net.InetSocketAddress;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DefaultAddressResolverProvider implements AddressResolverProvider {

  @Override
  public AddressResolverGroup<InetSocketAddress> resolver(AddressResolverOptions options) {
    return DefaultAddressResolverGroup.INSTANCE;
  }

  @Override
  public Future<Void> close() {
    return Future.succeededFuture();
  }
}
