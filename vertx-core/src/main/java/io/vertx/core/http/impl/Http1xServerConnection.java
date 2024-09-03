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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.MessageWrite;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static io.vertx.core.spi.metrics.Metrics.*;

/**
 *
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 * </p>
 * The connection maintains two fields for tracking requests:
 * <ul>
 *   <li>{@link #requestInProgress} is the request currently receiving messages, the field is set when
 *   a {@link HttpRequest} message is received and unset when {@link LastHttpContent} is received. Intermediate
 *   {@link HttpContent} messages are processed by the request.</li>
 *   <li>{@link #responseInProgress} is the request for which the response is currently being sent. This field
 *   is set when it is {@code null} and the {@link #requestInProgress} field if set, or when there is a pipelined
 *   request waiting its turn for writing the response</li>
 * </ul>
 * <p/>
 * When a request is received, it is also the current response if there is no response in progress, otherwise it is
 * queued and will become the response in progress when the current response in progress ends.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Http1xServerConnection extends Http1xConnection implements HttpServerConnection {

  private final String serverOrigin;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final TracingPolicy tracingPolicy;

  private Http1xServerRequest requestInProgress;
  private Http1xServerRequest responseInProgress;
  private boolean wantClose;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<HttpServerRequest> invalidRequestHandler;

  final HttpServerMetrics metrics;
  final boolean handle100ContinueAutomatically;
  final HttpServerOptions options;
  final SslContextManager sslContextManager;

  public Http1xServerConnection(Supplier<ContextInternal> streamContextSupplier,
                                SslContextManager sslContextManager,
                                HttpServerOptions options,
                                ChannelHandlerContext chctx,
                                ContextInternal context,
                                String serverOrigin,
                                HttpServerMetrics metrics) {
    super(context, chctx);
    this.serverOrigin = serverOrigin;
    this.streamContextSupplier = streamContextSupplier;
    this.options = options;
    this.sslContextManager = sslContextManager;
    this.metrics = metrics;
    this.handle100ContinueAutomatically = options.isHandle100ContinueAutomatically();
    this.tracingPolicy = options.getTracingPolicy();
    this.wantClose = false;
  }

  @Override
  protected void handleShutdown(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
    super.handleShutdown(reason, timeout, unit, promise);
    if (responseInProgress != null) {
    } else {
      closeInternal();
    }
  }

  TracingPolicy tracingPolicy() {
    return tracingPolicy;
  }

  @Override
  public HttpServerConnection handler(Handler<HttpServerRequest> handler) {
    requestHandler = handler;
    return this;
  }

  @Override
  public HttpServerConnection invalidRequestHandler(Handler<HttpServerRequest> handler) {
    invalidRequestHandler = handler;
    return this;
  }

  @Override
  public HttpServerMetrics metrics() {
    return metrics;
  }

  public void handleMessage(Object msg) {
    assert msg != null;
    if (requestInProgress == null && (shutdownInitiated || wantClose)) {
      ReferenceCountUtil.release(msg);
      return;
    }
    // fast-path first
    if (msg == LastHttpContent.EMPTY_LAST_CONTENT) {
      onEnd();
    } else if (msg instanceof DefaultHttpRequest) {
      // fast path type check vs concrete class
      DefaultHttpRequest request = (DefaultHttpRequest) msg;
      ContextInternal requestCtx = streamContextSupplier.get();
      Http1xServerRequest req = new Http1xServerRequest(this, request, requestCtx);
      requestInProgress = req;
      if (responseInProgress != null) {
        doPause();
        return;
      }
      boolean keepAlive = HttpUtils.isKeepAlive(request);
      responseInProgress = requestInProgress;
      wantClose = !keepAlive;
      req.handleBegin(keepAlive);
      Handler<HttpServerRequest> handler = request.decoderResult().isSuccess() ? requestHandler : invalidRequestHandler;
      req.context.emit(req, handler);
    } else {
      handleOther(msg);
    }
  }

  private void handleOther(Object msg) {
    // concrete type check first
    if (msg instanceof DefaultHttpContent || msg instanceof HttpContent) {
      onContent(msg);
    }
  }

  private void onContent(Object msg) {
    HttpContent content = (HttpContent) msg;
    if (!content.decoderResult().isSuccess()) {
      handleError(content);
      return;
    }
    Buffer buffer = BufferInternal.buffer(VertxHandler.safeBuffer(content.content()));
    Http1xServerRequest request = requestInProgress;
    request.handleContent(buffer);
    //TODO chunk trailers
    if (content instanceof LastHttpContent) {
      onEnd();
    }
  }

  private void onEnd() {
    boolean tryClose;
    Http1xServerRequest request = requestInProgress;
    requestInProgress = null;
    tryClose = (wantClose || shutdownInitiated) && responseInProgress == null;
    request.handleEnd();
    if (tryClose) {
      closeInternal();
    }
  }

  void write(HttpObject msg, PromiseInternal<Void> promise) {
    writeToChannel(new MessageWrite() {
      @Override
      public void write() {
        Http1xServerConnection.this.write(msg, false, promise == null ? voidPromise : wrap(promise));
        if (msg instanceof LastHttpContent) {
          responseComplete();
        }
      }
      @Override
      public void cancel(Throwable cause) {
        promise.fail(cause);
      }
    });
  }

  void responseComplete() {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      if (METRICS_ENABLED) {
        reportResponseComplete();
      }
      Http1xServerRequest request = responseInProgress;
      responseInProgress = null;
      DecoderResult result = request.decoderResult();
      if (result.isSuccess()) {
        if (requestInProgress == request) {
          // Deferred
        } else {
          Http1xServerRequest next = requestInProgress;
          if (next != null) {
            // Handle pipelined request
            handleNext(next);
          } else if (wantClose || shutdownInitiated) {
            closeInternal();
          }
        }
      } else {
        ChannelPromise channelFuture = channelFuture();
        writeToChannel(Unpooled.EMPTY_BUFFER, channelFuture);
        channelFuture.addListener(fut -> fail(result.cause()));
      }
    } else {
      eventLoop.execute(this::responseComplete);
    }
  }

  private void handleNext(Http1xServerRequest next) {
    boolean keepAlive = HttpUtils.isKeepAlive(next.nettyRequest());
    responseInProgress = next;
    wantClose |= !keepAlive;
    next.handleBegin(keepAlive);
    next.context.emit(next, next_ -> {
      Handler<HttpServerRequest> handler = next_.nettyRequest().decoderResult().isSuccess() ? requestHandler : invalidRequestHandler;
      handler.handle(next_);
    });
    doResume();
  }

  private void reportResponseComplete() {
    Http1xServerRequest request = responseInProgress;
    if (metrics != null) {
      flushBytesWritten();
      if (request.reportMetricsFailed) {
        metrics.requestReset(request.metric());
      } else {
        metrics.responseEnd(request.metric(), request.response(), request.response().bytesWritten());
      }
    }
    VertxTracer tracer = context.tracer();
    Object trace = request.trace();
    if (tracer != null && trace != null) {
      tracer.sendResponse(request.context, request.response(), trace, null, HttpUtils.SERVER_RESPONSE_TAG_EXTRACTOR);
    }
  }

  String serverOrigin() {
    return serverOrigin;
  }

  public VertxInternal vertx() {
    return vertx;
  }

  void createWebSocket(Http1xServerRequest request, PromiseInternal<ServerWebSocket> promise) {
    context.execute(() -> {
      if (request != responseInProgress) {
        promise.fail("Invalid request");
      } else if (!(request.nettyRequest() instanceof FullHttpRequest)) {
        promise.fail(new IllegalStateException());
      } else {
        WebSocketServerHandshaker handshaker;
        try {
          handshaker = createHandshaker(request);
        } catch (WebSocketHandshakeException e) {
          promise.fail(e);
          return;
        }
        promise.complete(new ServerWebSocketHandshaker(request, handshaker, options));
      }
    });
  }

  public WebSocketServerHandshaker createHandshaker(Http1xServerRequest request) throws WebSocketHandshakeException {
    // As a fun part, Firefox 6.0.2 supports Websockets protocol '7'. But,
    // it doesn't send a normal 'Connection: Upgrade' header. Instead it
    // sends: 'Connection: keep-alive, Upgrade'. Brilliant.
    String connectionHeader = request.getHeader(io.vertx.core.http.HttpHeaders.CONNECTION);
    if (connectionHeader == null || !connectionHeader.toLowerCase().contains("upgrade")) {
      request.response()
        .setStatusCode(BAD_REQUEST.code())
        .end("\"Connection\" header must be \"Upgrade\".");
      throw new WebSocketHandshakeException("Invalid connection header");
    }
    if (request.method() != io.vertx.core.http.HttpMethod.GET) {
      request.response()
        .setStatusCode(METHOD_NOT_ALLOWED.code())
        .end();
      throw new WebSocketHandshakeException("Invalid HTTP method");
    }
    String wsURL;
    try {
      wsURL = HttpUtils.getWebSocketLocation(request, isSsl());
    } catch (Exception e) {
      request.response()
        .setStatusCode(BAD_REQUEST.code())
        .end("Invalid request URI");
      throw new WebSocketHandshakeException("Invalid WebSocket location", e);
    }
    String subProtocols = null;
    if (options.getWebSocketSubProtocols() != null) {
      subProtocols = String.join(",", options.getWebSocketSubProtocols());
    }
    WebSocketDecoderConfig config = WebSocketDecoderConfig.newBuilder()
      .allowExtensions(options.getPerMessageWebSocketCompressionSupported() || options.getPerFrameWebSocketCompressionSupported())
      .maxFramePayloadLength(options.getMaxWebSocketFrameSize())
      .allowMaskMismatch(options.isAcceptUnmaskedFrames())
      .closeOnProtocolViolation(false)
      .build();
    WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(wsURL, subProtocols, config);
    WebSocketServerHandshaker shake = factory.newHandshaker(request.nettyRequest());
    if (shake != null) {
      return shake;
    }
    // See WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ch);
    request.response()
      .putHeader(HttpHeaderNames.SEC_WEBSOCKET_VERSION, WebSocketVersion.V13.toHttpHeaderValue())
      .setStatusCode(UPGRADE_REQUIRED.code())
      .end();
    throw new WebSocketHandshakeException("Invalid WebSocket version");
  }

  public void netSocket(Handler<AsyncResult<NetSocket>> handler) {
    Future<NetSocket> fut = netSocket();
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  public Future<NetSocket> netSocket() {
    Promise<NetSocket> promise = context.promise();
    netSocket(promise);
    return promise.future();
  }

  void netSocket(Promise<NetSocket> promise) {
    context.execute(() -> {

      // Flush out all pending data
      flush();

      // remove old http handlers and replace the old handler with one that handle plain sockets
      ChannelPipeline pipeline = chctx.pipeline();
      ChannelHandler compressor = pipeline.get(HttpChunkContentCompressor.class);
      if (compressor != null) {
        pipeline.remove(compressor);
      }

      pipeline.remove("httpDecoder");
      if (pipeline.get("chunkedWriter") != null) {
        pipeline.remove("chunkedWriter");
      }

      pipeline.replace("handler", "handler", VertxHandler.create(ctx -> {
        NetSocketImpl socket = new NetSocketImpl(context, ctx, sslContextManager, options.getSslOptions(), metrics, false) {
          @Override
          protected void handleClosed() {
            if (metrics != null) {
              Http1xServerRequest request = Http1xServerConnection.this.responseInProgress;
              metrics.responseEnd(request.metric(), request.response(), request.response().bytesWritten());
            }
            super.handleClosed();
          }

          @Override
          public synchronized void handleMessage(Object msg) {
            if (msg instanceof HttpContent) {
              ReferenceCountUtil.release(msg);
              return;
            }
            super.handleMessage(msg);
          }
        };
        socket.metric(metric());
        return socket;
      }));

      // check if the encoder can be removed yet or not.
      pipeline.remove("httpEncoder");

      //
      VertxHandler<NetSocketImpl> handler = (VertxHandler<NetSocketImpl>) pipeline.get("handler");
      promise.complete(handler.getConnection());
    });
  }

  @Override
  protected void handleWriteQueueDrained() {
    if (responseInProgress != null) {
      ContextInternal context = responseInProgress.context;
      Handler<Void> handler = responseInProgress.response()::handleWriteQueueDrained;
      context.execute(handler);
    } else {
      super.handleWriteQueueDrained();
    }
  }

  void write100Continue(FutureListener<Void> listener) {
    ChannelPromise promise = listener == null ? chctx.voidPromise() : chctx.newPromise().addListener(listener);
    chctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE), promise);
  }

  void write103EarlyHints(HttpHeaders headers, PromiseInternal<Void> promise) {
    chctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1,
      HttpResponseStatus.EARLY_HINTS,
      Unpooled.buffer(0),
      headers,
      EmptyHttpHeaders.INSTANCE
    )).addListener(promise);
  }

  protected void handleClosed() {
    Http1xServerRequest responseInProgress = this.responseInProgress;
    if (responseInProgress != null) {
      responseInProgress.handleException(HttpUtils.CONNECTION_CLOSED_EXCEPTION);
    }
    super.handleClosed();
  }

  @Override
  public void handleException(Throwable t) {
    super.handleException(t);
    Http1xServerRequest responseInProgress = this.responseInProgress;
    Http1xServerRequest requestInProgress = this.requestInProgress;
    if (requestInProgress != null) {
      requestInProgress.reportMetricsFailed = true;
      requestInProgress.handleException(t);
    }
    if (responseInProgress != null && responseInProgress != requestInProgress) {
      responseInProgress.reportMetricsFailed = true;
      responseInProgress.handleException(t);
    }
  }

  @Override
  protected boolean supportsFileRegion() {
    return super.supportsFileRegion() && chctx.pipeline().get(HttpChunkContentCompressor.class) == null;
  }

  private void handleError(HttpObject obj) {
    DecoderResult result = obj.decoderResult();
    ReferenceCountUtil.release(obj);
    fail(result.cause());
  }
}
