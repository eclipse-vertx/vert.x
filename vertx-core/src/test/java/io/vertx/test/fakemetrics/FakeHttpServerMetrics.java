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

package io.vertx.test.fakemetrics;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeHttpServerMetrics extends FakeTCPMetrics implements HttpServerMetrics<HttpServerMetric, WebSocketMetric, SocketMetric> {

  private final ConcurrentMap<String, WebSocketMetric> webSockets = new ConcurrentHashMap<>();
  private final Set<HttpServerMetric> requests = ConcurrentHashMap.newKeySet();

  public WebSocketMetric getWebSocketMetric(ServerWebSocket ws) {
    return webSockets.get(ws.path());
  }

  public HttpServerMetric getRequestMetric(HttpServerRequest request) {
    return requests.stream().filter(m -> m.uri.equals(request.uri())).findFirst().orElse(null);
  }

  public HttpServerMetric getResponseMetric(String uri) {
    return requests.stream().filter(m -> m.uri.equals(uri)).findFirst().orElse(null);
  }

  @Override
  public HttpServerMetric requestBegin(SocketMetric socketMetric, HttpRequest request) {
    HttpServerMetric metric = new HttpServerMetric(request, socketMetric);
    requests.add(metric);
    return metric;
  }

  @Override
  public void requestEnd(HttpServerMetric requestMetric, HttpRequest request, long bytesRead) {
    requestMetric.requestEnded.set(true);
    requestMetric.bytesRead.set(bytesRead);
  }

  @Override
  public HttpServerMetric responsePushed(SocketMetric socketMetric, HttpMethod method, String uri, HttpResponse response) {
    HttpServerMetric requestMetric = new HttpServerMetric(uri, socketMetric);
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
  public void responseBegin(HttpServerMetric requestMetric, HttpResponse response) {
    requestMetric.response.set(response);
  }

  @Override
  public void responseEnd(HttpServerMetric requestMetric, HttpResponse response, long bytesWritten) {
    requests.remove(requestMetric);
    requestMetric.responseEnded.set(true);
    requestMetric.bytesWritten.set(bytesWritten);
  }

  @Override
  public WebSocketMetric connected(SocketMetric socketMetric, HttpServerMetric requestMetric, ServerWebSocket serverWebSocket) {
    WebSocketMetric metric = new WebSocketMetric(serverWebSocket);
    if (webSockets.put(serverWebSocket.path(), metric) != null) {
      throw new AssertionError();
    }
    return metric;
  }

  @Override
  public void disconnected(WebSocketMetric serverWebSocketMetric) {
    webSockets.remove(((ServerWebSocket)serverWebSocketMetric.ws).path());
  }

  @Override
  public void exceptionOccurred(SocketMetric socketMetric, SocketAddress remoteAddress, Throwable t) {
  }

  @Override
  public void requestRouted(HttpServerMetric requestMetric, String route) {
    requestMetric.route.set(route);
  }
}
