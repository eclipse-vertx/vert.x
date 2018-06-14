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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.net.impl.VertxNetHandler;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 *
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Http1xServerConnection extends Http1xConnectionBase implements HttpConnection {

  private static final Logger log = LoggerFactory.getLogger(Http1xServerConnection.class);

  private static final int CHANNEL_PAUSE_QUEUE_SIZE = 5;

  private final String serverOrigin;
  private final SSLHelper sslHelper;
  final HttpServerOptions options;
  private WebSocketServerHandshaker handshaker;
  private final HttpServerMetrics metrics;
  private boolean requestFailed;
  private Object requestMetric;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> wsHandler;
  private HttpServerRequestImpl currentRequest;
  private HttpServerResponseImpl pendingResponse;
  private ServerWebSocketImpl ws;
  private long bytesRead;
  private long bytesWritten;

  // (queueing == true) <=> (paused || pending.size() > 0)
  private final Deque<Object> pending = new ArrayDeque<>(8);
  private boolean paused;
  private boolean sentCheck;
  private boolean queueing;
  private boolean channelPaused;

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
  }

  @Override
  public HttpServerMetrics metrics() {
    return metrics;
  }

  synchronized void pause() {
    if (!paused) {
      paused = true;
      queueing = true;
    }
  }

  synchronized void resume() {
    if (paused) {
      paused = false;
      if (pending.isEmpty()) {
        unsetQueueing();
      } else {
        checkNextTick();
      }
    }
  }

  synchronized void handleMessage(Object msg) {
    if (queueing) {
      enqueue(msg);
    } else {
      processMessage(msg);
    }
  }

  /**
   * Set in non queueing mode, i.e (pending.size() == 0 && !paused)
   */
  private void unsetQueueing() {
    queueing = false;
    if (channelPaused) {
      // Resume the actual channel
      channelPaused = false;
      doResume();
    }
  }

  private void enqueue(Object msg) {
    // We queue requests if paused or a request is in progress to prevent responses being written in the wrong order
    queueing = true;
    pending.add(msg);
    if (pending.size() == CHANNEL_PAUSE_QUEUE_SIZE) {
      // We pause the channel too, to prevent the queue growing too large, but we don't do this
      // until the queue reaches a certain size, to avoid pausing it too often
      doPause();
      channelPaused = true;
    }
  }

  synchronized void responseComplete() {
    if (METRICS_ENABLED && metrics != null) {
      reportBytesWritten(bytesWritten);
      bytesWritten = 0;
      if (requestFailed) {
        metrics.requestReset(requestMetric);
        requestFailed = false;
      } else {
        metrics.responseEnd(requestMetric, pendingResponse);
      }
    }
    pendingResponse = null;
    if (currentRequest == null && paused) {
      resume();
    }
  }

  synchronized void requestHandlers(HttpHandlers handlers) {
    Handler<HttpServerRequest> handler = handlers.requestHandler;
    if (handlers.connectionHandler != null) {
      handler = req -> {
        requestHandler = handlers.requestHandler;
        handlers.connectionHandler.handle(this);
        handlers.requestHandler.handle(req);
      };
    }
    requestHandler = handler;
    exceptionHandler(handlers.exceptionHandler);
  }

  synchronized void wsHandler(WebSocketServerHandshaker handshaker, HttpHandlers handlers) {
    this.handshaker = handshaker;
    this.wsHandler = handlers.wsHandler;
    exceptionHandler(handlers.exceptionHandler);
  }

  String getServerOrigin() {
    return serverOrigin;
  }

  Vertx vertx() {
    return vertx;
  }

  @Override
  public void writeToChannel(Object msg, ChannelPromise promise) {
    if (METRICS_ENABLED && metrics != null) {
      long bytes = getBytes(msg);
      if (bytes == -1) {
        log.warn("Metrics could not be updated to include bytes written because of unknown object " + msg.getClass() + " being written.");
      } else {
        bytesWritten += bytes;
      }
    }
    super.writeToChannel(msg, promise);
  }

  ServerWebSocket upgrade(HttpServerRequest request, HttpRequest nettyReq) {
    if (ws != null) {
      return ws;
    }
    Http1xServerHandler serverHandler = (Http1xServerHandler) chctx.pipeline().get("handler");
    handshaker = serverHandler.createHandshaker(this, nettyReq);
    if (handshaker == null) {
      throw new IllegalStateException("Can't upgrade this request");
    }

    ws = new ServerWebSocketImpl(vertx, request.uri(), request.path(),
      request.query(), request.headers(), this, handshaker.version() != WebSocketVersion.V00,
      null, options.getMaxWebsocketFrameSize(), options.getMaxWebsocketMessageSize());
    try {
      handshaker.handshake(chctx.channel(), nettyReq);
    } catch (WebSocketHandshakeException e) {
      handleException(e);
    } catch (Exception e) {
      log.error("Failed to generate shake response", e);
    }
    ChannelHandler handler = chctx.pipeline().get(HttpChunkContentCompressor.class);
    if (handler != null) {
      // remove compressor as its not needed anymore once connection was upgraded to websockets
      chctx.pipeline().remove(handler);
    }
    if (METRICS_ENABLED && metrics != null) {
      ws.setMetric(metrics.upgrade(requestMetric, ws));
    }
    ws.registerHandler(vertx.eventBus());
    return ws;
  }

  NetSocket createNetSocket() {
    NetSocketImpl socket = new NetSocketImpl(vertx, chctx, context, sslHelper, metrics);
    socket.metric(metric());
    Map<Channel, NetSocketImpl> connectionMap = new HashMap<>(1);
    connectionMap.put(chctx.channel(), socket);

    // Flush out all pending data
    endReadAndFlush();

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

    chctx.pipeline().replace("handler", "handler", new VertxNetHandler(socket) {
      @Override
      public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
        if (msg instanceof HttpContent) {
          ReferenceCountUtil.release(msg);
          return;
        }
        super.channelRead(chctx, msg);
      }

      @Override
      protected void handleMessage(NetSocketImpl connection, Object msg) {
        connection.handleMessageReceived(msg);
      }
    }.removeHandler(sock -> connectionMap.remove(chctx.channel())));

    // check if the encoder can be removed yet or not.
    chctx.pipeline().remove("httpEncoder");
    return socket;
  }

  private void handleChunk(Buffer chunk) {
    if (METRICS_ENABLED && metrics != null) {
      bytesRead += chunk.length();
    }
    currentRequest.handleData(chunk);
  }

  @Override
  public synchronized void handleInterestedOpsChanged() {
    if (!isNotWritable()) {
      if (pendingResponse != null) {
        pendingResponse.handleDrained();
      } else if (ws != null) {
        ws.writable();
      }
    }
  }

  @Override
  public void close() {
    if (handshaker == null) {
      super.close();
    } else {
      endReadAndFlush();
      handshaker.close(chctx.channel(), new CloseWebSocketFrame(true, 0, 1000, null));
    }
  }

  @Override
  public void closeWithPayload(ByteBuf byteBuf) {
    if (handshaker == null) {
      super.close();
    } else {
      endReadAndFlush();
      handshaker.close(chctx.channel(), new CloseWebSocketFrame(true, 0, byteBuf));
    }
  }


  synchronized void handleWebsocketConnect(ServerWebSocketImpl ws) {
    if (wsHandler != null) {
      wsHandler.handle(ws);
      this.ws = ws;
    }
  }

  void write100Continue() {
    chctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
  }

  synchronized protected void handleClosed() {
    if (METRICS_ENABLED && metrics != null && ws != null) {
      metrics.disconnected(ws.getMetric());
      ws.setMetric(null);
    }
    if (ws != null) {
      ws.handleClosed();
    }
    if (currentRequest != null) {
      currentRequest.handleException(CLOSED_EXCEPTION);
    }
    if (pendingResponse != null) {
      if (METRICS_ENABLED && metrics != null) {
        metrics.requestReset(requestMetric);
      }
      pendingResponse.handleClosed();
    }
    super.handleClosed();
  }

  @Override
  protected synchronized void handleException(Throwable t) {
    super.handleException(t);
    if (METRICS_ENABLED && metrics != null) {
      requestFailed = true;
    }
    if (currentRequest != null) {
      currentRequest.handleException(t);
    }
    if (pendingResponse != null) {
      pendingResponse.handleException(t);
    }
    if (ws != null) {
      ws.handleException(t);
    }
  }

  protected void addFuture(Handler<AsyncResult<Void>> completionHandler, ChannelFuture future) {
    super.addFuture(completionHandler, future);
  }

  @Override
  protected boolean supportsFileRegion() {
    return super.supportsFileRegion() && chctx.pipeline().get(HttpChunkContentCompressor.class) == null;
  }

  protected ChannelFuture sendFile(RandomAccessFile file, long offset, long length) throws IOException {
    return super.sendFile(file, offset, length);
  }

  private void handleError(HttpObject obj) {
    DecoderResult result = obj.decoderResult();
    Throwable cause = result.cause();
    if (cause instanceof TooLongFrameException) {
      String causeMsg = cause.getMessage();
      HttpVersion version;
      if (obj instanceof HttpRequest) {
        version = ((HttpRequest) obj).protocolVersion();
      } else if (currentRequest != null) {
        version = currentRequest.version() == io.vertx.core.http.HttpVersion.HTTP_1_0 ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
      } else {
        version = HttpVersion.HTTP_1_1;
      }
      HttpResponseStatus status = causeMsg.startsWith("An HTTP line is larger than") ? HttpResponseStatus.REQUEST_URI_TOO_LONG : HttpResponseStatus.BAD_REQUEST;
      DefaultFullHttpResponse resp = new DefaultFullHttpResponse(version, status);
      ChannelPromise fut = chctx.newPromise();
      writeToChannel(resp, fut);
      fut.addListener(res -> {
        if (res.isSuccess()) {
          // That will close the connection as it is considered as unusable
          chctx.pipeline().fireExceptionCaught(result.cause());
        }
      });
    } else {
      // That will close the connection as it is considered as unusable
      chctx.pipeline().fireExceptionCaught(result.cause());
    }
  }

  private void processMessage(Object msg) {
    if (msg instanceof HttpRequest) {
      HttpRequest request = (HttpRequest) msg;
      if (request.decoderResult().isFailure()) {
        handleError(request);
        return;
      }
      if (options.isHandle100ContinueAutomatically() && HttpUtil.is100ContinueExpected(request)) {
        write100Continue();
      }
      HttpServerResponseImpl resp = new HttpServerResponseImpl(vertx, this, request);
      HttpServerRequestImpl req = new HttpServerRequestImpl(this, request, resp);
      currentRequest = req;
      pendingResponse = resp;
      if (METRICS_ENABLED && metrics != null) {
        requestMetric = metrics.requestBegin(metric(), req);
      }
      if (requestHandler != null) {
        requestHandler.handle(req);
      }
    } else if (msg == LastHttpContent.EMPTY_LAST_CONTENT) {
      handleEnd();
    } else if (msg instanceof HttpContent) {
      handleContent(msg);
    } else {
      handleOther(msg);
    }
  }

  private void handleContent(Object msg) {
    HttpContent content = (HttpContent) msg;
    if (content.decoderResult().isFailure()) {
      handleError(content);
      return;
    }
    ByteBuf chunk = content.content();
    if (chunk.isReadable()) {
      Buffer buff = Buffer.buffer(chunk);
      handleChunk(buff);
    }
    //TODO chunk trailers
    if (content instanceof LastHttpContent) {
      handleEnd();
    }
  }

  private void handleEnd() {
    currentRequest.handleEnd();
    if (METRICS_ENABLED) {
      reportBytesRead(bytesRead);
      bytesRead = 0;
    }
    currentRequest = null;
    if (pendingResponse != null) {
      pause();
    } else if (paused) {
      resume();
    }
  }

  private void handleOther(Object msg) {
    if (msg instanceof WebSocketFrameInternal) {
      WebSocketFrameInternal frame = (WebSocketFrameInternal) msg;
      if (ws != null) {
        ws.handleFrame(frame);
      }
    }
  }

  private void checkNextTick() {
    // Check if there are more pending messages in the queue that can be processed next time around
    if (!paused && queueing && !sentCheck) {
      sentCheck = true;
      context.runOnContext(v -> {
        synchronized (Http1xServerConnection.this) {
          sentCheck = false;
          if (!paused) {
            // The only place we poll the pending queue, so we are sure that pending.size() > 0
            // since we got there because queueing was true
            Object msg = pending.poll();
            if (pending.isEmpty()) {
              // paused == false && pending.size() == 0 => queueing == false
              unsetQueueing();
            }
            // Process message, it might pause the connection
            processMessage(msg);
            // Check next tick in case we still have pending messages and the connection is not paused
            checkNextTick();
          }
        }
      });
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
      return ((WebSocketFrameInternal) obj).length();
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
