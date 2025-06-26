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
package io.vertx.core.http.impl.http2;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;

public interface Http2ServerStreamHandler extends Http2StreamHandler {

  void handleHead(MultiMap headers);

  // This should be removed which means changing the contract of tracing to provide observable response instead of HttpServerResponse
  Http2ServerResponse response();

  void dispatch(Handler<HttpServerRequest> handler);

}
