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
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.spi.DatagramSocketMetrics;
import io.vertx.core.metrics.spi.EventBusMetrics;
import io.vertx.core.metrics.spi.HttpClientMetrics;
import io.vertx.core.metrics.spi.HttpServerMetrics;
import io.vertx.core.metrics.spi.NetMetrics;
import io.vertx.core.metrics.spi.VertxMetrics;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;

import java.util.Collections;
import java.util.Map;

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
  public HttpServerMetrics createMetrics(HttpServer server, HttpServerOptions options) {
    return new DummyHttpServerMetrics();
  }

  @Override
  public HttpClientMetrics createMetrics(HttpClient client, HttpClientOptions options) {
    return new DummyHttpClientMetrics();
  }

  @Override
  public NetMetrics createMetrics(NetServer server, NetServerOptions options) {
    return new DummyNetMetrics();
  }

  @Override
  public NetMetrics createMetrics(NetClient client, NetClientOptions options) {
    return new DummyNetMetrics();
  }

  @Override
  public DatagramSocketMetrics createMetrics(DatagramSocket socket, DatagramSocketOptions options) {
    return new DummyDatagramMetrics();
  }

  @Override
  public void close() {
  }

  @Override
  public String baseName() {
    return null;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public String metricBaseName() {
    return null;
  }

  @Override
  public Map<String, JsonObject> metrics() {
    return Collections.EMPTY_MAP;
  }

  class DummyEventBusMetrics implements EventBusMetrics {

    @Override
    public void handlerRegistered(String address) {
    }

    @Override
    public void handlerUnregistered(String address) {
    }

    @Override
    public void messageSent(String address, boolean publish) {
    }

    @Override
    public void messageReceived(String address) {
    }

    @Override
    public void replyFailure(String address, ReplyFailure failure) {
    }

    @Override
    public String baseName() {
      return null;
    }

    @Override
    public boolean isEnabled() {
      return false;
    }

    @Override
    public void close() {
    }
  }

  class DummyHttpServerMetrics implements HttpServerMetrics {

    @Override
    public void requestBegin(HttpServerRequest request, HttpServerResponse response) {
    }

    @Override
    public void responseEnd(HttpServerResponse response) {
    }

    @Override
    public void listening(SocketAddress localAddress) {
    }

    @Override
    public void connected(SocketAddress remoteAddress) {
    }

    @Override
    public void disconnected(SocketAddress remoteAddress) {
    }

    @Override
    public void bytesRead(SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void bytesWritten(SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void exceptionOccurred(SocketAddress remoteAddress, Throwable t) {
    }

    @Override
    public void close() {
    }

    @Override
    public String baseName() {
      return null;
    }

    @Override
    public boolean isEnabled() {
      return false;
    }
  }

  class DummyHttpClientMetrics implements HttpClientMetrics {

    @Override
    public void requestBegin(HttpClientRequest request) {
    }

    @Override
    public void responseEnd(HttpClientRequest request, HttpClientResponse response) {
    }

    @Override
    public void listening(SocketAddress localAddress) {
    }

    @Override
    public void connected(SocketAddress remoteAddress) {
    }

    @Override
    public void disconnected(SocketAddress remoteAddress) {
    }

    @Override
    public void bytesRead(SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void bytesWritten(SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void exceptionOccurred(SocketAddress remoteAddress, Throwable t) {
    }

    @Override
    public void close() {
    }

    @Override
    public String baseName() {
      return null;
    }

    @Override
    public boolean isEnabled() {
      return false;
    }
  }

  class DummyNetMetrics implements NetMetrics {

    @Override
    public void listening(SocketAddress localAddress) {
    }

    @Override
    public void connected(SocketAddress remoteAddress) {
    }

    @Override
    public void disconnected(SocketAddress remoteAddress) {
    }

    @Override
    public void bytesRead(SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void bytesWritten(SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void exceptionOccurred(SocketAddress remoteAddress, Throwable t) {
    }

    @Override
    public void close() {
    }

    @Override
    public String baseName() {
      return null;
    }

    @Override
    public boolean isEnabled() {
      return false;
    }
  }

  class DummyDatagramMetrics implements DatagramSocketMetrics {

    @Override
    public void newSocket() {
    }

    @Override
    public void listening(SocketAddress localAddress) {
    }

    @Override
    public void bytesRead(SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void bytesWritten(SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void exceptionOccurred(SocketAddress remoteAddress, Throwable t) {
    }

    @Override
    public void close() {
    }

    @Override
    public String baseName() {
      return null;
    }

    @Override
    public boolean isEnabled() {
      return false;
    }
  }
}
