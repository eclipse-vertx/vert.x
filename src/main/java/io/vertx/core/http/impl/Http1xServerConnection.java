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

import io.netty.channel.*;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.function.Supplier;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.UPGRADE_REQUIRED;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 *
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 * </p>
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
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
public class Http1xServerConnection extends Http1xConnectionBase<ServerWebSocketImpl> implements HttpServerConnection {

  private static final Logger log = LoggerFactory.getLogger(Http1xServerConnection.class);

  private final String serverOrigin;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final SSLHelper sslHelper;
  private final TracingPolicy tracingPolicy;
  private boolean requestFailed;

  private Http1xServerRequest requestInProgress;
  private Http1xServerRequest responseInProgress;
  private boolean channelPaused;
  private Handler<HttpServerRequest> requestHandler;

  final HttpServerMetrics metrics;
  final boolean handle100ContinueAutomatically;
  final HttpServerOptions options;

  public Http1xServerConnection(Supplier<ContextInternal> streamContextSupplier,
                                SSLHelper sslHelper,
                                HttpServerOptions options,
                                ChannelHandlerContext chctx,
                                ContextInternal context,
                                String serverOrigin,
                                HttpServerMetrics metrics) {
    super(context, chctx);
    this.serverOrigin = serverOrigin;
    this.streamContextSupplier = streamContextSupplier;
    this.options = options;
    this.sslHelper = sslHelper;
    this.metrics = metrics;
    this.handle100ContinueAutomatically = options.isHandle100ContinueAutomatically();
    this.tracingPolicy = options.getTracingPolicy();
  }

  @Override
  public HttpServerConnection handler(Handler<HttpServerRequest> handler) {
    requestHandler = handler;
    return this;
  }

  @Override
  public HttpServerMetrics metrics() {
    return metrics;
  }

  public void handleMessage(Object msg) {
    if (msg instanceof HttpRequest) {
      DefaultHttpRequest request = (DefaultHttpRequest) msg;
      if (!request.decoderResult().isSuccess()) {
        handleError(request);
        return;
      }
      ContextInternal requestCtx = streamContextSupplier.get();
      Http1xServerRequest req = new Http1xServerRequest(this, request, requestCtx);
      requestInProgress = req;
      if (responseInProgress != null) {
        enqueueRequest(req);
        return;
      }
      responseInProgress = requestInProgress;
      if (METRICS_ENABLED) {
        reportRequestBegin(req);
      }
      req.handleBegin();
      req.context.emit(req, requestHandler);
    } else if (msg == LastHttpContent.EMPTY_LAST_CONTENT) {
      onEnd();
    } else {
      handleOther(msg);
    }
  }

  private void enqueueRequest(Http1xServerRequest req) {
    // Deferred until the current response completion
    responseInProgress.enqueue(req);
    req.pause();
  }

  private void handleOther(Object msg) {
    if (msg instanceof HttpContent) {
      onContent(msg);
    } else if (msg instanceof WebSocketFrame) {
      handleWsFrame((WebSocketFrame) msg);
    }
  }

  private void reportRequestBegin(Http1xServerRequest request) {
    if (metrics != null) {
      request.metric = metrics.requestBegin(metric(), request);
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      request.trace = tracer.receiveRequest(request.context, SpanKind.RPC, tracingPolicy, request, request.method().name(), request.headers(), HttpUtils.SERVER_REQUEST_TAG_EXTRACTOR);
    }
  }

  private void onContent(Object msg) {
    HttpContent content = (HttpContent) msg;
    if (!content.decoderResult().isSuccess()) {
      handleError(content);
      return;
    }
    Buffer buffer = Buffer.buffer(VertxHandler.safeBuffer(content.content()));
    Http1xServerRequest request;
    synchronized (this) {
      request = requestInProgress;
    }
    request.context.execute(buffer, request::handleContent);
    //TODO chunk trailers
    if (content instanceof LastHttpContent) {
      onEnd();
    }
  }

  private void onEnd() {
    Http1xServerRequest request;
    synchronized (this) {
      request = requestInProgress;
      requestInProgress = null;
    }
    if (METRICS_ENABLED) {
      reportRequestComplete(request);
    }
    request.context.execute(request, Http1xServerRequest::handleEnd);
  }

