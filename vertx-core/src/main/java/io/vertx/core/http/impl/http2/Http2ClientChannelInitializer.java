/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.http2;

import io.netty.channel.Channel;
import io.vertx.core.http.impl.Http1xClientConnection;
import io.vertx.core.http.impl.Http2UpgradeClientConnection;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Http2ClientChannelInitializer {

  void http2Connected(ContextInternal context, Object metric, Channel ch, PromiseInternal<HttpClientConnection> promise);

  Http2UpgradeClientConnection.Http2ChannelUpgrade channelUpgrade(Http1xClientConnection conn);

}
