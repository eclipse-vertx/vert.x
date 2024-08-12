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

import io.netty.channel.ChannelHandler;
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler;
import io.netty.incubator.codec.http3.Http3ConnectionHandler;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.http3.Http3SettingsFrame;

class VertxHttp3ConnectionHandlerBuilder {

  private boolean isServer;

  protected VertxHttp3ConnectionHandlerBuilder server(boolean isServer) {
    this.isServer = isServer;
    return this;
  }

  protected Http3ConnectionHandler build(ChannelHandler controlStreamHandler,
                                         Http3SettingsFrame initialSettings) {
    Http3ConnectionHandler handler;
    if (isServer) {
      handler = new Http3ServerConnectionHandler(controlStreamHandler,
        null, null, initialSettings, false);
    } else {
      handler = new Http3ClientConnectionHandler(controlStreamHandler,
        null, null, initialSettings, false);
    }
    return handler;
  }
}