  void responseComplete() {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      if (METRICS_ENABLED) {
        reportResponseComplete();
      }
      Http1xServerRequest request = responseInProgress;
      responseInProgress = null;
      Http1xServerRequest next = request.next();
      if (next != null) {
        // Handle pipelined request
        handleNext(next);
      }
    } else {
      eventLoop.execute(this::responseComplete);
    }
  }

  private void handleNext(Http1xServerRequest next) {
    responseInProgress = next;
    next.handleBegin();
    context.emit(next, next_ -> {
      next_.resume();
      requestHandler.handle(next_);
    });
  }

  @Override
  public void doPause() {
    if (!channelPaused) {
      channelPaused = true;
      super.doPause();
    }
  }

  @Override
  public void doResume() {
    if (channelPaused) {
      channelPaused = false;
      super.doResume();
    }
  }

  private void reportRequestComplete(Http1xServerRequest request) {
    if (metrics != null) {
      metrics.requestEnd(request.metric(), request, request.bytesRead());
      flushBytesRead();
    }
  }

  private void reportResponseComplete() {
    Http1xServerRequest request = responseInProgress;
    if (metrics != null) {
      flushBytesWritten();
      if (requestFailed) {
        metrics.requestReset(request.metric());
        requestFailed = false;
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

  String getServerOrigin() {
    return serverOrigin;
  }

  Vertx vertx() {
    return vertx;
  }

  void createWebSocket(Http1xServerRequest request, PromiseInternal<ServerWebSocket> promise) {
    context.execute(() -> {
      if (request != responseInProgress) {
        promise.fail("Invalid request");
      } else if (webSocket != null) {
        promise.complete(webSocket);
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
        webSocket = new ServerWebSocketImpl(
          promise.context(),
          this,
          handshaker.version() != WebSocketVersion.V00,
          options.getWebSocketClosingTimeout(),
          request,
          handshaker,
          options.getMaxWebSocketFrameSize(),
          options.getMaxWebSocketMessageSize());
        if (METRICS_ENABLED && metrics != null) {
          webSocket.setMetric(metrics.connected(metric(), request.metric(), webSocket));
        }
        promise.complete(webSocket);
      }
    });
  }

  private WebSocketServerHandshaker createHandshaker(Http1xServerRequest request) throws WebSocketHandshakeException {
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
        NetSocketImpl socket = new NetSocketImpl(context, ctx, sslHelper, metrics) {
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
  public void handleInterestedOpsChanged() {
    ContextInternal context;
    Handler<Boolean> handler;
    synchronized (this) {
      if (responseInProgress != null) {
        context = responseInProgress.context;
        handler = responseInProgress.response()::handleWritabilityChanged;
      } else if (webSocket != null) {
        context = webSocket.context;
        handler = webSocket::handleWritabilityChanged;
      } else {
        return;
      }
    }
    boolean writable = !isNotWritable();
    context.execute(writable, handler);
  }

  void write100Continue() {
    chctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
  }

  protected void handleClosed() {
    Http1xServerRequest responseInProgress;
    Http1xServerRequest requestInProgress;
    ServerWebSocketImpl ws;
    synchronized (this) {
      ws = this.webSocket;
      requestInProgress = this.requestInProgress;
      responseInProgress = this.responseInProgress;
    }
    if (requestInProgress != null) {
      requestInProgress.context.execute(v -> {
        requestInProgress.handleException(CLOSED_EXCEPTION);
      });
    }
    if (responseInProgress != null && responseInProgress != requestInProgress) {
      responseInProgress.context.execute(v -> {
        responseInProgress.handleException(CLOSED_EXCEPTION);
      });
    }
    if (ws != null) {
      ws.context.execute(v -> ws.handleConnectionClosed());
    }
    super.handleClosed();
  }

  @Override
  protected void handleException(Throwable t) {
    super.handleException(t);
    Http1xServerRequest responseInProgress;
    Http1xServerRequest requestInProgress;
    ServerWebSocketImpl ws;
    synchronized (this) {
      ws = this.webSocket;
      requestInProgress = this.requestInProgress;
      responseInProgress = this.responseInProgress;
      if (METRICS_ENABLED && metrics != null) {
        requestFailed = true;
      }
    }
    if (requestInProgress != null) {
      requestInProgress.handleException(t);
    }
    if (responseInProgress != null && responseInProgress != requestInProgress) {
      responseInProgress.handleException(t);
    }
    if (ws != null) {
      ws.context.execute(v -> ws.handleException(t));
    }
  }

  @Override
  protected boolean supportsFileRegion() {
    return super.supportsFileRegion() && chctx.pipeline().get(HttpChunkContentCompressor.class) == null;
  }

  private void handleError(HttpRequest request) {
    DecoderResult result = request.decoderResult();
    Throwable cause = result.cause();
    HttpResponseStatus status = null;
    if (cause instanceof TooLongFrameException) {
      String causeMsg = cause.getMessage();
      if (causeMsg.startsWith("An HTTP line is larger than")) {
        status = HttpResponseStatus.REQUEST_URI_TOO_LONG;
      } else if (causeMsg.startsWith("HTTP header is larger than")) {
        status = HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE;
      }
    }
    if (status == null && HttpMethod.GET == request.method() &&
      HttpVersion.HTTP_1_0 == request.protocolVersion() && "/bad-request".equals(request.uri())) {
      // Matches Netty's specific HttpRequest for invalid messages
      status = HttpResponseStatus.BAD_REQUEST;
    }
    if (status != null) {
      DefaultFullHttpResponse resp = new DefaultFullHttpResponse(request.protocolVersion(), status);
      ChannelPromise fut = chctx.newPromise();
      writeToChannel(resp, fut);
      fut.addListener(res -> fail(result.cause()));
    } else {
      handleError((HttpObject) request);
    }
  }

  private void handleError(HttpObject obj) {
    DecoderResult result = obj.decoderResult();
    ReferenceCountUtil.release(obj);
    fail(result.cause());
  }
}
