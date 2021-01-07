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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.net.impl.clientconnection.ConnectResult;
import io.vertx.core.net.impl.clientconnection.ConnectionListener;
import io.vertx.core.net.impl.clientconnection.ConnectionProvider;

/**
 * Pooled HTTP connection provider.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpConnectionProvider implements ConnectionProvider<HttpClientConnection> {

  private final HttpClientImpl client;
  private final HttpChannelConnector connector;
  private final ContextInternal context;
  private final HttpClientOptions options;
  private final long weight;
  private final long http1Weight;
  private final long http2Weight;

  public HttpConnectionProvider(HttpClientImpl client,
                                HttpChannelConnector connector,
                                ContextInternal context,
                                HttpVersion version) {
    this.client = client;
    this.connector = connector;
    this.context = context;
    this.options = client.getOptions();
    // this is actually normal (although it sounds weird)
    // the pool uses a weight mechanism to keep track of the max number of connections
    // for instance when http2Size = 2 and http1Size= 5 then maxWeight = 10
    // which means that the pool can contain
    // - maxWeight / http1Weight = 5 HTTP/1.1 connections
    // - maxWeight / http2Weight = 2 HTTP/2 connections
    this.http1Weight = options.getHttp2MaxPoolSize();
    this.http2Weight = options.getMaxPoolSize();
    this.weight = version == HttpVersion.HTTP_2 ? http2Weight : http1Weight;
  }

  public long weight() {
    return weight;
  }

  @Override
  public void close(HttpClientConnection conn) {
    conn.close();
  }

  @Override
  public void init(HttpClientConnection conn) {
    Handler<HttpConnection> handler = client.connectionHandler();
    if (handler != null) {
      context.emit(conn, handler);
    }
  }

  @Override
  public boolean isValid(HttpClientConnection conn) {
    return conn.isValid();
  }

  @Override
  public void connect(ConnectionListener<HttpClientConnection> listener, ContextInternal context, Handler<AsyncResult<ConnectResult<HttpClientConnection>>> asyncResultHandler) {
    connector
      .httpConnect((EventLoopContext) context)
      .map(conn -> {
      conn.evictionHandler(recycle -> listener.onEvict());
      conn.concurrencyChangeHandler(listener::onConcurrencyChange);
      long weight;
      if (conn instanceof Http1xClientConnection) {
        weight = http1Weight;
      } else if (conn instanceof Http2ClientConnection) {
        weight = http2Weight;
      } else {
        // Upgrade
        weight = http2Weight;
      }
      return new ConnectResult<>(conn, conn.concurrency(), weight);
    }).onComplete(asyncResultHandler);
  }
}
