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

package io.vertx.core.http.impl.http1;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.CorruptedWebSocketFrameException;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.*;
import io.vertx.core.http.impl.headers.Http1xHeaders;
import io.vertx.core.http.impl.websocket.ServerWebSocketHandshaker;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.http.QueryParamDecoder;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.impl.MessageWrite;
import io.vertx.core.net.impl.tcp.NetSocketImpl;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.function.Supplier;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static io.vertx.core.spi.metrics.Metrics.*;

/**
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
public class Http1ServerConnection extends Http1Connection implements HttpServerConnection {

  private final String serverOrigin;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final TracingPolicy tracingPolicy;
  private final boolean eagerCreateRequestQueue;
  private final int maxFormAttributeSize;
  private final int maxFormFields;
  private final int maxFormBufferedBytes;
  private final QueryParamDecoder queryParamDecoder;
  private final Http1ServerConfig serverConfig;
  private final boolean registerWebSocketWriteHandlers;
  private final WebSocketServerConfig webSocketConfig;
  private final ServerSSLOptions sslOptions;

  private Http1ServerRequest requestInProgress;
  private Http1ServerRequest responseInProgress;
  private boolean wantClose;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<HttpServerRequest> invalidRequestHandler;
  private boolean createStream;

  // WebSocket upgrade: a client may send frames before receiving the 101 response (RFC 6455 forbids it, but a server
  // must still cope with it). The Netty handshaker replaces the HTTP decoder with the WebSocket decoder before writing
  // the 101, and the WebSocket handler only replaces this connection in the pipeline once the 101 write completes.
  // Frames decoded in between are delivered to this connection and must not be dropped since they are pooled buffers
  // that only this connection can release.
  //
  // - pendingWebSocketFrames: frames received while the outcome of the upgrade is not yet known, reading the channel
  //   is paused while this buffer is not empty so a client cannot make it grow (see webSocketHandshakeCompleted)
  // - webSocketHandler: set when the upgrade completed, frames received afterwards are forwarded to it
  // - webSocketUpgradeFailed: set when the upgrade failed, frames received afterwards are released
  private ArrayDeque<WebSocketFrame> pendingWebSocketFrames;
  private VertxHandler<?> webSocketHandler;
  private boolean webSocketUpgradeFailed;

  public final HttpServerMetrics httpMetrics;
  private final TransportMetrics<?> transportMetrics;
  final boolean handle100ContinueAutomatically;
  final SslContextManager sslContextManager;

  public Http1ServerConnection(ThreadingModel threadingModel,
                               Supplier<ContextInternal> streamContextSupplier,
                               boolean handle100ContinueAutomatically,
                               ServerSSLOptions sslOptions,
                               SslContextManager sslContextManager,
                               int maxFormAttributeSize,
                               int maxFormFields,
                               int maxFormBufferedBytes,
                               QueryParamDecoderConfig queryParamDecoderConfig,
                               Http1ServerConfig serverConfig,
                               boolean registerWebSocketWriteHandlers,
                               WebSocketServerConfig webSocketConfig,
                               ChannelHandlerContext chctx,
                               ContextInternal context,
                               String serverOrigin,
                               TracingPolicy tracingPolicy,
                               HttpServerMetrics<?, ?> httpMetrics,
                               TransportMetrics<?> transportMetrics) {
    super(context, chctx);
    this.serverOrigin = serverOrigin;
    this.streamContextSupplier = streamContextSupplier;
    this.maxFormAttributeSize = maxFormAttributeSize;
    this.maxFormFields = maxFormFields;
    this.maxFormBufferedBytes = maxFormBufferedBytes;
    this.queryParamDecoder = new QueryParamDecoder(queryParamDecoderConfig);
    this.serverConfig = serverConfig;
    this.registerWebSocketWriteHandlers = registerWebSocketWriteHandlers;
    this.webSocketConfig = webSocketConfig;
    this.sslContextManager = sslContextManager;
    this.httpMetrics = httpMetrics;
    this.transportMetrics = transportMetrics;
    this.handle100ContinueAutomatically = handle100ContinueAutomatically;
    this.tracingPolicy = tracingPolicy;
    this.wantClose = false;
    this.eagerCreateRequestQueue = threadingModel != ThreadingModel.EVENT_LOOP;
    this.sslOptions = sslOptions;
  }

  int maxFormAttributeSize() {
    return maxFormAttributeSize;
  }

  int maxFormFields() {
    return maxFormFields;
  }

  int maxFormBufferedBytes() {
    return maxFormBufferedBytes;
  }

  QueryParamDecoder queryParamDecoder() {
    return queryParamDecoder;
  }

  @Override
  protected void handleShutdown(Duration timeout, ChannelPromise promise) {
    super.handleShutdown(timeout, promise);
    if (!timeout.isZero() && responseInProgress == null) {
      closeInternal();
    }
  }

  @Override
  public MultiMap newHeaders() {
    return Http1xHeaders.httpHeaders();
  }

  @Override
  public boolean supportsSendFile() {
    return true;
  }

  TracingPolicy tracingPolicy() {
    return tracingPolicy;
  }

  @Override
  public HttpServerConnection streamHandler(Handler<HttpServerStream> handler) {
    if (handler != null) {
      createStream = true;
      requestHandler = request -> {
        Http1ServerStream stream = (Http1ServerStream)request;
        stream.init();
        handler.handle(stream);
        stream.handleHead();
      };
    } else {
      createStream = false;
      requestHandler = null;
    }
    return this;
  }

  public HttpServerConnection handler(Handler<HttpServerRequest> handler) {
    requestHandler = handler;
    return this;
  }

  public HttpServerConnection invalidRequestHandler(Handler<HttpServerRequest> handler) {
    invalidRequestHandler = handler;
    return this;
  }

  @Override
  public TransportMetrics<?> metrics() {
    return transportMetrics;
  }

  public void handleMessage(Object msg) {
    assert msg != null;
    if (requestInProgress == null && (shutdownInitiated != null || wantClose)) {
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
      Http1ServerRequest req = createStream ? new Http1ServerStream(this, request, requestCtx) : new Http1ServerRequest(this, request, requestCtx);
      if (eagerCreateRequestQueue) {
        req.resume();
      }
      requestInProgress = req;
      if (responseInProgress != null) {
        req.pause();
        enqueueRequest(req);
        doPause();
        return;
      }
      boolean keepAlive = HttpUtils.isKeepAlive(request);
      responseInProgress = requestInProgress;
      wantClose = !keepAlive;
      req.handleBegin(keepAlive);
      req.context.emit(req, r -> {
        Handler<HttpServerRequest> handler = request.decoderResult().isSuccess() ? requestHandler : invalidRequestHandler;
        handler.handle(req);
      });
    } else {
      handleOther(msg);
    }
  }

  private void handleOther(Object msg) {
    // concrete type check first
    if (msg instanceof DefaultHttpContent || msg instanceof HttpContent) {
      onContent(msg);
    } else if (msg instanceof WebSocketFrame) {
      onWebSocketFrame((WebSocketFrame) msg);
    } else {
      // Unexpected message: release it rather than leak it (no-op when it is not reference counted)
      ReferenceCountUtil.release(msg);
    }
  }

  /**
   * Handle a WebSocket frame sent by the client along with the upgrade request, i.e. before the {@code 101} response.
   * <p>
   * Such frame is decoded when the WebSocket decoder replaces the HTTP decoder, before the WebSocket handler replaces
   * the handler of this connection in the pipeline. It is buffered until the upgrade completes and then forwarded to
   * the WebSocket handler, or released when the upgrade fails.
   * <p>
   * The upgrade outcome can be known by the time the frame gets here: the accept usually happens within the read of
   * the upgrade request, the frame is therefore decoded while {@link #handleMessage} is being executed and is queued
   * by {@link #read} until the current message is handled. When the 101 write completes synchronously, the handshake
   * listener runs (and calls {@link #webSocketUpgraded} or {@link #webSocketUpgradeFailed}) before this frame is
   * dequeued. This is the common case, buffering only happens when the 101 write completes asynchronously.
   */
  private void onWebSocketFrame(WebSocketFrame frame) {
    if (webSocketUpgradeFailed) {
      // The upgrade failed before this frame left the inbound queue, nothing will ever consume it
      ReferenceCountUtil.release(frame);
      return;
    }
    VertxHandler<?> handler = webSocketHandler;
    if (handler == null) {
      // Upgrade in progress: the 101 write has not completed yet
      if (pendingWebSocketFrames == null) {
        pendingWebSocketFrames = new ArrayDeque<>();
        // Stop reading the channel until the outcome of the upgrade is known: a client flooding the connection
        // before it receives the 101 cannot make this buffer grow beyond the frames decoded from the bytes already
        // received, i.e. at most a couple of socket reads. Reading is resumed by webSocketHandshakeCompleted() or
        // webSocketUpgradeFailed().
        doPause();
      }
      pendingWebSocketFrames.add(frame);
      return;
    }
    // The upgrade completed while this frame was in the inbound queue
    ChannelHandlerContext ctx = chctx.pipeline().context(handler);
    if (ctx == null) {
      // The WebSocket handler was already removed from the pipeline (e.g. the WebSocket was closed by the application
      // when it was handed over), nothing will ever consume this frame
      ReferenceCountUtil.release(frame);
      return;
    }
    if (forwardWebSocketFrame(handler, ctx, frame)) {
      // This frame is dequeued outside of a Netty read event of the WebSocket handler, signal the read completion so
      // that anything written by the application in reaction to the frame is flushed
      handler.channelReadComplete(ctx);
    }
  }

  /**
   * Forward to the WebSocket handler a frame that was decoded before the WebSocket handler was in the pipeline.
   * <p>
   * The handler is invoked directly instead of firing the frame in the pipeline: the frame has already been through
   * the handlers preceding this one (e.g. logging or traffic shaping) and is only missing the handover to the WebSocket
   * connection, firing it again from the head of the pipeline would process it twice.
   *
   * @return {@code true} when the frame was forwarded, {@code false} when the frame was rejected and released
   */
  private static boolean forwardWebSocketFrame(VertxHandler<?> handler, ChannelHandlerContext ctx, WebSocketFrame frame) {
    int rsv = frame.rsv();
    if (rsv != 0) {
      // Extensions (e.g. compression) are negotiated by the 101 response and their decoders are added to the pipeline
      // once it is written, so no extension decoder processed this frame and nothing can interpret its RSV bits (the
      // WebSocket decoder lets them through when the server supports extensions). The client sent an extension frame
      // before knowing whether the extension was accepted, this is a protocol violation: fail the WebSocket like the
      // WebSocket decoder does when the server supports no extension.
      ReferenceCountUtil.release(frame);
      handler.exceptionCaught(ctx, new CorruptedWebSocketFrameException(WebSocketCloseStatus.PROTOCOL_ERROR,
        "RSV != 0 and no extension negotiated, RSV:" + rsv));
      return false;
    }
    handler.channelRead(ctx, frame);
    return true;
  }

  /**
   * Signal the {@code 101} response has been written, before the WebSocket handler replaces this connection handler
   * in the pipeline: reading the channel is resumed when it was paused by {@link #onWebSocketFrame}.
   * <p>
   * This must happen before the WebSocket is handed over to the application (and therefore cannot be done by
   * {@link #webSocketUpgraded}): the application may pause the WebSocket, which also stops reading the channel,
   * resuming afterwards would override the application pause.
   */
  public void webSocketHandshakeCompleted() {
    assert chctx.executor().inEventLoop();
    doResume();
  }

  /**
   * Signal the WebSocket upgrade completed, the {@code handler} replaced this connection handler in the pipeline.
   * WebSocket frames received by this connection during the upgrade are forwarded to the {@code handler}.
   * <p>
   * The caller must invoke this method after the WebSocket has been handed over to the application, so that the
   * application handlers are set when the frames are delivered, like for a frame received after the upgrade.
   *
   * @param handler the WebSocket handler
   */
  public void webSocketUpgraded(VertxHandler<?> handler) {
    assert chctx.executor().inEventLoop();
    webSocketHandler = handler;
    ArrayDeque<WebSocketFrame> frames = pendingWebSocketFrames;
    if (frames != null) {
      pendingWebSocketFrames = null;
      ChannelHandlerContext ctx = chctx.pipeline().context(handler);
      if (ctx == null) {
        // The WebSocket handler was already removed from the pipeline, see onWebSocketFrame
        frames.forEach(ReferenceCountUtil::release);
        return;
      }
      WebSocketFrame frame;
      while ((frame = frames.poll()) != null) {
        if (!forwardWebSocketFrame(handler, ctx, frame)) {
          // The WebSocket failed, the remaining frames are not delivered
          frames.forEach(ReferenceCountUtil::release);
          return;
        }
      }
      // Single read completion for the batch, see onWebSocketFrame
      handler.channelReadComplete(ctx);
    }
  }

  /**
   * Signal the WebSocket upgrade failed, WebSocket frames received by this connection during the upgrade are released
   * and reading the channel is resumed when it was paused by {@link #onWebSocketFrame}.
   * <p>
   * The connection is left with the WebSocket decoder installed and this handler in the pipeline, any WebSocket frame
   * received from now on is released as well since nothing can consume it.
   */
  public void webSocketUpgradeFailed() {
    assert chctx.executor().inEventLoop();
    webSocketUpgradeFailed = true;
    releasePendingWebSocketFrames();
    doResume();
  }

  private void releasePendingWebSocketFrames() {
    ArrayDeque<WebSocketFrame> frames = pendingWebSocketFrames;
    if (frames != null) {
      pendingWebSocketFrames = null;
      frames.forEach(ReferenceCountUtil::release);
    }
  }

  private void onContent(Object msg) {
    HttpContent content = (HttpContent) msg;
    if (!content.decoderResult().isSuccess()) {
      handleError(content);
      return;
    }
    Buffer buffer = BufferInternal.safeBuffer(content.content());
    Http1ServerRequest request = requestInProgress;
    request.handleContent(buffer);
    //TODO chunk trailers
    if (content instanceof LastHttpContent) {
      onEnd();
    }
  }

  private void onEnd() {
    Http1ServerRequest request = requestInProgress;
    requestInProgress = null;
    boolean tryClose = (wantClose || shutdownInitiated != null) && responseInProgress == null;
    request.handleEnd();
    if (METRICS_ENABLED && httpMetrics != null) {
      flushBytesRead();
    }
    if (tryClose) {
      closeInternal();
    }
  }

  void write(VertxHttpObject msg, Promise<Void> promise) {
    writeToChannel(new MessageWrite() {
      @Override
      public void write() {
        Http1ServerConnection.this.unsafeWrite(msg, false, promise);
        if (msg.isEnded()) {
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
      Http1ServerRequest request = responseInProgress;
      responseInProgress = null;
      DecoderResult result = request.decoderResult();
      if (result.isSuccess()) {
        if (requestInProgress == request) {
          // Deferred
        } else {
          Http1ServerRequest next = request.next;
          if (next != null) {
            // Handle pipelined request
            handleNext(next);
          } else if (wantClose || shutdownInitiated != null) {
            closeInternal();
          }
        }
      } else {
        ChannelPromise channelFuture = newChannelPromise();
        writeToChannel(Unpooled.EMPTY_BUFFER, channelFuture);
        channelFuture.addListener(fut -> fail(result.cause()));
      }
    } else {
      eventLoop.execute(this::responseComplete);
    }
  }

  private void handleNext(Http1ServerRequest next) {
    boolean keepAlive = HttpUtils.isKeepAlive(next.nettyRequest());
    responseInProgress = next;
    wantClose |= !keepAlive;
    next.handleBegin(keepAlive);
    next.context.emit(next, next_ -> {
      next.resume();
      Handler<HttpServerRequest> handler = next_.nettyRequest().decoderResult().isSuccess() ? requestHandler : invalidRequestHandler;
      handler.handle(next_);
    });
    doResume();
  }

  void resumeRequest(Http1ServerRequest request) {
    if (responseInProgress == request) {
      doResume();
    }
  }

  private void enqueueRequest(Http1ServerRequest req) {
    Http1ServerRequest tail = responseInProgress;
    while (tail.next != null) {
      tail = tail.next;
    }
    tail.next = req;
  }

  private void reportResponseComplete() {
    Http1ServerRequest request = responseInProgress;
    if (httpMetrics != null) {
      flushBytesWritten();
      if (request.reportMetricsFailed) {
        httpMetrics.requestReset(request.metric());
      } else {
        httpMetrics.responseEnd(request.metric(), request.response(), request.response().bytesWritten());
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

  void createWebSocket(Http1ServerRequest request, FullHttpRequest fullRequest, PromiseInternal<ServerWebSocketHandshake> promise) {
    context.execute(() -> {
      wantClose = false;
      if (request != responseInProgress) {
        promise.fail("Invalid request");
      } else {
        WebSocketServerHandshaker handshaker;
        try {
          handshaker = createHandshaker(request, fullRequest);
        } catch (WebSocketHandshakeException e) {
          promise.fail(e);
          return;
        }
        promise.complete(new ServerWebSocketHandshaker(request, fullRequest, handshaker, webSocketConfig, registerWebSocketWriteHandlers));
      }
    });
  }

  public WebSocketServerHandshaker createHandshaker(Http1ServerRequest request, FullHttpRequest fullRequest) throws WebSocketHandshakeException {
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
    if (webSocketConfig.getSubProtocols() != null) {
      subProtocols = String.join(",", webSocketConfig.getSubProtocols());
    }
    WebSocketDecoderConfig config = WebSocketDecoderConfig.newBuilder()
      .allowExtensions(webSocketConfig.getUsePerMessageCompression() || webSocketConfig.getUsePerFrameCompression())
      .maxFramePayloadLength(webSocketConfig.getMaxFrameSize())
      .allowMaskMismatch(webSocketConfig.isUseUnmaskedFrames())
      .closeOnProtocolViolation(false)
      .build();
    WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(wsURL, subProtocols, config);
    WebSocketServerHandshaker shake = factory.newHandshaker(fullRequest);
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
        NetSocketImpl socket = new NetSocketImpl(context, ctx, sslContextManager, sslOptions, transportMetrics, false) {
          @Override
          protected void handleClosed() {
            if (httpMetrics != null) {
              Http1ServerRequest request = Http1ServerConnection.this.responseInProgress;
              httpMetrics.responseEnd(request.metric(), request.response(), request.response().bytesWritten());
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

  void write100Continue(Promise<Void> promise) {
    write(new VertxFullHttpResponse(
      false,
      HTTP_1_1,
      CONTINUE,
      Unpooled.buffer(0),
      DefaultHttpHeadersFactory.headersFactory().newHeaders(),
      DefaultHttpHeadersFactory.trailersFactory().newHeaders(),
      false), promise);
  }

  void write103EarlyHints(HttpHeaders headers, Promise<Void> promise) {
    write(new VertxFullHttpResponse(false,
      HTTP_1_1,
      HttpResponseStatus.EARLY_HINTS,
      Unpooled.buffer(0),
      headers,
      EmptyHttpHeaders.INSTANCE,
      false), promise);
  }

  protected void handleClosed() {
    // The connection can be closed while the upgrade is in progress
    releasePendingWebSocketFrames();
    Http1ServerRequest responseInProgress = this.responseInProgress;
    Http1ServerRequest requestInProgress = this.requestInProgress;
    for (Http1ServerRequest req = responseInProgress; req != null;req = req.next) {
      if (req.response() != null) {
        req.handleClosed();
      }
    }
    if (requestInProgress != null && responseInProgress == null && requestInProgress.response() != null) {
      requestInProgress.handleClosed();
    }
    super.handleClosed();
  }

  @Override
  public boolean handleException(Throwable t) {
    boolean ret = super.handleException(t);
    Http1ServerRequest responseInProgress = this.responseInProgress;
    Http1ServerRequest requestInProgress = this.requestInProgress;
    for (Http1ServerRequest req = responseInProgress;req != null;req = req.next) {
      if (req.response() != null) {
        req.reportMetricsFailed = true;
        req.handleException(t);
      }
    }
    if (requestInProgress != null && responseInProgress == null && requestInProgress.response() != null) {
      requestInProgress.reportMetricsFailed = true;
      requestInProgress.handleException(t);
    }
    return ret;
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
