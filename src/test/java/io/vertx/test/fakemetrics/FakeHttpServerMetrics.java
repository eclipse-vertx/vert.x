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

import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeHttpServerMetrics extends FakeMetricsBase implements HttpServerMetrics<Void, WebSocketMetric, SocketMetric> {

  private final ConcurrentMap<WebSocketBase, WebSocketMetric> webSockets = new ConcurrentHashMap<>();
  public final HttpServer server;

  public FakeHttpServerMetrics(HttpServer server) {
    super(server);
    this.server = server;
  }

  public WebSocketMetric getMetric(ServerWebSocket ws) {
    return webSockets.get(ws);
  }

  @Override
  public Void requestBegin(SocketMetric socketMetric, HttpServerRequest request) {
    return null;
  }

  @Override
  public void responseEnd(Void requestMetric, HttpServerResponse response) {
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
  public SocketMetric connected(SocketAddress remoteAddress) {
    return new SocketMetric(remoteAddress);
  }

  @Override
  public void disconnected(SocketMetric socketMetric, SocketAddress remoteAddress) {
  }

  @Override
  public void bytesRead(SocketMetric socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
  }

  @Override
  public void bytesWritten(SocketMetric socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
  }

  @Override
  public void exceptionOccurred(SocketMetric socketMetric, SocketAddress remoteAddress, Throwable t) {
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void close() {
  }
}
