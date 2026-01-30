/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Composite HTTP server metrics.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CompositeHttpServerMetrics<R, W, S> implements HttpServerMetrics<R, W, S> {

  private final HttpServerMetrics<R, W, S> actual;
  private final AtomicInteger closeCount = new AtomicInteger(2);

  public CompositeHttpServerMetrics(HttpServerMetrics<R, W, S> actual) {
    this.actual = actual;
  }

  @Override
  public R requestBegin(S socketMetric, HttpRequest request) {
    return actual.requestBegin(socketMetric, request);
  }

  @Override
  public void requestEnd(R requestMetric, HttpRequest request, long bytesRead) {
    actual.requestEnd(requestMetric, request, bytesRead);
  }

  @Override
  public void requestReset(R requestMetric) {
    actual.requestReset(requestMetric);
  }

  @Override
  public void responseBegin(R requestMetric, HttpResponse response) {
    actual.responseBegin(requestMetric, response);
  }

  @Override
  public R responsePushed(S socketMetric, HttpMethod method, String uri, HttpResponse response) {
    return actual.responsePushed(socketMetric, method, uri, response);
  }

  @Override
  public void responseEnd(R requestMetric, HttpResponse response, long bytesWritten) {
    actual.responseEnd(requestMetric, response, bytesWritten);
  }

  @Override
  public W connected(S socketMetric, R requestMetric, ServerWebSocket serverWebSocket) {
    return actual.connected(socketMetric, requestMetric, serverWebSocket);
  }

  @Override
  public void disconnected(W serverWebSocketMetric) {
    actual.disconnected(serverWebSocketMetric);
  }

  @Override
  public void requestRouted(R requestMetric, String route) {
    actual.requestRouted(requestMetric, route);
  }

  @Override
  public void bound(boolean tcp, SocketAddress localAddress) {
    actual.bound(tcp, localAddress);
  }

  @Override
  public void unbound(boolean tcp, SocketAddress localAddress) {
    actual.unbound(tcp, localAddress);
  }

  @Override
  public S connected(SocketAddress remoteAddress, String remoteName) {
    return actual.connected(remoteAddress, remoteName);
  }

  @Override
  public void disconnected(S socketMetric, SocketAddress remoteAddress) {
    actual.disconnected(socketMetric, remoteAddress);
  }

  @Override
  public void bytesRead(S socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    actual.bytesRead(socketMetric, remoteAddress, numberOfBytes);
  }

  @Override
  public void bytesWritten(S socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    actual.bytesWritten(socketMetric, remoteAddress, numberOfBytes);
  }

  @Override
  public void exceptionOccurred(S socketMetric, SocketAddress remoteAddress, Throwable t) {
    actual.exceptionOccurred(socketMetric, remoteAddress, t);
  }

  @Override
  public void close() {
    if (closeCount.decrementAndGet() == 0) {
      actual.close();
    }
  }
}
