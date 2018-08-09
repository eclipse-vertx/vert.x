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

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeHttpClientMetrics extends FakeMetricsBase implements HttpClientMetrics<HttpClientMetric, WebSocketMetric, SocketMetric, EndpointMetric, Void> {

  private final String name;
  private final ConcurrentMap<WebSocketBase, WebSocketMetric> webSockets = new ConcurrentHashMap<>();
  private final ConcurrentMap<HttpClientRequest, HttpClientMetric> requests = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, EndpointMetric> endpoints = new ConcurrentHashMap<>();

  public FakeHttpClientMetrics(String name) {
    this.name = name;
  }

  public WebSocketMetric getMetric(WebSocket ws) {
    return webSockets.get(ws);
  }

  public HttpClientMetric getMetric(HttpClientRequest request) {
    return requests.get(request);
  }

  public String getName() {
    return name;
  }

  public Set<String> endpoints() {
    return new HashSet<>(endpoints.keySet());
  }

  public EndpointMetric endpoint(String name) {
    return endpoints.get(name);
  }

  public Integer queueSize(String name) {
    EndpointMetric server = endpoints.get(name);
    return server != null ? server.queueSize.get() : null;
  }

  public Integer connectionCount(String name) {
    EndpointMetric server = endpoints.get(name);
    return server != null ? server.connectionCount.get() : null;
  }

  @Override
  public EndpointMetric createEndpoint(String host, int port, int maxPoolSize) {
    EndpointMetric metric = new EndpointMetric();
    endpoints.put(host + ":" + port, metric);
    return metric;
  }

  @Override
  public Void enqueueRequest(EndpointMetric endpointMetric) {
    endpointMetric.queueSize.incrementAndGet();
    return null;
  }

  @Override
  public void dequeueRequest(EndpointMetric endpointMetric, Void v) {
    endpointMetric.queueSize.decrementAndGet();
  }

  @Override
  public void closeEndpoint(String host, int port, EndpointMetric endpointMetric) {
    endpoints.remove(host + ":" + port);
  }

  @Override
  public void endpointConnected(EndpointMetric endpointMetric, SocketMetric socketMetric) {
    endpointMetric.connectionCount.incrementAndGet();
  }

  @Override
  public void endpointDisconnected(EndpointMetric endpointMetric, SocketMetric socketMetric) {
    endpointMetric.connectionCount.decrementAndGet();
  }

  @Override
  public WebSocketMetric connected(EndpointMetric endpointMetric, SocketMetric socketMetric, WebSocket webSocket) {
    WebSocketMetric metric = new WebSocketMetric(socketMetric, webSocket);
    webSockets.put(webSocket, metric);
    return metric;
  }

  @Override
  public void disconnected(WebSocketMetric webSocketMetric) {
    webSockets.remove(webSocketMetric.ws);
  }

  @Override
  public HttpClientMetric requestBegin(EndpointMetric endpointMetric, SocketMetric socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
    endpointMetric.requests.incrementAndGet();
    HttpClientMetric metric = new HttpClientMetric(endpointMetric, request, socketMetric);
    requests.put(request, metric);
    return metric;
  }

  @Override
  public void requestEnd(HttpClientMetric requestMetric) {
    requestMetric.requestEnded.incrementAndGet();
  }

  @Override
  public void responseBegin(HttpClientMetric requestMetric, HttpClientResponse response) {
    assertNotNull(response);
    requestMetric.responseBegin.incrementAndGet();
  }

  @Override
  public HttpClientMetric responsePushed(EndpointMetric endpointMetric, SocketMetric socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
    endpointMetric.requests.incrementAndGet();
    HttpClientMetric metric = new HttpClientMetric(endpointMetric, request, socketMetric);
    requests.put(request, metric);
    return metric;
  }

  @Override
  public void requestReset(HttpClientMetric requestMetric) {
    requestMetric.endpoint.requests.decrementAndGet();
    requestMetric.failed.set(true);
    requests.remove(requestMetric.request);
  }

  @Override
  public void responseEnd(HttpClientMetric requestMetric, HttpClientResponse response) {
    requestMetric.endpoint.requests.decrementAndGet();
    requests.remove(requestMetric.request);
  }

  public SocketMetric connected(SocketAddress remoteAddress, String remoteName) {
    return new SocketMetric(remoteAddress, remoteName);
  }

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
