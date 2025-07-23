/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An un-pooled HTTP client connection that maintains a queue for pending requests that cannot be served
 * by the actual connection.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class UnpooledHttpClientConnection implements io.vertx.core.http.HttpClientConnection {

  private final HttpClientConnection actual;
  private final Deque<PromiseInternal<HttpClientStream>> pending;
  private long concurrency;
  private long inflight;

  public UnpooledHttpClientConnection(HttpClientConnection actual) {
    this.actual = actual;
    this.concurrency = actual.concurrency();
    this.pending = new ArrayDeque<>();
  }

  UnpooledHttpClientConnection init() {
    actual.evictionHandler(v -> {
      // Ignore
    });
    actual.concurrencyChangeHandler(val -> {
      synchronized (UnpooledHttpClientConnection.this) {
        concurrency = val;
      }
      checkPending(null);
    });
    return this;
  }

  @Override
  public long activeStreams() {
    return actual.activeStreams();
  }

  @Override
  public synchronized long maxActiveStreams() {
    return concurrency;
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    return actual.shutdown(timeout, unit);
  }

  @Override
  public int getWindowSize() {
    return actual.getWindowSize();
  }

  @Override
  @Fluent
  public HttpConnection setWindowSize(int windowSize) {
    return actual.setWindowSize(windowSize);
  }

  @Override
  @Fluent
  public HttpConnection goAway(long errorCode) {
    return actual.goAway(errorCode);
  }

  @Override
  @Fluent
  public HttpConnection goAway(long errorCode, int lastStreamId) {
    return actual.goAway(errorCode, lastStreamId);
  }

  @Override
  @Fluent
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    return actual.goAway(errorCode, lastStreamId, debugData);
  }

  @Override
  @Fluent
  public HttpConnection goAwayHandler(@Nullable Handler<GoAway> handler) {
    return actual.goAwayHandler(handler);
  }

  @Override
  @Fluent
  public HttpConnection shutdownHandler(@Nullable Handler<Void> handler) {
    return actual.shutdownHandler(handler);
  }

  @Override
  @Fluent
  public HttpConnection closeHandler(Handler<Void> handler) {
    return actual.closeHandler(handler);
  }

  @Override
  public Http3Settings http3Settings() {
    return actual.http3Settings();
  }

  @Override
  public Future<Void> updateHttp3Settings(Http3Settings settings) {
    return actual.updateHttp3Settings(settings);
  }

  @Override
  public Http3Settings remoteHttp3Settings() {
    return actual.remoteHttp3Settings();
  }

  @Override
  public HttpConnection remoteHttp3SettingsHandler(Handler<Http3Settings> handler) {
    return actual.remoteHttp3SettingsHandler(handler);
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    return actual.ping(data);
  }

  @Override
  @Fluent
  public HttpConnection pingHandler(@Nullable Handler<Buffer> handler) {
    return actual.pingHandler(handler);
  }

  @Override
  @Fluent
  public HttpConnection exceptionHandler(Handler<Throwable> handler) {
    return actual.exceptionHandler(handler);
  }

  @Override
  @CacheReturn
  public SocketAddress remoteAddress() {
    return actual.remoteAddress();
  }

  @Override
  public SocketAddress remoteAddress(boolean real) {
    return actual.remoteAddress(real);
  }

  @Override
  @CacheReturn
  public SocketAddress localAddress() {
    return actual.localAddress();
  }

  @Override
  public SocketAddress localAddress(boolean real) {
    return actual.localAddress(real);
  }

  @Override
  public boolean isSsl() {
    return actual.isSsl();
  }

  @Override
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  public SSLSession sslSession() {
    return actual.sslSession();
  }

  @Override
  @GenIgnore
  public List<Certificate> peerCertificates() throws SSLPeerUnverifiedException {
    return actual.peerCertificates();
  }

  @Override
  public String indicatedServerName() {
    return actual.indicatedServerName();
  }

  /**
   * Create an HTTP stream.
   *
   * @param context the stream context
   * @return a future notified with the created request
   */
  public Future<HttpClientRequest> request(ContextInternal context, RequestOptions options) {
    Future<HttpClientStream> future;
    synchronized (this) {
      if (inflight >= concurrency) {
        PromiseInternal<HttpClientStream> promise = context.promise();
        pending.add(promise);
        future = promise.future();
      } else {
        inflight++;
        future = actual.createStream(context);
      }
    }
    return future.map(stream -> {
      HttpClientRequestImpl request = new HttpClientRequestImpl(this, stream);
      stream.closeHandler(this::checkPending);
      if (options != null) {
        request.init(options);
      }
      return request;
    });
  }

  private void checkPending(Void v) {
    PromiseInternal<HttpClientStream> promise;
    synchronized (this) {
      if (--inflight >= concurrency || (promise = pending.poll()) == null) {
        return;
      }
      inflight++;
    }
    actual.createStream(promise.context()).onComplete(promise);
  }

  @Override
  public Future<HttpClientRequest> request(RequestOptions options) {
    ContextInternal ctx = actual.context().owner().getOrCreateContext();
    return request(ctx, options);
  }

  @Override
  public Http2Settings settings() {
    return actual.settings();
  }

  @Override
  public Future<Void> updateSettings(Http2Settings settings) {
    return actual.updateSettings(settings);
  }

  @Override
  public Http2Settings remoteSettings() {
    return actual.remoteSettings();
  }

  @Override
  public HttpConnection remoteSettingsHandler(Handler<Http2Settings> handler) {
    return actual.remoteSettingsHandler(handler);
  }
}
