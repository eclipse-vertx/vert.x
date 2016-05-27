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

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeHttpClientMetrics extends FakeMetricsBase implements HttpClientMetrics<HttpClientMetric, WebSocketMetric, SocketMetric, AtomicInteger> {

  private final String name;
  private final ConcurrentMap<WebSocketBase, WebSocketMetric> webSockets = new ConcurrentHashMap<>();
  private final ConcurrentMap<HttpClientRequest, HttpClientMetric> requests = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicInteger> queues = new ConcurrentHashMap<>();

  public FakeHttpClientMetrics(Measured measured, String name) {
    super(measured);
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

  public Set<String> queueNames() {
    return new HashSet<>(queues.keySet());
  }

  public Integer queue(String name) {
    AtomicInteger queue = queues.get(name);
    return queue != null ? queue.get() : null;
  }

  @Override
  public AtomicInteger createQueue(String host, int port) {
    AtomicInteger metric = new AtomicInteger();
    queues.put(host + ":" + port, metric);
    return metric;
  }

  @Override
  public void enqueue(AtomicInteger queueMetric) {
    queueMetric.incrementAndGet();
  }

  @Override
  public void dequeue(AtomicInteger queueMetric) {
    queueMetric.decrementAndGet();
  }

  @Override
  public void closeQueue(String host, int port, AtomicInteger queueMetric) {
    queues.remove(host + ":" + port);
  }

  @Override
  public WebSocketMetric connected(SocketMetric socketMetric, WebSocket webSocket) {
    WebSocketMetric metric = new WebSocketMetric(socketMetric, webSocket);
    webSockets.put(webSocket, metric);
    return metric;
  }

  @Override
  public void disconnected(WebSocketMetric webSocketMetric) {
    webSockets.remove(webSocketMetric.ws);
  }

  @Override
  public HttpClientMetric requestBegin(SocketMetric socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
    HttpClientMetric metric = new HttpClientMetric(request, socketMetric);
    requests.put(request, metric);
    return metric;
  }

  @Override
  public HttpClientMetric responsePushed(SocketMetric socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
    HttpClientMetric metric = new HttpClientMetric(request, socketMetric);
    requests.put(request, metric);
    return metric;
  }

  @Override
  public void requestReset(HttpClientMetric requestMetric) {
    requestMetric.failed.set(true);
    requests.remove(requestMetric.request);
  }

  @Override
  public void responseEnd(HttpClientMetric requestMetric, HttpClientResponse response) {
    requests.remove(requestMetric.request);
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
