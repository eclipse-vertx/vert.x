/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.metrics.impl;

import io.vertx.core.Verticle;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocket;
import io.vertx.core.spi.metrics.*;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DummyVertxMetrics implements VertxMetrics {

  public static final DummyVertxMetrics INSTANCE = new DummyVertxMetrics();

  @Override
  public void verticleDeployed(Verticle verticle) {
  }

  @Override
  public void verticleUndeployed(Verticle verticle) {
  }

  @Override
  public void timerCreated(long id) {
  }

  @Override
  public void timerEnded(long id, boolean cancelled) {
  }

  @Override
  public EventBusMetrics createMetrics(EventBus eventBus) {
    return DummyEventBusMetrics.INSTANCE;
  }

  @Override
  public HttpServerMetrics createMetrics(HttpServer server, SocketAddress localAddress, HttpServerOptions options) {
    return DummyHttpServerMetrics.INSTANCE;
  }

  @Override
  public HttpClientMetrics createMetrics(HttpClient client, HttpClientOptions options) {
    return DummyHttpClientMetrics.INSTANCE;
  }

  @Override
  public TCPMetrics createMetrics(SocketAddress localAddress, NetServerOptions options) {
    return DummyTCPMetrics.INSTANCE;
  }

  @Override
  public TCPMetrics createMetrics(NetClientOptions options) {
    return DummyTCPMetrics.INSTANCE;
  }

  @Override
  public DatagramSocketMetrics createMetrics(DatagramSocket socket, DatagramSocketOptions options) {
    return DummyDatagramMetrics.INSTANCE;
  }

  @Override
  public <P> PoolMetrics<?> createMetrics(P pool, String poolType, String poolName, int maxPoolSize) {
    return DummyWorkerPoolMetrics.INSTANCE;
  }

  @Override
  public void close() {
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public boolean isMetricsEnabled() {
    return false;
  }

  public static class DummyEventBusMetrics implements EventBusMetrics<Void> {

    public static final DummyEventBusMetrics INSTANCE = new DummyEventBusMetrics();

    @Override
    public void messageWritten(String address, int numberOfBytes) {
    }

    @Override
    public void messageRead(String address, int numberOfBytes) {
    }

    @Override
    public Void handlerRegistered(String address, String repliedAddress) {
      return null;
    }

    @Override
    public void handlerUnregistered(Void handler) {
    }

    @Override
    public void beginHandleMessage(Void handler, boolean local) {
    }

    @Override
    public void scheduleMessage(Void handler, boolean local) {
    }

    @Override
    public void endHandleMessage(Void handler, Throwable failure) {
    }

    @Override
    public void messageSent(String address, boolean publish, boolean local, boolean remote) {
    }

    @Override
    public void messageReceived(String address, boolean publish, boolean local, int handlers) {
    }

    @Override
    public void replyFailure(String address, ReplyFailure failure) {
    }

    @Override
    public boolean isEnabled() {
      return false;
    }

    @Override
    public void close() {
    }
  }

  public static class DummyHttpServerMetrics implements HttpServerMetrics<Void, Void, Void> {

    public static final DummyHttpServerMetrics INSTANCE = new DummyHttpServerMetrics();

    @Override
    public Void requestBegin(Void socketMetric, HttpServerRequest request) {
      return null;
    }

    @Override
    public void requestReset(Void requestMetric) {
    }

    @Override
    public Void responsePushed(Void socketMetric, HttpMethod method, String uri, HttpServerResponse response) {
      return null;
    }

    @Override
    public void responseEnd(Void requestMetric, HttpServerResponse response) {
    }

    @Override
    public Void upgrade(Void requestMetric, ServerWebSocket serverWebSocket) {
      return null;
    }

    @Override
    public Void connected(SocketAddress remoteAddress, String remoteName) {
      return null;
    }

    @Override
    public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
    }

    @Override
    public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void exceptionOccurred(Void socketMetric, SocketAddress remoteAddress, Throwable t) {
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isEnabled() {
      return false;
    }

    @Override
    public Void connected(Void socketMetric, ServerWebSocket serverWebSocket) {
      return null;
    }

    @Override
    public void disconnected(Void serverWebSocketMetric) {
    }
  }

  public static class DummyHttpClientMetrics implements HttpClientMetrics<Void, Void, Void, Void, Void> {

    public static final DummyHttpClientMetrics INSTANCE = new DummyHttpClientMetrics();

    @Override
    public Void createEndpoint(String host, int port, int maxPoolSize) {
      return null;
    }

    @Override
    public Void enqueueRequest(Void endpointMetric) {
      return null;
    }

    @Override
    public void dequeueRequest(Void endpointMetric, Void taskMetric) {
    }

    @Override
    public void closeEndpoint(String host, int port, Void endpointMetric) {
    }

    @Override
    public void endpointConnected(Void endpointMetric, Void socketMetric) {
    }

    @Override
    public Void connected(SocketAddress remoteAddress, String remoteName) {
      return null;
    }

    @Override
    public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
    }

    @Override
    public void endpointDisconnected(Void endpointMetric, Void socketMetric) {
    }

    @Override
    public Void requestBegin(Void endpointMetric, Void socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
      return null;
    }

    @Override
    public void requestEnd(Void requestMetric) {
    }

    @Override
    public void responseBegin(Void requestMetric, HttpClientResponse response) {
    }

    @Override
    public Void responsePushed(Void endpointMetric, Void socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
      return null;
    }

    @Override
    public void requestReset(Void requestMetric) {
    }

    @Override
    public void responseEnd(Void requestMetric, HttpClientResponse response) {
    }

    @Override
    public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void exceptionOccurred(Void socketMetric, SocketAddress remoteAddress, Throwable t) {
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isEnabled() {
      return false;
    }

    @Override
    public Void connected(Void endpointMetric, Void socketMetric, WebSocket webSocket) {
      return null;
    }

    @Override
    public void disconnected(Void webSocketMetric) {
    }
  }

  public static class DummyTCPMetrics implements TCPMetrics<Void> {

    public static final DummyTCPMetrics INSTANCE = new DummyTCPMetrics();

    @Override
    public Void connected(SocketAddress remoteAddress, String remoteName) {
      return null;
    }

    @Override
    public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
    }

    @Override
    public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void exceptionOccurred(Void socketMetric, SocketAddress remoteAddress, Throwable t) {
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isEnabled() {
      return false;
    }
  }

  public static class DummyDatagramMetrics implements DatagramSocketMetrics {

    public static final DummyDatagramMetrics INSTANCE = new DummyDatagramMetrics();

    @Override
    public void listening(String localName, SocketAddress localAddress) {
    }

    @Override
    public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void exceptionOccurred(Void socketMetric, SocketAddress remoteAddress, Throwable t) {
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isEnabled() {
      return false;
    }
  }

  public static class DummyWorkerPoolMetrics implements PoolMetrics<Void> {

    public static final DummyWorkerPoolMetrics INSTANCE = new DummyWorkerPoolMetrics();

    @Override
    public Void submitted() {
      return null;
    }

    @Override
    public void rejected(Void t) {
    }

    @Override
    public Void begin(Void t) {
      return t;
    }

    public void end(Void t, boolean succeeded) {
    }

    @Override
    public boolean isEnabled() {
      return false;
    }

    @Override
    public void close() {
    }
  }
}