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

package io.vertx.core.http.impl;

import io.vertx.core.Promise;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpClientRequestBase implements HttpClientRequest {

  private static final Logger log = LoggerFactory.getLogger(HttpClientRequestImpl.class);

  protected final HttpClientImpl client;
  protected final io.vertx.core.http.HttpMethod method;
  protected final String uri;
  protected final String path;
  protected final String query;
  protected final String host;
  protected final int port;
  protected final SocketAddress server;
  protected final boolean ssl;
  private Handler<Throwable> exceptionHandler;
  private long currentTimeoutTimerId = -1;
  private long currentTimeoutMs;
  private long lastDataReceived;
  protected Throwable exceptionOccurred;
  private boolean paused;
  private HttpClientResponse response;

  HttpClientRequestBase(HttpClientImpl client, boolean ssl, HttpMethod method, SocketAddress server, String host, int port, String uri) {
    this.client = client;
    this.uri = uri;
    this.method = method;
    this.server = server;
    this.host = host;
    this.port = port;
    this.path = uri.length() > 0 ? HttpUtils.parsePath(uri) : "";
    this.query = HttpUtils.parseQuery(uri);
    this.ssl = ssl;
  }

  protected void checkEnded() {
  }

  protected String hostHeader() {
    if ((port == 80 && !ssl) || (port == 443 && ssl)) {
      return host;
    } else {
      return host + ':' + port;
    }
  }

  @Override
  public String absoluteURI() {
    return (ssl ? "https://" : "http://") + hostHeader() + uri;
  }

  public String query() {
    return query;
  }

  public String path() {
    return path;
  }

  public String uri() {
    return uri;
  }

  public String host() {
    return server.host();
  }

  @Override
  public HttpMethod method() {
    return method;
  }

  @Override
  public synchronized HttpClientRequest exceptionHandler(Handler<Throwable> handler) {
    if (handler != null) {
      checkEnded();
      this.exceptionHandler = handler;
    } else {
      this.exceptionHandler = null;
    }
    return this;
  }

  synchronized Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  @Override
  public synchronized HttpClientRequest setTimeout(long timeoutMs) {
    cancelTimeout();
    currentTimeoutMs = timeoutMs;
    currentTimeoutTimerId = client.getVertx().setTimer(timeoutMs, id -> handleTimeout(timeoutMs));
    return this;
  }

  public void handleException(Throwable t) {
    Handler<Throwable> handler;
    synchronized (this) {
      cancelTimeout();
      exceptionOccurred = t;
      if (exceptionHandler != null) {
        handler = exceptionHandler;
      } else {
        handler = log::error;
      }
    }
    handler.handle(t);
  }

  void handleResponse(HttpClientResponse resp) {
    synchronized (this) {
      response = resp;
    }
    checkHandleResponse();
  }

  private void checkHandleResponse() {
    long timeoutMS;
    HttpClientResponse resp;
    synchronized (this) {
      if (response != null) {
        if (paused) {
          return;
        }
        timeoutMS = cancelTimeout();
        resp = response;
        response = null;
      } else {
        return;
      }
    }
    try {
      handleResponse(resp, timeoutMS);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  abstract void handleResponse(HttpClientResponse resp, long timeoutMs);

  private synchronized long cancelTimeout() {
    long ret;
    if ((ret = currentTimeoutTimerId) != -1) {
      client.getVertx().cancelTimer(currentTimeoutTimerId);
      currentTimeoutTimerId = -1;
      ret = currentTimeoutMs;
      currentTimeoutMs = 0;
    }
    return ret;
  }

  private void handleTimeout(long timeoutMs) {
    synchronized (this) {
      if (lastDataReceived > 0) {
        long now = System.currentTimeMillis();
        long timeSinceLastData = now - lastDataReceived;
        if (timeSinceLastData < timeoutMs) {
          // reschedule
          lastDataReceived = 0;
          setTimeout(timeoutMs - timeSinceLastData);
          return;
        }
      }
    }
    String msg = "The timeout period of " + timeoutMs + "ms has been exceeded while executing " + method + " " + uri + " for server " + server;
    reset(new NoStackTraceTimeoutException(msg));
  }

  synchronized void dataReceived() {
    if (currentTimeoutTimerId != -1) {
      lastDataReceived = System.currentTimeMillis();
    }
  }

  @Override
  public boolean reset(long code) {
    return reset(new StreamResetException(code));
  }

  abstract boolean reset(Throwable cause);

  @Override
  public HttpClientRequest pause() {
    paused = true;
    return this;
  }

  @Override
  public HttpClientRequest resume() {
    synchronized (this) {
      if (paused) {
        paused = false;
      } else {
        return this;
      }
    }
    checkHandleResponse();
    return this;
  }

  @Override
  public synchronized HttpClientRequest fetch(long amount) {
    if (amount < 0L) {
      throw new IllegalArgumentException();
    }
    if (amount > 0L) {
      resume();
    }
    return this;
  }

}
