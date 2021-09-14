/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.LocalMap;

import java.util.List;
import java.util.function.Function;

public class SharedHttpClient implements HttpClient, Closeable {

  private static final String MAP_NAME = "__vertx.shared.httpClients";
  public static final String DEFAULT_CLIENT_NAME = "SharedHttpClient.DEFAULT";

  private final VertxInternal vertx;
  private final String name;
  private final HttpClient delegate;

  private SharedHttpClient(VertxInternal vertx, String name, HttpClient delegate) {
    this.vertx = vertx;
    this.name = name;
    this.delegate = delegate;
  }

  public static SharedHttpClient create(VertxInternal vertx, String name, HttpClientOptions options) {
    LocalMap<String, HttpClientHolder> localMap = vertx.sharedData().getLocalMap(MAP_NAME);
    HttpClient client;
    HttpClientHolder current, candidate;
    for (; ; ) {
      current = localMap.get(name);
      if (current != null) {
        candidate = current.increment();
        if (candidate != null && localMap.replaceIfPresent(name, current, candidate)) {
          client = candidate.get();
          break;
        }
      } else {
        candidate = new HttpClientHolder();
        if (localMap.putIfAbsent(name, candidate) == null) {
          candidate = candidate.init(vertx, options);
          client = candidate.get();
          localMap.put(name, candidate);
          break;
        }
      }
    }
    return new SharedHttpClient(vertx, name, client);
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    Future<Void> future = close();
    if (handler != null) {
      future.onComplete(handler);
    }
  }

  @Override
  public Future<Void> close() {
    Promise<Void> promise = vertx.promise();
    LocalMap<String, HttpClientHolder> localMap = vertx.sharedData().getLocalMap(MAP_NAME);
    HttpClientHolder current, candidate;
    for (; ; ) {
      current = localMap.get(name);
      candidate = current.decrement();
      if (candidate == null) {
        if (localMap.removeIfPresent(name, current)) {
          current.close().onComplete(promise);
          break;
        }
      } else if (localMap.replace(name, current, candidate)) {
        promise.complete();
        break;
      }
    }
    return promise.future();
  }

  @Override
  public void close(Promise<Void> completion) {
    close().onComplete(completion);
  }

  @Override
  protected void finalize() throws Throwable {
    // Make sure the shared client count gets decreased if there are no more references to this instance
    close();
    super.finalize();
  }

  @Override
  public void request(RequestOptions options, Handler<AsyncResult<HttpClientRequest>> handler) {
    delegate.request(options, handler);
  }

  @Override
  public Future<HttpClientRequest> request(RequestOptions options) {
    return delegate.request(options);
  }

  @Override
  public void request(HttpMethod method, int port, String host, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler) {
    delegate.request(method, port, host, requestURI, handler);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, int port, String host, String requestURI) {
    return delegate.request(method, port, host, requestURI);
  }

  @Override
  public void request(HttpMethod method, String host, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler) {
    delegate.request(method, host, requestURI, handler);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String host, String requestURI) {
    return delegate.request(method, host, requestURI);
  }

  @Override
  public void request(HttpMethod method, String requestURI, Handler<AsyncResult<HttpClientRequest>> handler) {
    delegate.request(method, requestURI, handler);
  }

  @Override
  public Future<HttpClientRequest> request(HttpMethod method, String requestURI) {
    return delegate.request(method, requestURI);
  }

  @Override
  public void webSocket(int port, String host, String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    delegate.webSocket(port, host, requestURI, handler);
  }

  @Override
  public Future<WebSocket> webSocket(int port, String host, String requestURI) {
    return delegate.webSocket(port, host, requestURI);
  }

  @Override
  public void webSocket(String host, String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    delegate.webSocket(host, requestURI, handler);
  }

  @Override
  public Future<WebSocket> webSocket(String host, String requestURI) {
    return delegate.webSocket(host, requestURI);
  }

  @Override
  public void webSocket(String requestURI, Handler<AsyncResult<WebSocket>> handler) {
    delegate.webSocket(requestURI, handler);
  }

  @Override
  public Future<WebSocket> webSocket(String requestURI) {
    return delegate.webSocket(requestURI);
  }

  @Override
  public void webSocket(WebSocketConnectOptions options, Handler<AsyncResult<WebSocket>> handler) {
    delegate.webSocket(options, handler);
  }

  @Override
  public Future<WebSocket> webSocket(WebSocketConnectOptions options) {
    return delegate.webSocket(options);
  }

  @Override
  public void webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols, Handler<AsyncResult<WebSocket>> handler) {
    delegate.webSocketAbs(url, headers, version, subProtocols, handler);
  }

  @Override
  public Future<WebSocket> webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols) {
    return delegate.webSocketAbs(url, headers, version, subProtocols);
  }

  @Override
  public HttpClient connectionHandler(Handler<HttpConnection> handler) {
    return delegate.connectionHandler(handler);
  }

  @Override
  public HttpClient redirectHandler(Function<HttpClientResponse, Future<RequestOptions>> handler) {
    return delegate.redirectHandler(handler);
  }

  @Override
  public Function<HttpClientResponse, Future<RequestOptions>> redirectHandler() {
    return delegate.redirectHandler();
  }
}
