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

package io.vertx.core.http.impl;

import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.vertx.core.internal.ContextInternal;

import java.util.function.Function;

class VertxHttp3ConnectionHandlerBuilder<C extends Http3ConnectionBase> {

  private Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory;
  private Http3SettingsFrame httpSettings;
  private boolean isServer;
  private long initialMaxStreamsBidirectional;

  VertxHttp3ConnectionHandlerBuilder<C> connectionFactory(Function<VertxHttp3ConnectionHandler<C>, C> connectionFactory) {
    this.connectionFactory = connectionFactory;
    return this;
  }


  protected VertxHttp3ConnectionHandlerBuilder<C> server(boolean isServer) {
    this.isServer = isServer;
    return this;
  }

  protected VertxHttp3ConnectionHandlerBuilder<C> initialMaxStreamsBidirectional(long initialMaxStreamsBidirectional) {
    this.initialMaxStreamsBidirectional = initialMaxStreamsBidirectional;
    return this;
  }

  public VertxHttp3ConnectionHandlerBuilder<C> httpSettings(Http3SettingsFrame httpSettings) {
    this.httpSettings = httpSettings;
    return this;
  }

  protected VertxHttp3ConnectionHandler<C> build(ContextInternal context) {
    return new VertxHttp3ConnectionHandler<>(connectionFactory, context, httpSettings, isServer, initialMaxStreamsBidirectional);
  }
}
