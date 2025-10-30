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
package io.vertx.core.http.impl;

import io.netty.handler.codec.http3.Http3;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.Http3ClientOptions;
import io.vertx.core.http.impl.http3.Http3ClientConnection;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.ClientMetrics;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Http3ChannelConnector implements HttpChannelConnector {

  private final VertxInternal vertx;
  private final Lock lock;
  private Future<QuicClient> clientFuture;
  private final Http3ClientOptions options;

  public Http3ChannelConnector(VertxInternal vertxInternal,  Http3ClientOptions options) {

    options = new Http3ClientOptions(options);
    options.getSslOptions().setApplicationLayerProtocols(Arrays.asList(Http3.supportedApplicationProtocols()));
    options.getTransportOptions().setInitialMaxData(10000000L);
    options.getTransportOptions().setInitialMaxStreamDataBidirectionalLocal(1000000L);
    options.getTransportOptions().setInitialMaxStreamDataBidirectionalRemote(1000000L);
    options.getTransportOptions().setInitialMaxStreamDataUnidirectional(1000000L);
    options.getTransportOptions().setInitialMaxStreamsBidirectional(100L);
    options.getTransportOptions().setInitialMaxStreamsUnidirectional(100L);

    this.vertx = vertxInternal;
    this.lock = new ReentrantLock();
    this.options = options;
  }

  @Override
  public Future<HttpClientConnection> httpConnect(ContextInternal context, SocketAddress server, HostAndPort authority, HttpConnectParams params, long maxLifetimeMillis, ClientMetrics<?, ?, ?> metrics) {

    lock.lock();
    Future<QuicClient> fut = clientFuture;
    if (fut == null) {
      QuicClient client = QuicClient.create(vertx, this.options);
      fut = client.bind(SocketAddress.inetSocketAddress(0, "localhost")).map(client);
      clientFuture = fut;
      lock.unlock();
    } else {
      lock.unlock();
    }
    Promise<HttpClientConnection> promise = context.promise();

    fut.onComplete((res, err) -> {
      if (err == null) {
        Future<QuicConnection> f = res.connect(server);
        f.onComplete((res2, err2) -> {
          if (err2 == null) {
            Http3ClientConnection c = new Http3ClientConnection((QuicConnectionInternal) res2);
            c.init();
            promise.complete(c);
          } else {
            promise.fail(err2);
          }
        });
      } else {
        promise.fail(err);
      }
    });

    return promise.future();
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    if (clientFuture == null) {
      return vertx.getOrCreateContext().succeededFuture();
    } else {
      return clientFuture.compose(client -> client.shutdown(timeout));
    }
  }

  @Override
  public Future<Void> close() {
    if (clientFuture == null) {
      return vertx.getOrCreateContext().succeededFuture();
    } else {
      return clientFuture.compose(QuicEndpoint::close);
    }
  }
}
