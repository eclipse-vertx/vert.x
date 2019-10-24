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
import io.netty.channel.*;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.*;

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
  private final SSLHelper sslHelper;
  private boolean requestFailed;
  private long bytesRead;
  private long bytesWritten;

  private HttpServerRequestImpl requestInProgress;
  private HttpServerRequestImpl responseInProgress;
  private boolean channelPaused;
  private Handler<HttpServerRequest> requestHandler;

  final HttpServerMetrics metrics;
  final boolean handle100ContinueAutomatically;
  final HttpServerOptions options;

  public Http1xServerConnection(VertxInternal vertx,
                                SSLHelper sslHelper,
                                HttpServerOptions options,
                                ChannelHandlerContext channel,
                                ContextInternal context,
                                String serverOrigin,
                                HttpServerMetrics metrics) {
    super(vertx, channel, context);
    this.serverOrigin = serverOrigin;
    this.options = options;
    this.sslHelper = sslHelper;
    this.metrics = metrics;
    this.handle100ContinueAutomatically = options.isHandle100ContinueAutomatically();
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
      if (request.decoderResult() != DecoderResult.SUCCESS) {
        handleError(request);
        return;
      }
      HttpServerRequestImpl req = new HttpServerRequestImpl(this, request);
      synchronized (this) {
        requestInProgress = req;
        if (responseInProgress != null) {
          // Deferred until the current response completion
          responseInProgress.enqueue(req);
          req.pause();
          return;
        }
        responseInProgress = requestInProgress;
        if (METRICS_ENABLED) {
          req.reportRequestBegin();
        }
        req.handleBegin();
      }
      ContextInternal ctx = req.context;
      ContextInternal prev = ctx.beginDispatch();
      try {
        requestHandler.handle(req);
      } catch (Throwable t) {
        ctx.reportException(t);
      } finally {
        ctx.endDispatch(prev);
      }
    } else if (msg == LastHttpContent.EMPTY_LAST_CONTENT) {
      handleEnd();
    } else if (msg instanceof HttpContent) {
      handleContent(msg);
    } else if (msg instanceof WebSocketFrame) {
      handleWsFrame((WebSocketFrame) msg);
    }
  }

  private void handleContent(Object msg) {
    HttpContent content = (HttpContent) msg;
    if (content.decoderResult() != DecoderResult.SUCCESS) {
      handleError(content);
      return;
    }
    Buffer buffer = Buffer.buffer(VertxHandler.safeBuffer(content.content(), chctx.alloc()));
    HttpServerRequestImpl request;
    synchronized (this) {
      if (METRICS_ENABLED) {
        reportBytesRead(buffer);
      }
      request = requestInProgress;
    }
    request.context.dispatch(v -> {
      request.handleContent(buffer);
    });
    //TODO chunk trailers
    if (content instanceof LastHttpContent) {
      handleEnd();
    }
  }

  private void handleEnd() {
    HttpServerRequestImpl request;
    synchronized (this) {
      if (METRICS_ENABLED) {
        reportRequestComplete();
      }
      request = requestInProgress;
      requestInProgress = null;
    }
    request.context.dispatch(v -> {
      request.handleEnd();
    });
  }

  synchronized void responseComplete() {
    if (METRICS_ENABLED) {
      reportResponseComplete();
    }
    HttpServerRequestImpl request = responseInProgress;
    responseInProgress = null;
    HttpServerRequestImpl next = request.next();
    if (next != null) {
      // Handle pipelined request
      handleNext(next);
    }
  }

  private void handleNext(HttpServerRequestImpl next) {
    responseInProgress = next;
    next.handleBegin();
    context.runOnContext(v -> {
      next.resume();
      requestHandler.handle(next);
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

  private void reportBytesRead(Buffer buffer) {
    if (metrics != null) {
      bytesRead += buffer.length();
    }
  }

  private void reportBytesWritten(Object msg) {
    if (metrics != null) {
      long bytes = getBytes(msg);
      if (bytes == -1) {
        log.warn("Metrics could not be updated to include bytes written because of unknown object " + msg.getClass() + " being written.");
      } else {
        bytesWritten += bytes;
      }
    }
  }

  private void reportRequestComplete() {
    if (metrics != null) {
      reportBytesRead(bytesRead);
      bytesRead = 0;
    }
  }

  private void reportResponseComplete() {
    if (metrics != null) {
      reportBytesWritten(bytesWritten);
      if (requestFailed) {
        metrics.requestReset(responseInProgress.metric());
        requestFailed = false;
      } else {
        metrics.responseEnd(responseInProgress.metric(), responseInProgress.response());
      }
      bytesWritten = 0;
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      tracer.sendResponse(responseInProgress.context, responseInProgress.response(), responseInProgress.trace(), null, HttpUtils.SERVER_RESPONSE_TAG_EXTRACTOR);
    }
  }

  String getServerOrigin() {
    return serverOrigin;
  }

  Vertx vertx() {
    return vertx;
  }

  @Override
  public void writeToChannel(Object msg, ChannelPromise promise) {
    if (METRICS_ENABLED) {
      reportBytesWritten(msg);
    }
    super.writeToChannel(msg, promise);
  }

  ServerWebSocketImpl createWebSocket(HttpServerRequestImpl request) {
    if (ws != null) {
      return ws;
    }
    if (!(request.nettyRequest() instanceof FullHttpRequest)) {
      throw new IllegalStateException();
    }
    WebSocketServerHandshaker handshaker = createHandshaker(request);
    if (handshaker == null) {
      return null;
    }
    ws = new ServerWebSocketImpl(vertx.getOrCreateContext(), this, handshaker.version() != WebSocketVersion.V00,
      request, handshaker, options.getMaxWebsocketFrameSize(), options.getMaxWebsocketMessageSize());
    if (METRICS_ENABLED && metrics != null) {
      ws.setMetric(metrics.connected(metric(), request.metric(), ws));
    }
    return ws;
  }

  private WebSocketServerHandshaker createHandshaker(HttpServerRequestImpl request) {
    // As a fun part, Firefox 6.0.2 supports Websockets protocol '7'. But,
    // it doesn't send a normal 'Connection: Upgrade' header. Instead it
    // sends: 'Connection: keep-alive, Upgrade'. Brilliant.
    Channel ch = channel();
    String connectionHeader = request.getHeader(io.vertx.core.http.HttpHeaders.CONNECTION);
    if (connectionHeader == null || !connectionHeader.toLowerCase().contains("upgrade")) {
      request.response()
        .setStatusCode(BAD_REQUEST.code())
        .end("\"Connection\" header must be \"Upgrade\".");
      return null;
    }
    if (request.method() != io.vertx.core.http.HttpMethod.GET) {
      request.response()
        .setStatusCode(METHOD_NOT_ALLOWED.code())
        .end();
      return null;
    }
    String wsURL;
    try {
      wsURL = HttpUtils.getWebSocketLocation(request, isSsl());
    } catch (Exception e) {
      request.response()
        .setStatusCode(BAD_REQUEST.code())
        .end("Invalid request URI");
      return null;
    }

    WebSocketServerHandshakerFactory factory =
      new WebSocketServerHandshakerFactory(wsURL,
        options.getWebsocketSubProtocols(),
        options.getPerMessageWebsocketCompressionSupported() || options.getPerFrameWebsocketCompressionSupported(),
        options.getMaxWebsocketFrameSize(), options.isAcceptUnmaskedFrames());
    WebSocketServerHandshaker shake = factory.newHandshaker(request.nettyRequest());
    if (shake == null) {
      // See WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ch);
      request.response()
        .putHeader(HttpHeaderNames.SEC_WEBSOCKET_VERSION, WebSocketVersion.V13.toHttpHeaderValue())
        .setStatusCode(UPGRADE_REQUIRED.code())
        .end();
    }
    return shake;
  }

  NetSocket createNetSocket() {

    Map<Channel, NetSocketImpl> connectionMap = new HashMap<>(1);

    NetSocketImpl socket = new NetSocketImpl(vertx, chctx, context, sslHelper, metrics) {
      @Override
      protected void handleClosed() {
        if (metrics != null) {
          metrics.responseEnd(responseInProgress.metric(), responseInProgress.response());
        }
        connectionMap.remove(chctx.channel());
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
    connectionMap.put(chctx.channel(), socket);

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

    chctx.pipeline().replace("handler", "handler", VertxHandler.create(socket));

    // check if the encoder can be removed yet or not.
    chctx.pipeline().remove("httpEncoder");
    return socket;
  }

  @Override
  public synchronized void handleInterestedOpsChanged() {
    if (!isNotWritable()) {
      if (responseInProgress != null) {
        responseInProgress.context.dispatch(v -> responseInProgress.response().handleDrained());
      } else if (ws != null) {
        ws.handleDrained();
      }
    }
  }

  void write100Continue() {
    chctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
  }

  protected void handleClosed() {
    HttpServerRequestImpl responseInProgress;
    HttpServerRequestImpl requestInProgress;
    ServerWebSocketImpl ws;
    synchronized (this) {
      ws = this.ws;
      requestInProgress = this.requestInProgress;
      responseInProgress = this.responseInProgress;
      if (METRICS_ENABLED && metrics != null && ws != null) {
        metrics.disconnected(ws.getMetric());
        ws.setMetric(null);
      }
    }
    if (requestInProgress != null) {
      requestInProgress.context.dispatch(v -> {
        requestInProgress.handleException(CLOSED_EXCEPTION);
      });
    }
    if (responseInProgress != null && responseInProgress != requestInProgress) {
      responseInProgress.context.dispatch(v -> {
        responseInProgress.handleException(CLOSED_EXCEPTION);
      });
    }
    if (ws != null) {
      ws.context.dispatch(v -> ws.handleClosed());
    }
    super.handleClosed();
  }

  @Override
  protected void handleException(Throwable t) {
    super.handleException(t);
    HttpServerRequestImpl responseInProgress;
    HttpServerRequestImpl requestInProgress;
    ServerWebSocketImpl ws;
    synchronized (this) {
      ws = this.ws;
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
      ws.context.dispatch(v -> ws.handleException(t));
    }
  }

  @Override
  protected boolean supportsFileRegion() {
    return super.supportsFileRegion() && chctx.pipeline().get(HttpChunkContentCompressor.class) == null;
  }

  private void handleError(HttpObject obj) {
    DecoderResult result = obj.decoderResult();
    Throwable cause = result.cause();
    if (cause instanceof TooLongFrameException) {
      String causeMsg = cause.getMessage();
      HttpVersion version;
      if (obj instanceof HttpRequest) {
        version = ((HttpRequest) obj).protocolVersion();
      } else if (requestInProgress != null) {
        version = requestInProgress.version() == io.vertx.core.http.HttpVersion.HTTP_1_0 ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
      } else {
        version = HttpVersion.HTTP_1_1;
      }
      HttpResponseStatus status = causeMsg.startsWith("An HTTP line is larger than") ? HttpResponseStatus.REQUEST_URI_TOO_LONG : HttpResponseStatus.BAD_REQUEST;
      DefaultFullHttpResponse resp = new DefaultFullHttpResponse(version, status);
      ChannelPromise fut = chctx.newPromise();
      writeToChannel(resp, fut);
      fut.addListener(res -> {
        fail(result.cause());
      });
    } else {
      fail(result.cause());
    }
  }

  private long getBytes(Object obj) {
    if (obj == null) return 0;

    if (obj instanceof Buffer) {
      return ((Buffer) obj).length();
    } else if (obj instanceof ByteBuf) {
      return ((ByteBuf) obj).readableBytes();
    } else if (obj instanceof HttpContent) {
      return ((HttpContent) obj).content().readableBytes();
    } else if (obj instanceof WebSocketFrame) {
      return ((WebSocketFrame) obj).content().readableBytes();
    } else if (obj instanceof FileRegion) {
      return ((FileRegion) obj).count();
    } else if (obj instanceof ChunkedFile) {
      ChunkedFile file = (ChunkedFile) obj;
      return file.endOffset() - file.startOffset();
    } else {
      return -1;
    }
  }
}
