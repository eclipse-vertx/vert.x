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

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.impl.VertxConnection;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServerWebSocketImpl extends WebSocketImplBase<ServerWebSocketImpl> implements ServerWebSocket {

  private final String scheme;
  private final HostAndPort authority;
  private final String uri;
  private final String path;
  private final String query;

  ServerWebSocketImpl(ContextInternal context,
                      VertxConnection conn,
                      boolean supportsContinuation,
                      Http1xServerRequest request,
                      int maxWebSocketFrameSize,
                      int maxWebSocketMessageSize,
                      boolean registerWebSocketWriteHandlers) {
    super(context, conn, request.headers(), supportsContinuation, maxWebSocketFrameSize, maxWebSocketMessageSize, registerWebSocketWriteHandlers);
    this.scheme = request.scheme();
    this.authority = request.authority();
    this.uri = request.uri();
    this.path = request.path();
    this.query = request.query();
  }

  @Override
  public String scheme() {
    return scheme;
  }

  @Override
  public HostAndPort authority() {
    return authority;
  }

  @Override
  public String uri() {
    return uri;
  }

  @Override
  public String path() {
    return path;
  }

  @Override
  public String query() {
    return query;
  }

}
