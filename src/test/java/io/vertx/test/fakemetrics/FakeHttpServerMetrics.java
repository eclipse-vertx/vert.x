/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.fakemetrics;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
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
  public final HttpServer server;

  public FakeHttpServerMetrics(HttpServer server) {
    super(server);
    this.server = server;
  }

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
  public void responseEnd(HttpServerMetric requestMetric, HttpServerResponse response) {
    requests.remove(requestMetric);
  }

  @Override
  public WebSocketMetric upgrade(HttpServerMetric requestMetric, ServerWebSocket serverWebSocket) {
    requests.remove(requestMetric);
    WebSocketMetric metric = new WebSocketMetric(requestMetric.socket, serverWebSocket);
    webSockets.put(serverWebSocket, metric);
    return metric;
  }

  @Override
  public WebSocketMetric connected(SocketMetric socketMetric, ServerWebSocket serverWebSocket) {
    WebSocketMetric metric = new WebSocketMetric(socketMetric, serverWebSocket);
    webSockets.put(serverWebSocket, metric);
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

  @Override
  public void close() {
  }
}
