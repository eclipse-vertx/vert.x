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

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeHttpClientMetrics extends FakeTCPMetrics implements HttpClientMetrics<HttpClientMetric, WebSocketMetric, SocketMetric> {

  private final String name;
  private final ConcurrentMap<WebSocketBase, WebSocketMetric> webSockets = new ConcurrentHashMap<>();
  private final ConcurrentMap<SocketAddress, EndpointMetric> endpoints = new ConcurrentHashMap<>();

  public FakeHttpClientMetrics(String name) {
    this.name = name;
  }

  public WebSocketMetric getMetric(WebSocket ws) {
    return webSockets.get(ws);
  }

  public HttpClientMetric getMetric(HttpClientRequest request) {
    for (EndpointMetric metric : endpoints.values()) {
      for (HttpRequest req : metric.requests.keySet()) {
        if (req.uri().equals(request.getURI()) &&
            req.remoteAddress().equals(request.connection().remoteAddress()) &&
            req.method() == request.getMethod()) {
          return metric.requests.get(req);
        }
      }
    }
    return null;
  }

  public String getName() {
    return name;
  }

  public Set<String> endpoints() {
    return endpoints.keySet().stream().map(Object::toString).collect(Collectors.toSet());
  }

  public EndpointMetric endpoint(String name) {
    for (Map.Entry<SocketAddress, EndpointMetric> entry : endpoints.entrySet()) {
      if (entry.getKey().toString().equalsIgnoreCase(name)) {
        return entry.getValue();
      }
    }
    return null;
  }

//  public Integer queueSize(String name) {
//    EndpointMetric server = endpoint(name);
//    return server != null ? server.queueSize.get() : null;
//  }

  public Integer connectionCount(String name) {
    EndpointMetric endpoint = endpoint(name);
    return endpoint != null ? endpoint.connectionCount.get() : null;
  }

  @Override
  public ClientMetrics<HttpClientMetric, HttpRequest, HttpResponse> createEndpointMetrics(SocketAddress remoteAddress, int maxPoolSize) {
    EndpointMetric metric = new EndpointMetric() {
      @Override
      public void close() {
        endpoints.remove(remoteAddress);
      }
    };
    endpoints.put(remoteAddress, metric);
    return metric;
  }

  @Override
  public void endpointConnected(ClientMetrics<HttpClientMetric, ?, ?> endpointMetric) {
    ((EndpointMetric)endpointMetric).connectionCount.incrementAndGet();
  }

  @Override
  public void endpointDisconnected(ClientMetrics<HttpClientMetric, ?, ?> endpointMetric) {
    ((EndpointMetric)endpointMetric).connectionCount.decrementAndGet();
  }

  @Override
  public WebSocketMetric connected(WebSocket webSocket) {
    WebSocketMetric metric = new WebSocketMetric(webSocket);
    webSockets.put(webSocket, metric);
    return metric;
  }

  @Override
  public void disconnected(WebSocketMetric webSocketMetric) {
    webSockets.remove(webSocketMetric.ws);
  }

}
