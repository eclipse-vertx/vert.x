/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.WebSocketBase;

public interface WebSocketInternal extends WebSocketBase {

  ChannelHandlerContext channelHandlerContext();

  HttpConnection connection();

}
