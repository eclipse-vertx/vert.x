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

package io.vertx.core.http.impl;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.concurrent.TimeoutException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class HttpClientRequestBase implements HttpClientRequest {

  private static final Logger log = LoggerFactory.getLogger(HttpClientRequestImpl.class);

  protected final HttpClientImpl client;
  protected final io.vertx.core.http.HttpMethod method;
  protected final String uri;
  protected final String path;
  protected final String host;
  protected final int port;
  protected final String query;
  protected final boolean ssl;
  private Handler<Throwable> exceptionHandler;
  private long currentTimeoutTimerId = -1;
  private long currentTimeoutMs;
  private long lastDataReceived;
  protected Throwable exceptionOccurred;
  private Object metric;

  HttpClientRequestBase(HttpClientImpl client, boolean ssl, HttpMethod method, String host, int port, String uri) {
    this.client = client;
    this.uri = uri;
    this.method = method;
    this.host = host;
    this.port = port;
    this.path = uri.length() > 0 ? HttpUtils.parsePath(uri) : "";
    this.query = HttpUtils.parseQuery(uri);
    this.ssl = ssl;
  }

  Object metric() {
    return metric;
  }

  void metric(Object metric) {
    this.metric = metric;
  }

  protected abstract Object getLock();
  protected abstract void doHandleResponse(HttpClientResponseImpl resp, long timeoutMs);
  protected abstract void checkComplete();

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

  @Override
  public io.vertx.core.http.HttpMethod method() {
    return method;
  }

  @Override
  public HttpClientRequest exceptionHandler(Handler<Throwable> handler) {
    synchronized (getLock()) {
      if (handler != null) {
        checkComplete();
        this.exceptionHandler = handler;
      } else {
        this.exceptionHandler = null;
      }
      return this;
    }
  }

  Handler<Throwable> exceptionHandler() {
    synchronized (getLock()) {
      return exceptionHandler;
    }
  }

  @Override
  public HttpClientRequest setTimeout(long timeoutMs) {
    synchronized (getLock()) {
      cancelOutstandingTimeoutTimer();
      currentTimeoutMs = timeoutMs;
      currentTimeoutTimerId = client.getVertx().setTimer(timeoutMs, id -> handleTimeout(timeoutMs));
      return this;
    }
  }

  public void handleException(Throwable t) {
    synchronized (getLock()) {
      cancelOutstandingTimeoutTimer();
      exceptionOccurred = t;
      if (exceptionHandler != null) {
        exceptionHandler.handle(t);
      } else {
        log.error(t);
      }
    }
  }

  void handleResponse(HttpClientResponseImpl resp) {
    synchronized (getLock()) {
      // If an exception occurred (e.g. a timeout fired) we won't receive the response.
      if (exceptionOccurred == null) {
        long timeoutMS = currentTimeoutMs;
        cancelOutstandingTimeoutTimer();
        try {
          doHandleResponse(resp, timeoutMS);
        } catch (Throwable t) {
          handleException(t);
        }
      }
    }
  }

  private void cancelOutstandingTimeoutTimer() {
    if (currentTimeoutTimerId != -1) {
      client.getVertx().cancelTimer(currentTimeoutTimerId);
      currentTimeoutTimerId = -1;
      currentTimeoutMs = 0;
    }
  }

  private void handleTimeout(long timeoutMs) {
    if (lastDataReceived == 0) {
      timeout(timeoutMs);
    } else {
      long now = System.currentTimeMillis();
      long timeSinceLastData = now - lastDataReceived;
      if (timeSinceLastData >= timeoutMs) {
        timeout(timeoutMs);
      } else {
        // reschedule
        lastDataReceived = 0;
        setTimeout(timeoutMs - timeSinceLastData);
      }
    }
  }

  private void timeout(long timeoutMs) {
    handleException(new TimeoutException("The timeout period of " + timeoutMs + "ms has been exceeded while executing " + method + " " + uri + " for host " + host));
  }

  void handleResponseEnd() {
  }

  void dataReceived() {
    synchronized (getLock()) {
      if (currentTimeoutTimerId != -1) {
        lastDataReceived = System.currentTimeMillis();
      }
    }
  }
}
