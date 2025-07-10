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

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.net.HostAndPort;

public interface Http2ServerConnection extends Http2Connection {

  // Toto use interface for Http2ServerStream ????
  Http2ServerConnection streamHandler(Handler<Http2ServerStream> handler);

  // Promise<VertxHttpStream> instead
  void sendPush(int streamId,
                HostAndPort authority,
                HttpMethod method,
                MultiMap headers,
                String path,
                StreamPriorityBase streamPriority,
                Promise<Http2ServerStream> promise);
}
