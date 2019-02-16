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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.pool.ConnectionListener;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.util.Map;
import java.util.function.BiConsumer;

import static io.vertx.core.http.HttpHeaders.DEFLATE_GZIP;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2ClientConnection extends Http2ConnectionBase implements HttpClientConnection {

  private final ConnectionListener<HttpClientConnection> listener;
  private final HttpClientImpl client;
  final HttpClientMetrics metrics;
  final Object queueMetric;

  public Http2ClientConnection(ConnectionListener<HttpClientConnection> listener,
                               Object queueMetric,
                               HttpClientImpl client,
                               ContextInternal context,
                               VertxHttp2ConnectionHandler connHandler,
                               HttpClientMetrics metrics) {
    super(context, connHandler);
    this.metrics = metrics;
    this.queueMetric = queueMetric;
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
    return metrics;
  }

  @Override
  void onStreamClosed(Http2Stream nettyStream) {
    super.onStreamClosed(nettyStream);
  }

  void upgradeStream(HttpClientRequestImpl req, Handler<AsyncResult<HttpClientStream>> completionHandler) {
    Future<HttpClientStream> fut;
    synchronized (this) {
      try {
        Http2ClientStream stream = createStream(handler.connection().stream(1));
        stream.beginRequest(req);
        fut = Future.succeededFuture(stream);
      } catch (Exception e) {
        fut = Future.failedFuture(e);
      }
    }
    completionHandler.handle(fut);
  }

  @Override
  public void createStream(Handler<AsyncResult<HttpClientStream>> completionHandler) {
    Future<HttpClientStream> fut;
    synchronized (this) {
      Http2Connection conn = handler.connection();
      try {
        int id = conn.local().lastStreamCreated() == 0 ? 1 : conn.local().lastStreamCreated() + 2;
        Http2ClientStream stream = createStream(conn.local().createStream(id, false));
        fut = Future.succeededFuture(stream);
      } catch (Exception e) {
        fut = Future.failedFuture(e);
      }
    }
    completionHandler.handle(fut);
  }

  private Http2ClientStream createStream(Http2Stream stream) {
    boolean writable = handler.encoder().flowController().isWritable(stream);
    Http2ClientStream clientStream = new Http2ClientStream(this, stream, writable);
    streams.put(clientStream.stream.id(), clientStream);
    return clientStream;
  }

  private void recycle() {
    int timeout = client.getOptions().getHttp2KeepAliveTimeout();
    long expired = timeout > 0 ? System.currentTimeMillis() + timeout * 1000 : 0L;
    listener.onRecycle(expired);
  }

  @Override
  public synchronized void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
    Http2ClientStream stream = (Http2ClientStream) streams.get(streamId);
    if (stream != null) {
      StreamPriority streamPriority = new StreamPriority()
        .setDependency(streamDependency)
        .setWeight(weight)
        .setExclusive(exclusive);
      stream.handleHeaders(headers, streamPriority, endOfStream);
    }
  }

  @Override
  public synchronized void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endOfStream) throws Http2Exception {
    Http2ClientStream stream = (Http2ClientStream) streams.get(streamId);
    if (stream != null) {
      stream.handleHeaders(headers, null, endOfStream);
    }
  }

  @Override
  public synchronized void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
    Http2ClientStream stream = (Http2ClientStream) streams.get(streamId);
    if (stream != null) {
      Handler<HttpClientRequest> pushHandler = stream.pushHandler();
      if (pushHandler != null) {
        String rawMethod = headers.method().toString();
        HttpMethod method = HttpUtils.toVertxMethod(rawMethod);
        String uri = headers.path().toString();
        String host = headers.authority() != null ? headers.authority().toString() : null;
        MultiMap headersMap = new Http2HeadersAdaptor(headers);
        Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
        int port = remoteAddress().port();
        HttpClientRequestPushPromise pushReq = new HttpClientRequestPushPromise(this, promisedStream, client, isSsl(), method, rawMethod, uri, host, port, headersMap);
        if (metrics != null) {
          pushReq.metric(metrics.responsePushed(queueMetric, metric(), localAddress(), remoteAddress(), pushReq));
        }
        streams.put(promisedStreamId, pushReq.getStream());
        context.dispatch(pushReq, pushHandler);
        return;
      }
    }
    handler.writeReset(promisedStreamId, Http2Error.CANCEL.code());
  }

  static class Http2ClientStream extends VertxHttp2Stream<Http2ClientConnection> implements HttpClientStream {

    private HttpClientRequestBase request;
    private HttpClientResponseImpl response;
    private boolean requestEnded;
    private boolean responseEnded;

    Http2ClientStream(Http2ClientConnection conn, Http2Stream stream, boolean writable) {
      super(conn, stream, writable);
    }

    Http2ClientStream(Http2ClientConnection conn, HttpClientRequestPushPromise request, Http2Stream stream, boolean writable) {
      super(conn, stream, writable);
      this.request = request;
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
    public int id() {
      return super.id();
    }

    @Override
    void handleEnd(MultiMap trailers) {
      if (conn.metrics != null) {
        if (request.exceptionOccurred != null) {
          conn.metrics.requestReset(request.metric());
        } else {
          conn.metrics.responseEnd(request.metric(), response);
        }
      }
      responseEnded = true;
      // Should use a shared immutable object for CaseInsensitiveHeaders ?
      if (trailers == null) {
        trailers = new CaseInsensitiveHeaders();
      }
      response.handleEnd(trailers);
    }

    @Override
    void handleData(Buffer buf) {
      response.handleChunk(buf);
    }

    @Override
    void handleReset(long errorCode) {
      synchronized (conn) {
        if (responseEnded) {
          return;
        }
        responseEnded = true;
        if (conn.metrics != null) {
          conn.metrics.requestReset(request.metric());
        }
      }
      handleException(new StreamResetException(errorCode));
    }

    @Override
    void handleClose() {
      // commented to be used later when we properly define the HTTP/2 connection expiration from the pool
      // boolean disposable = conn.streams.isEmpty();
      if (request == null || request instanceof HttpClientRequestImpl) {
        conn.recycle();
      } /* else {
        conn.listener.onRecycle(0, dispable);
      } */
      if (!responseEnded) {
        responseEnded = true;
        if (conn.metrics != null) {
          conn.metrics.requestReset(request.metric());
        }
        handleException(CLOSED_EXCEPTION);
      }
    }

    @Override
    void handleInterestedOpsChanged() {
      if (request instanceof HttpClientRequestImpl && !isNotWritable()) {
        if (!isNotWritable()) {
          ((HttpClientRequestImpl) request).handleDrained();
        }
      }
    }

    @Override
    void handleCustomFrame(int type, int flags, Buffer buff) {
      response.handleUnknownFrame(new HttpFrameImpl(type, flags, buff));
    }

    
    @Override
    void handlePriorityChange(StreamPriority streamPriority) {
      if(streamPriority != null && !streamPriority.equals(priority())) {
        priority(streamPriority);
        response.handlePriorityChange(streamPriority);
      }
    }

    void handleHeaders(Http2Headers headers, StreamPriority streamPriority, boolean end) {
      if(streamPriority != null)
        priority(streamPriority);
      if (response == null || response.statusCode() == 100) {
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
        response = new HttpClientResponseImpl(
            request,
            HttpVersion.HTTP_2,
            this,
            status,
            statusMessage,
            new Http2HeadersAdaptor(headers)
        );
        if (conn.metrics != null) {
          conn.metrics.responseBegin(request.metric(), response);
        }
        request.handleResponse(response);
        if (end) {
          onEnd();
        }
      } else if (end) {
        onEnd(new Http2HeadersAdaptor(headers));
      }
    }

    void handleException(Throwable exception) {
      HttpClientRequestBase req;
      HttpClientResponseImpl resp;
      synchronized (conn) {
        req = (!requestEnded || response == null) ? request : null;
        resp = response;
      }
      if (req != null) {
        req.handleException(exception);
      }
      if (resp != null) {
        resp.handleException(exception);
      }
    }

    Handler<HttpClientRequest> pushHandler() {
      return ((HttpClientRequestImpl) request).pushHandler();
    }

    @Override
    public void writeHead(HttpMethod method, String rawMethod, String uri, MultiMap headers, String hostHeader, boolean chunked, ByteBuf content, boolean end, StreamPriority priority) {
      Http2Headers h = new DefaultHttp2Headers();
      h.method(method != HttpMethod.OTHER ? method.name() : rawMethod);
      if (method == HttpMethod.CONNECT) {
        if (hostHeader == null) {
          throw new IllegalArgumentException("Missing :authority / host header");
        }
        h.authority(hostHeader);
      } else {
        h.path(uri);
        h.scheme(conn.isSSL() ? "https" : "http");
        if (hostHeader != null) {
          h.authority(hostHeader);
        }
      }
      if (headers != null && headers.size() > 0) {
        for (Map.Entry<String, String> header : headers) {
          h.add(Http2HeadersAdaptor.toLowerCase(header.getKey()), header.getValue());
        }
      }
      if (conn.client.getOptions().isTryUseCompression() && h.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
        h.set(HttpHeaderNames.ACCEPT_ENCODING, DEFLATE_GZIP);
      }
      if (conn.metrics != null) {
        request.metric(conn.metrics.requestBegin(conn.queueMetric, conn.metric(), conn.localAddress(), conn.remoteAddress(), request));
      }
      priority(priority);
      writeHeaders(h, end && content == null);
      if (content != null) {
        writeBuffer(content, end);
      } else {
        handlerContext.flush();
      }
    }

    @Override
    public void writeBuffer(ByteBuf buf, boolean end) {
      if (buf == null && end) {
        buf = Unpooled.EMPTY_BUFFER;
      }
      if (buf != null) {
        writeData(buf, end);
      }
      if (end) {
        handlerContext.flush();
      }
    }
    
    @Override
    public void writeFrame(int type, int flags, ByteBuf payload) {
      super.writeFrame(type, flags, payload);
    }

    @Override
    public void reportBytesWritten(long numberOfBytes) {
      conn.reportBytesWritten(numberOfBytes);
    }

    @Override
    public void reportBytesRead(long numberOfBytes) {
      conn.reportBytesRead(numberOfBytes);
    }

    @Override
    public ContextInternal getContext() {
      return context;
    }

    @Override
    public void doSetWriteQueueMaxSize(int size) {
    }

    @Override
    public boolean isNotWritable() {
      return super.isNotWritable();
    }

    @Override
    public void beginRequest(HttpClientRequestImpl req) {
      request = req;
    }

    @Override
    public void endRequest() {
      if (conn.metrics != null) {
        conn.metrics.requestEnd(request.metric());
      }
      requestEnded = true;
    }

    @Override
    public void reset(long code) {
      if (request == null) {
        writeReset(code);
      } else {
        if (!(requestEnded && responseEnded)) {
          requestEnded = true;
          responseEnded = true;
          writeReset(code);
          if (conn.metrics != null) {
            conn.metrics.requestReset(request.metric());
          }
        }
      }
    }

    @Override
    public HttpClientConnection connection() {
      return conn;
    }

    @Override
    public NetSocket createNetSocket() {
      return conn.toNetSocket(this);
    }
  }

  @Override
  protected void handleIdle() {
    synchronized (this) {
      if (streams.isEmpty()) {
        return;
      }
    }
    super.handleIdle();
  }

  public static VertxHttp2ConnectionHandler<Http2ClientConnection> createHttp2ConnectionHandler(
    HttpClientImpl client,
    Object queueMetric,
    ConnectionListener<HttpClientConnection> listener,
    ContextInternal context,
    Object socketMetric,
    BiConsumer<Http2ClientConnection, Long> c) {
    long http2MaxConcurrency = client.getOptions().getHttp2MultiplexingLimit() <= 0 ? Long.MAX_VALUE : client.getOptions().getHttp2MultiplexingLimit();
    HttpClientOptions options = client.getOptions();
    HttpClientMetrics metrics = client.metrics();
    VertxHttp2ConnectionHandler<Http2ClientConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnection>()
      .server(false)
      .useCompression(client.getOptions().isTryUseCompression())
      .initialSettings(client.getOptions().getInitialSettings())
      .connectionFactory(connHandler -> new Http2ClientConnection(listener, queueMetric, client, context, connHandler, metrics))
      .logEnabled(options.getLogActivity())
      .build();
    handler.addHandler(conn -> {
      if (options.getHttp2ConnectionWindowSize() > 0) {
        conn.setWindowSize(options.getHttp2ConnectionWindowSize());
      }
      if (metrics != null) {
        Object m = socketMetric;
        if (m == null)  {
          m = metrics.connected(conn.remoteAddress(), conn.remoteName());
          metrics.endpointConnected(queueMetric, m);
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
        metrics.endpointDisconnected(queueMetric, conn.metric());
      }
      listener.onEvict();
    });
    return handler;
  }
}
