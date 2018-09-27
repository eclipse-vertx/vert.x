/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.fakemetrics;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeHttpServerMetrics extends FakeMetricsBase implements HttpServerMetrics<HttpServerMetric, WebSocketMetric, SocketMetric> {

  private final ConcurrentMap<WebSocketBase, WebSocketMetric> webSockets = new ConcurrentHashMap<>();
  private final ConcurrentHashSet<HttpServerMetric> requests = new ConcurrentHashSet<>();

  public WebSocketMetric getMetric(ServerWebSocket ws) {
    return webSockets.get(ws);
  }

  public HttpServerMetric getMetric(HttpServerRequest request) {
    return requests.stream().filter(m -> m.request == request).findFirst().orElse(null);
  }

  public HttpServerMetric getMetric(HttpServerResponse response) {
    return requests.stream().filter(m -> m.response.get() == response).findFirst().orElse(null);
  }

  @Override
  public HttpServerMetric requestBegin(SocketMetric socketMetric, HttpServerRequest request) {
    HttpServerMetric metric = new HttpServerMetric(request, socketMetric);
    requests.add(metric);
    return metric;
  }

  @Override
  public HttpServerMetric responsePushed(SocketMetric socketMetric, HttpMethod method, String uri, HttpServerResponse response) {
    HttpServerMetric requestMetric = new HttpServerMetric(null, socketMetric);
    requestMetric.response.set(response);
    requests.add(requestMetric);
    return requestMetric;
  }

  @Override
  public void requestReset(HttpServerMetric requestMetric) {
    requestMetric.failed.set(true);
    requests.remove(requestMetric);
  }

  @Override
  public void responseBegin(HttpServerMetric requestMetric, HttpServerResponse response) {
    requestMetric.response.set(response);
  }

  @Override
  public void responseEnd(HttpServerMetric requestMetric, HttpServerResponse response) {
    requests.remove(requestMetric);
  }

  @Override
  public WebSocketMetric connected(SocketMetric socketMetric, HttpServerMetric requestMetric, ServerWebSocket serverWebSocket) {
    if (!requests.remove(requestMetric)) {
      throw new IllegalStateException();
    }
    WebSocketMetric metric = new WebSocketMetric(socketMetric, serverWebSocket);
    if (webSockets.put(serverWebSocket, metric) != null) {
      throw new AssertionError();
    }
    return metric;
  }

  @Override
  public void disconnected(WebSocketMetric serverWebSocketMetric) {
    webSockets.remove(serverWebSocketMetric.ws);
  }

  @Override
  public SocketMetric connected(SocketAddress remoteAddress, String remoteName) {
    return new SocketMetric(remoteAddress, remoteName);
  }

  @Override
  public void disconnected(SocketMetric socketMetric, SocketAddress remoteAddress) {
    socketMetric.connected.set(false);
  }

  @Override
  public void bytesRead(SocketMetric socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    socketMetric.bytesRead.addAndGet(numberOfBytes);
  }

  @Override
  public void bytesWritten(SocketMetric socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    socketMetric.bytesWritten.addAndGet(numberOfBytes);
  }

  @Override
  public void exceptionOccurred(SocketMetric socketMetric, SocketAddress remoteAddress, Throwable t) {
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

}
