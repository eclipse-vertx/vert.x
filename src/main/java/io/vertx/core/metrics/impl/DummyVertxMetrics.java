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
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocket;
import io.vertx.core.spi.metrics.DatagramSocketMetrics;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DummyVertxMetrics implements VertxMetrics {

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
    return new DummyEventBusMetrics();
  }

  @Override
  public HttpServerMetrics createMetrics(HttpServer server, SocketAddress localAddress, HttpServerOptions options) {
    return new DummyHttpServerMetrics();
  }

  @Override
  public HttpClientMetrics createMetrics(HttpClient client, HttpClientOptions options) {
    return new DummyHttpClientMetrics();
  }

  @Override
  public TCPMetrics createMetrics(NetServer server, SocketAddress localAddress, NetServerOptions options) {
    return new DummyTCPMetrics();
  }

  @Override
  public TCPMetrics createMetrics(NetClient client, NetClientOptions options) {
    return new DummyTCPMetrics();
  }

  @Override
  public DatagramSocketMetrics createMetrics(DatagramSocket socket, DatagramSocketOptions options) {
    return new DummyDatagramMetrics();
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

  protected class DummyEventBusMetrics implements EventBusMetrics<Void> {

    @Override
    public void messageWritten(String address, int numberOfBytes) {
    }

    @Override
    public void messageRead(String address, int numberOfBytes) {
    }

    @Override
    public Void handlerRegistered(String address, boolean replyHandler) {
      return null;
    }

    @Override
    public void handlerUnregistered(Void handler) {
    }

    @Override
    public void beginHandleMessage(Void handler, boolean local) {
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

  protected class DummyHttpServerMetrics implements HttpServerMetrics<Void, Void, Void> {

    @Override
    public Void requestBegin(Void socketMetric, HttpServerRequest request) {
      return null;
    }

    @Override
    public void responseEnd(Void requestMetric, HttpServerResponse response) {
    }

    @Override
    public Void connected(SocketAddress remoteAddress) {
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

  protected class DummyHttpClientMetrics implements HttpClientMetrics<Void, Void, Void> {

    @Override
    public Void requestBegin(Void socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
      return null;
    }

    @Override
    public void responseEnd(Void requestMetric, HttpClientResponse response) {
    }

    @Override
    public Void connected(SocketAddress remoteAddress) {
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
    public Void connected(Void socketMetric, WebSocket webSocket) {
      return null;
    }

    @Override
    public void disconnected(Void webSocketMetric) {
    }
  }

  protected class DummyTCPMetrics implements TCPMetrics<Void> {

    @Override
    public Void connected(SocketAddress remoteAddress) {
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

  protected class DummyDatagramMetrics implements DatagramSocketMetrics {

    @Override
    public void listening(SocketAddress localAddress) {
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
}
