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

package io.vertx.core.internal.net;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.net.NetSocket;

/**
 * Extends to expose Netty interactions for reusing existing Netty codecs and benefit from the features
 * {@link io.vertx.core.net.NetServer} and {@link io.vertx.core.net.NetClient}:
 *
 * <ul>
 *   <li>Server sharing</li>
 *   <li>SSL/TLS</li>
 *   <li>SNI</li>
 *   <li>SSL/TLS upgrade</li>
 *   <li>Write batching during read operation</li>
 *   <li>Client proxy support</li>
 * </ul>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface NetSocketInternal extends NetSocket, SocketInternal {

  /**
   * Common reusable connection closed exception.
   */
  VertxException CLOSED_EXCEPTION = new VertxException("Connection was closed", true);

  @Override
  Future<Void> write(String str);

  @Override
  Future<Void> write(String str, String enc);
}
