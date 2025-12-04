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

package io.vertx.core.http.impl;

import io.netty.handler.codec.http2.Http2Error;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.impl.NoStackTraceTimeoutException;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.http.HttpClientRequestInternal;
import io.vertx.core.net.HostAndPort;

import java.util.Objects;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpClientRequestBase implements HttpClientRequestInternal {

  protected final ContextInternal context;
  protected final HttpConnection connection;
  protected final HttpClientStream stream;
  protected final boolean ssl;
  private io.vertx.core.http.HttpMethod method;
  private HostAndPort authority;
  private String uri;
  private String path;
  private String query;
  private final PromiseInternal<HttpClientResponse> responsePromise;
  private Handler<HttpClientRequest> pushHandler;
  private long currentTimeoutTimerId = -1L;
  private long currentTimeoutMs;
  private long lastDataReceived;
  protected Throwable reset;

  HttpClientRequestBase(HttpConnection connection, HttpClientStream stream, PromiseInternal<HttpClientResponse> responsePromise, HttpMethod method, String uri) {
    this.connection = connection;
    this.stream = stream;
    this.responsePromise = responsePromise;
    this.context = responsePromise.context();
    this.uri = uri;
    this.method = method;
    this.authority = stream.connection().authority();
    this.ssl = stream.connection().isSsl();

    //
    stream.pushHandler(this::handlePush);
    stream.headHandler(resp -> {
      HttpClientResponseImpl response = new HttpClientResponseImpl(this, stream.version(), stream, resp.statusCode, resp.statusMessage, resp.headers);
      stream.dataHandler(chunk -> {
        dataReceived();
        response.handleChunk(chunk);
      });
      stream.trailersHandler(response::handleTrailers);
      stream.priorityChangeHandler(response::handlePriorityChange);
      stream.customFrameHandler(response::handleUnknownFrame);
      handleResponse(response);
    });
  }

  protected HostAndPort authority() {
    return authority;
  }

  @Override
  public int streamId() {
    return stream.id();
  }

  @Override
  public Object metric() {
    return stream.metric();
  }

  @Override
  public String absoluteURI() {
    return (ssl ? "https://" : "http://") + authority() + uri;
  }

  public String query() {
    if (query == null) {
      query = HttpUtils.parseQuery(uri);
    }
    return query;
  }

  public String path() {
    if (path == null) {
      path = HttpUtils.parsePath(uri);
    }
    return path;
  }

  public synchronized String getURI() {
    return uri;
  }

  @Override
  public HttpConnection connection() {
    return connection;
  }

  @Override
  public synchronized HttpClientRequest setURI(String uri) {
    Objects.requireNonNull(uri);
    this.uri = uri;
    // invalidate cached values
    this.path = null;
    this.query = null;
    return this;
  }

  @Override
  public synchronized HttpClientRequest authority(HostAndPort authority) {
    this.authority = authority;
    return this;
  }

  @Override
  public synchronized HttpMethod getMethod() {
    return method;
  }

  @Override
  public synchronized HttpClientRequest setMethod(HttpMethod method) {
    Objects.requireNonNull(uri);
    this.method = method;
    return this;
  }

  @Override
  public HttpClientRequest idleTimeout(long timeout) {
    scheduleTimeout(timeout);
    return this;
  }

  protected Throwable mapException(Throwable t) {
    if ((t instanceof HttpClosedException || t instanceof StreamResetException) && reset != null) {
      Throwable cause = reset.getCause();
      t = cause != null ? cause : reset;
    }
    return t;
  }

  void handleException(Throwable t) {
    fail(t);
  }

  void handleClosed() {
    cancelTimeout();
  }

  void fail(Throwable t) {
    responsePromise.tryFail(t);
    HttpClientResponseImpl response = (HttpClientResponseImpl) responsePromise.future().result();
    if (response != null) {
      response.handleException(t);
    }
  }

  void handlePush(HttpClientPush push) {
    HttpClientRequestPushPromise pushReq = new HttpClientRequestPushPromise(connection, push.stream(), push.method(), push.uri(), push.headers());
    if (pushHandler != null) {
      pushHandler.handle(pushReq);
    } else {
      pushReq.reset(Http2Error.CANCEL.code());
    }
  }

  void handleResponse(HttpClientResponse resp) {
    if (reset == null) {
      handleResponse(responsePromise, resp, cancelTimeout());
    }
  }

  abstract void handleResponse(Promise<HttpClientResponse> promise, HttpClientResponse resp, long timeoutMs);

  synchronized void scheduleTimeout(long timeoutMillis) {
    if (timeoutMillis < 0L) {
      throw new IllegalArgumentException();
    }
    long id = currentTimeoutTimerId;
    if (id != -1L && !context.owner().cancelTimer(id)) {
      currentTimeoutMs = 0L;
      currentTimeoutTimerId = -1L;
    } else {
      currentTimeoutMs = timeoutMillis;
      currentTimeoutTimerId = context.setTimer(timeoutMillis, id_ -> {
        synchronized (HttpClientRequestBase.this) {
          currentTimeoutMs = 0L;
          currentTimeoutTimerId = -1L;
        }
        handleTimeout(timeoutMillis);
      });
    }
  }

  private synchronized long cancelTimeout() {
    long id = currentTimeoutTimerId;
    if (id >= 0L && context.owner().cancelTimer(id)) {
      long timeout = currentTimeoutMs;
      currentTimeoutTimerId = -1L;
      currentTimeoutMs = 0L;
      return timeout;
    } else {
      return 0L;
    }
  }

  private void dataReceived() {
    lastDataReceived = System.currentTimeMillis();
  }

  private void handleTimeout(long timeoutMs) {
    NoStackTraceTimeoutException cause;
    if (lastDataReceived > 0) {
      long now = System.currentTimeMillis();
      long timeSinceLastData = now - lastDataReceived;
      long remainingTime = timeoutMs - timeSinceLastData;
      if (remainingTime > 0L) {
        lastDataReceived = 0;
        scheduleTimeout(remainingTime);
        return;
      }
    }
    cause = timeoutEx(timeoutMs, method, authority, uri);
    reset(0x08L, cause);
  }

  static NoStackTraceTimeoutException timeoutEx(long timeoutMs, HttpMethod method, HostAndPort peer, String uri) {
    return new NoStackTraceTimeoutException("The timeout period of " + timeoutMs + "ms has been exceeded while executing " + method + " " + uri + " for server " + peer);
  }

  @Override
  public Future<Void> reset(long code) {
    return reset(code, null);
  }

  @Override
  public Future<Void> reset(long code, Throwable cause) {
    synchronized (this) {
      if (reset != null) {
        return context.failedFuture("Already reset");
      }
      reset = new StreamResetException(code, cause);
    }
    return stream.writeReset(code);
  }

  public Future<Boolean> cancel() {
    synchronized (this) {
      if (reset != null) {
        return context.failedFuture("Already reset");
      }
      reset = new StreamResetException(0x08);
    }
    return stream.cancel();
  }

  @Override
  public Future<HttpClientResponse> response() {
    return responsePromise.future();
  }

  synchronized Handler<HttpClientRequest> pushHandler() {
    return pushHandler;
  }

  @Override
  public synchronized HttpClientRequest pushHandler(Handler<HttpClientRequest> handler) {
    pushHandler = handler;
    return this;
  }
}
