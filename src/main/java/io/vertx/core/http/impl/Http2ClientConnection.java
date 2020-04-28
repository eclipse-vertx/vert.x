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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.net.impl.clientconnection.ConnectionListener;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.*;
import java.util.function.BiConsumer;

import static io.vertx.core.http.HttpHeaders.DEFLATE_GZIP;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2ClientConnection extends Http2ConnectionBase implements HttpClientConnection {

  private final ConnectionListener<HttpClientConnection> listener;
  private final HttpClientImpl client;
  private final ClientMetrics metrics;
  private long expirationTimestamp;

  Http2ClientConnection(ConnectionListener<HttpClientConnection> listener,
                               HttpClientImpl client,
                               ContextInternal context,
                               VertxHttp2ConnectionHandler connHandler,
                               ClientMetrics metrics) {
    super(context, connHandler);
    this.metrics = metrics;
    this.client = client;
    this.listener = listener;
  }

  @Override
  synchronized boolean onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    boolean goneAway = super.onGoAwaySent(lastStreamId, errorCode, debugData);
    if (goneAway) {
      listener.onEvict();
    }
    return goneAway;
  }

  @Override
  synchronized boolean onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    boolean goneAway = super.onGoAwayReceived(lastStreamId, errorCode, debugData);
    if (goneAway) {
      listener.onEvict();
    }
    return goneAway;
  }

  @Override
  protected void concurrencyChanged(long concurrency) {
    int limit = client.getOptions().getHttp2MultiplexingLimit();
    if (limit > 0) {
      concurrency = Math.min(concurrency, limit);
    }
    listener.onConcurrencyChange(concurrency);
  }

  @Override
  public HttpClientMetrics metrics() {
    return client.metrics();
  }

  void upgradeStream(Object metric, HttpClientRequestImpl req, Promise<NetSocket> netSocketPromise, ContextInternal context, Handler<AsyncResult<HttpClientStream>> completionHandler) {
    Future<HttpClientStream> fut;
    synchronized (this) {
      try {
        StreamImpl stream = createStream(context, req, netSocketPromise);
        stream.init(handler.connection().stream(1));
        ((Stream)stream).metric = metric;
        fut = Future.succeededFuture(stream);
      } catch (Exception e) {
        fut = Future.failedFuture(e);
      }
    }
    completionHandler.handle(fut);
  }

  @Override
  public void createStream(ContextInternal context, HttpClientRequestImpl req, Promise<NetSocket> netSocketPromise, Handler<AsyncResult<HttpClientStream>> completionHandler) {
    Future<HttpClientStream> fut;
    synchronized (this) {
      try {
        StreamImpl stream = createStream(context, req, netSocketPromise);
        VertxTracer tracer = context.tracer();
        if (tracer != null) {
          BiConsumer<String, String> headers = (key, val) -> req.headers().add(key, val);
          ((Stream)stream).trace = tracer.sendRequest(context, req, req.method.name(), headers, HttpUtils.CLIENT_REQUEST_TAG_EXTRACTOR);
        }
        fut = Future.succeededFuture(stream);
      } catch (Exception e) {
        fut = Future.failedFuture(e);
      }
    }
    context.dispatch(fut, completionHandler);
  }

  private StreamImpl createStream(ContextInternal context, HttpClientRequestImpl req, Promise<NetSocket> netSocketPromise) {
    return new StreamImpl(this, context, req, netSocketPromise);
  }

  private void recycle() {
    int timeout = client.getOptions().getHttp2KeepAliveTimeout();
    long expired = timeout > 0 ? System.currentTimeMillis() + timeout * 1000 : 0L;
    expirationTimestamp = timeout > 0 ? System.currentTimeMillis() + timeout * 1000 : 0L;
    listener.onRecycle();
  }

  @Override
  public boolean isValid() {
    return expirationTimestamp == 0 || System.currentTimeMillis() <= expirationTimestamp;
  }

  protected synchronized void onHeadersRead(int streamId, Http2Headers headers, StreamPriority streamPriority, boolean endOfStream) {
    Stream stream = (Stream) stream(streamId);
    if (!stream.stream.isTrailersReceived()) {
      stream.onHeaders(headers, streamPriority);
      if (endOfStream) {
        stream.onEnd();
      }
    } else {
      stream.onEnd(new Http2HeadersAdaptor(headers));
    }
  }

  private void metricsEnd(Stream stream) {
    if (metrics != null) {
      metrics.responseEnd(stream.metric, stream.response);
    }
  }

  @Override
  public synchronized void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
    StreamImpl stream = (StreamImpl) stream(streamId);
    if (stream != null) {
      Handler<HttpClientRequest> pushHandler = stream.pushHandler();
      if (pushHandler != null) {
        String rawMethod = headers.method().toString();
        HttpMethod method = HttpMethod.valueOf(rawMethod);
        String uri = headers.path().toString();
        String authority = headers.authority() != null ? headers.authority().toString() : null;
        MultiMap headersMap = new Http2HeadersAdaptor(headers);
        Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
        int pos = authority.indexOf(':');
        int port;
        String host;
        if (pos == -1) {
          host = authority;
          port = 80;
        } else {
          host = authority.substring(0, pos);
          port = Integer.parseInt(authority.substring(pos + 1));
        }
        HttpClientRequestPushPromise pushReq = new HttpClientRequestPushPromise(this, client, isSsl(), method, uri, host, port, headersMap);
        pushReq.getStream().init(promisedStream);
        if (metrics != null) {
          Object metric = metrics.requestBegin(pushReq.uri, pushReq);
          ((Stream)pushReq.getStream()).metric = metric;
          metrics.requestEnd(metric);
        }
        stream.context.emit(pushReq, pushHandler);
        return;
      }
    }

    Http2ClientConnection.this.handler.writeReset(promisedStreamId, Http2Error.CANCEL.code());
  }

  //
  static abstract class Stream extends VertxHttp2Stream<Http2ClientConnection> {

    protected final HttpClientRequestBase request;
    private Promise<NetSocket> netSocketPromise;
    private HttpClientResponseImpl response;
    private Object metric;
    private Object trace;
    private boolean requestEnded;
    private boolean responseEnded;

    Stream(Http2ClientConnection conn, HttpClientRequestBase request, Promise<NetSocket> netSocketPromise, ContextInternal context) {
      super(conn, context);

      this.request = request;
      this.netSocketPromise = netSocketPromise;
    }

    void onContinue() {
      context.dispatch(null, v -> handleContinue());
    }

    void onResponse(HttpClientResponseImpl response) {
      context.schedule(response, this::handleResponse);
    }

    abstract void handleResponse(HttpClientResponseImpl response);
    abstract void handleContinue();

    public Object metric() {
      return metric;
    }

    @Override
    void doWriteHeaders(Http2Headers headers, boolean end, Handler<AsyncResult<Void>> handler) {
      int id = this.conn.handler.encoder().connection().local().lastStreamCreated();
      if (id == 0) {
        id = 1;
      } else {
        id += 2;
      }
      Http2Stream stream;
      try {
        stream = this.conn.handler.encoder().connection().local().createStream(id, false);
      } catch (Http2Exception e) {
        if (handler != null) {
          handler.handle(context.failedFuture(e));
        }
        request.handleException(e);
        return;
      }
      if (conn.metrics != null) {
        metric = conn.metrics.requestBegin(request.uri, request);
      }
      init(stream);
      super.doWriteHeaders(headers, end, handler);
      if (end) {
        endRequest();
      }
    }

    @Override
    void doWriteData(ByteBuf chunk, boolean end, Handler<AsyncResult<Void>> handler) {
      if (end) {
        endRequest();
      }
      super.doWriteData(chunk, end, handler);
    }

    private void endRequest() {
      requestEnded = true;
      if (conn.metrics != null) {
        conn.metrics.requestEnd(metric);
      }
    }

    @Override
    void onEnd(MultiMap trailers) {
      conn.metricsEnd(this);
      responseEnded = true;
      super.onEnd(trailers);
    }

    @Override
    void onReset(long code) {
      if (conn.metrics != null) {
        conn.metrics.requestReset(metric);
      }
      super.onReset(code);
    }

    @Override
    void onHeaders(Http2Headers headers, StreamPriority streamPriority) {
      if (streamPriority != null) {
        priority(streamPriority);
      }
      if (response == null) {
        int status;
        String statusMessage;
        try {
          status = Integer.parseInt(headers.status().toString());
          statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
        } catch (Exception e) {
          handleException(e);
          writeReset(0x01 /* PROTOCOL_ERROR */);
          return;
        }
        if (status == 100) {
          onContinue();
          return;
        }
        headers.remove(":status");
        HttpClientResponseImpl resp = new HttpClientResponseImpl(
          request,
          HttpVersion.HTTP_2,
          (HttpClientStream) this,
          status,
          statusMessage,
          new Http2HeadersAdaptor(headers)
        );
        if (conn.metrics != null) {
          conn.metrics.responseBegin(metric, resp);
        }
        response = resp; // NOT HAPPY ??
        onResponse(resp);
        Promise<NetSocket> promise = netSocketPromise;
        netSocketPromise = null;
        if (promise != null) {
          if (response.statusCode() == 200) {
            NetSocket ns = conn.toNetSocket(this);
            promise.complete(ns);
          } else {
            promise.fail("Server responded with " + response.statusCode() + " code instead of 200");
          }
        }
      }
    }

    @Override
    void onClose() {
      if (netSocketPromise != null) {
        netSocketPromise.fail(ConnectionBase.CLOSED_EXCEPTION);
      }
      if (conn.metrics != null) {
        if (!requestEnded || !responseEnded) {
          conn.metrics.requestReset(metric);
        }
      }
      VertxTracer tracer = context.tracer();
      if (tracer != null) {
        VertxException err;
        if (responseEnded && requestEnded) {
          err = null;
        } else {
          err = ConnectionBase.CLOSED_EXCEPTION;
        }
        tracer.receiveResponse(context, response, trace, err, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
      }
      if (!responseEnded) {
        onError(CLOSED_EXCEPTION);
      }
      super.onClose();
      // commented to be used later when we properly define the HTTP/2 connection expiration from the pool
      // boolean disposable = conn.streams.isEmpty();
      if (request instanceof HttpClientRequestImpl) {
        conn.recycle();
      } /* else {
        conn.listener.onRecycle(0, dispable);
      } */
    }
  }

  static class StreamImpl extends Stream implements HttpClientStream {

    private HttpClientResponseImpl response;

    StreamImpl(Http2ClientConnection conn, ContextInternal context, HttpClientRequestBase request, Promise<NetSocket> netSocketPromise) {
      super(conn, request, netSocketPromise, context);
    }

    @Override
    public StreamPriority priority() {
      return super.priority();
    }

    @Override
    public void updatePriority(StreamPriority streamPriority) {
      super.updatePriority(streamPriority);
    }

    @Override
    public HttpVersion version() {
      return HttpVersion.HTTP_2;
    }

    @Override
    void handleEnd(MultiMap trailers) {
      response.handleEnd(trailers);
    }

    @Override
    void handleData(Buffer buf) {
      response.handleChunk(buf);
    }

    @Override
    void handleReset(long errorCode) {
      handleException(new StreamResetException(errorCode));
    }

    @Override
    void handleClose() {
      super.handleClose();
    }

    @Override
    void handleWritabilityChanged(boolean writable) {
      if (request instanceof HttpClientRequestImpl && writable) {
        ((HttpClientRequestImpl) request).handleDrained();
      }
    }

    @Override
    void handleCustomFrame(HttpFrame frame) {
      if (response != null) {
        response.handleUnknownFrame(frame);
      }
    }


    @Override
    void handlePriorityChange(StreamPriority streamPriority) {
      if (response != null) {
        response.handlePriorityChange(streamPriority);
      }
    }

    void handleContinue() {
      if (request instanceof HttpClientRequestImpl) {
        ((HttpClientRequestImpl)request).handleContinue();
      }
    }

    void handleResponse(HttpClientResponseImpl response) {
      this.response = response;
      request.handleResponse(response);
    }

    void handleException(Throwable exception) {
      request.handleException(exception);
    }

    Handler<HttpClientRequest> pushHandler() {
      return ((HttpClientRequestImpl) request).pushHandler();
    }

    @Override
    public void writeHead(HttpMethod method, String uri, MultiMap headers, String authority, boolean chunked, ByteBuf content, boolean end, StreamPriority priority, Handler<AsyncResult<Void>> handler) {
      Http2Headers h = new DefaultHttp2Headers();
      h.method(method.name());
      boolean e;
      if (method == HttpMethod.CONNECT) {
        if (authority == null) {
          throw new IllegalArgumentException("Missing :authority / host header");
        }
        h.authority(authority);
        // don't end stream for CONNECT
        e = false;
      } else {
        h.path(uri);
        h.scheme(conn.isSsl() ? "https" : "http");
        if (authority != null) {
          h.authority(authority);
        }
        e= end;
      }
      if (headers != null && headers.size() > 0) {
        for (Map.Entry<String, String> header : headers) {
          h.add(HttpUtils.toLowerCase(header.getKey()), header.getValue());
        }
      }
      if (conn.client.getOptions().isTryUseCompression() && h.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
        h.set(HttpHeaderNames.ACCEPT_ENCODING, DEFLATE_GZIP);
      }
      priority(priority);
      conn.context.dispatch(null, v -> {
        if (content != null) {
          writeHeaders(h, false, null);
          writeBuffer(content, e, handler);
        } else {
          writeHeaders(h, e, handler);
        }
      });
    }

    @Override
    public void writeBuffer(ByteBuf buf, boolean end, Handler<AsyncResult<Void>> listener) {
      ByteBuf chunk;
      if (buf == null && end) {
        chunk = Unpooled.EMPTY_BUFFER;
      } else {
        chunk = buf;
      }
      if (chunk != null) {
        writeData(chunk, end, listener);
      }
    }

    @Override
    public ContextInternal getContext() {
      return context;
    }

    @Override
    public void doSetWriteQueueMaxSize(int size) {
    }

    @Override
    public void reset(Throwable cause) {
      long code = cause instanceof StreamResetException ? ((StreamResetException)cause).getCode() : 0;
      conn.context.dispatch(code, this::writeReset);
    }

    @Override
    public HttpClientConnection connection() {
      return conn;
    }
  }

  @Override
  protected void handleIdle() {
    if (handler.connection().local().numActiveStreams() > 0) {
      super.handleIdle();
    }
  }

  public static VertxHttp2ConnectionHandler<Http2ClientConnection> createHttp2ConnectionHandler(
    HttpClientImpl client,
    ClientMetrics metrics,
    ConnectionListener<HttpClientConnection> listener,
    ContextInternal context,
    Object socketMetric,
    BiConsumer<Http2ClientConnection, Long> c) {
    long http2MaxConcurrency = client.getOptions().getHttp2MultiplexingLimit() <= 0 ? Long.MAX_VALUE : client.getOptions().getHttp2MultiplexingLimit();
    HttpClientOptions options = client.getOptions();
    VertxHttp2ConnectionHandler<Http2ClientConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnection>()
      .server(false)
      .useCompression(client.getOptions().isTryUseCompression())
      .gracefulShutdownTimeoutMillis(0) // So client close tests don't hang 30 seconds - make this configurable later but requires HTTP/1 impl
      .initialSettings(client.getOptions().getInitialSettings())
      .connectionFactory(connHandler -> new Http2ClientConnection(listener, client, context, connHandler, metrics))
      .logEnabled(options.getLogActivity())
      .build();
    HttpClientMetrics met = client.metrics();
    handler.addHandler(conn -> {
      if (options.getHttp2ConnectionWindowSize() > 0) {
        conn.setWindowSize(options.getHttp2ConnectionWindowSize());
      }
      if (metrics != null) {
        Object m = socketMetric;
        if (m == null)  {
          m = met.connected(conn.remoteAddress(), conn.remoteName());
          met.endpointConnected(metrics);
        }
        conn.metric(m);
      }
      long concurrency = conn.remoteSettings().getMaxConcurrentStreams();
      if (http2MaxConcurrency > 0) {
        concurrency = Math.min(concurrency, http2MaxConcurrency);
      }
      c.accept(conn, concurrency);
    });
    handler.removeHandler(conn -> {
      if (metrics != null) {
        met.endpointDisconnected(metrics);
      }
      listener.onEvict();
    });
    return handler;
  }
}
