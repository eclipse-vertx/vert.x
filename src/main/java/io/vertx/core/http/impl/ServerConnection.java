/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
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
import io.vertx.core.VoidHandler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.VertxNetHandler;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 *
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ServerConnection extends ConnectionBase {

  private static final Logger log = LoggerFactory.getLogger(ServerConnection.class);

  private static final int CHANNEL_PAUSE_QUEUE_SIZE = 5;

  private final Queue<Object> pending = new ArrayDeque<>(8);
  private final String serverOrigin;
  private final HttpServerImpl server;
  private final WebSocketServerHandshaker handshaker;
  private final HttpServerMetrics metrics;

  private Object requestMetric;
  private Handler<HttpServerRequest> requestHandler;
  private Handler<ServerWebSocket> wsHandler;
  private HttpServerRequestImpl currentRequest;
  private HttpServerResponseImpl pendingResponse;
  private ServerWebSocketImpl ws;
  private ChannelFuture lastWriteFuture;
  private boolean channelPaused;
  private boolean paused;
  private boolean sentCheck;
  private long bytesRead;
  private long bytesWritten;
  private Object metric;

  ServerConnection(VertxInternal vertx, HttpServerImpl server, Channel channel, ContextImpl context, String serverOrigin,
                   WebSocketServerHandshaker handshaker, HttpServerMetrics metrics) {
    super(vertx, channel, context, metrics);
    this.serverOrigin = serverOrigin;
    this.server = server;
    this.handshaker = handshaker;
    this.metrics = metrics;
  }

  @Override
  protected synchronized Object metric() {
    return metric;
  }

  synchronized void setMetric(Object metric) {
    this.metric = metric;
  }

  public synchronized void pause() {
    if (!paused) {
      paused = true;
    }
  }

  public synchronized void resume() {
    if (paused) {
      paused = false;
      checkNextTick();
    }
  }

  synchronized void handleMessage(Object msg) {
    if (paused || (pendingResponse != null && msg instanceof HttpRequest) || !pending.isEmpty()) {
      //We queue requests if paused or a request is in progress to prevent responses being written in the wrong order
      pending.add(msg);
      if (pending.size() == CHANNEL_PAUSE_QUEUE_SIZE) {
        //We pause the channel too, to prevent the queue growing too large, but we don't do this
        //until the queue reaches a certain size, to avoid pausing it too often
        super.doPause();
        channelPaused = true;
      }
    } else {
      processMessage(msg);
    }
  }

  synchronized void responseComplete() {
    if (metrics.isEnabled()) {
      reportBytesWritten(bytesWritten);
      bytesWritten = 0;
      metrics.responseEnd(requestMetric, pendingResponse);
    }
    pendingResponse = null;
    checkNextTick();
  }

  synchronized void requestHandler(Handler<HttpServerRequest> handler) {
    this.requestHandler = handler;
  }

  synchronized void wsHandler(Handler<ServerWebSocket> handler) {
    this.wsHandler = handler;
  }

  String getServerOrigin() {
    return serverOrigin;
  }

  Vertx vertx() {
    return vertx;
  }

  @Override
  public ChannelFuture writeToChannel(Object obj) {
    if (metrics.isEnabled()) {
      long bytes = getBytes(obj);
      if (bytes == -1) {
        log.warn("Metrics could not be updated to include bytes written because of unknown object " + obj.getClass() + " being written.");
      } else {
        bytesWritten += bytes;
      }
    }
    return lastWriteFuture = super.writeToChannel(obj);
  }

  ServerWebSocket upgrade(HttpServerRequest request, HttpRequest nettyReq) {
    if (ws != null) {
      return ws;
    }
    if (handshaker == null || !(nettyReq instanceof FullHttpRequest)) {
      throw new IllegalStateException("Can't upgrade this request");
    }
    ws = new ServerWebSocketImpl(vertx, request.uri(), request.path(),
      request.query(), request.headers(), this, handshaker.version() != WebSocketVersion.V00,
      null, server.options().getMaxWebsocketFrameSize());
    try {
      handshaker.handshake(channel, (FullHttpRequest)nettyReq);
    } catch (WebSocketHandshakeException e) {
      handleException(e);
    } catch (Exception e) {
      log.error("Failed to generate shake response", e);
    }
    ChannelHandler handler = channel.pipeline().get(HttpChunkContentCompressor.class);
    if (handler != null) {
      // remove compressor as its not needed anymore once connection was upgraded to websockets
      channel.pipeline().remove(handler);
    }
    server.connectionMap().put(channel, this);
    return ws;
  }

  NetSocket createNetSocket() {
    NetSocketImpl socket = new NetSocketImpl(vertx, channel, context, server.getSslHelper(), false, metrics);
    Map<Channel, NetSocketImpl> connectionMap = new HashMap<Channel, NetSocketImpl>(1);
    connectionMap.put(channel, socket);

    // Flush out all pending data
    endReadAndFlush();

    // remove old http handlers and replace the old handler with one that handle plain sockets
    ChannelPipeline pipeline = channel.pipeline();
    ChannelHandler compressor = pipeline.get(HttpChunkContentCompressor.class);
    if (compressor != null) {
      pipeline.remove(compressor);
    }

    pipeline.remove("httpDecoder");
    if (pipeline.get("chunkedWriter") != null) {
      pipeline.remove("chunkedWriter");
    }

    channel.pipeline().replace("handler", "handler", new VertxNetHandler(vertx, connectionMap) {
      @Override
      public void exceptionCaught(ChannelHandlerContext chctx, Throwable t) throws Exception {
        // remove from the real mapping
        server.removeChannel(channel);
        super.exceptionCaught(chctx, t);
      }

      @Override
      public void channelInactive(ChannelHandlerContext chctx) throws Exception {
        // remove from the real mapping
        server.removeChannel(channel);
        super.channelInactive(chctx);
      }

      @Override
      public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
        if (msg instanceof HttpContent) {
          ReferenceCountUtil.release(msg);
          return;
        }
        super.channelRead(chctx, msg);
      }
    });

    // check if the encoder can be removed yet or not.
    if (lastWriteFuture == null) {
      channel.pipeline().remove("httpEncoder");
    } else {
      lastWriteFuture.addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
          channel.pipeline().remove("httpEncoder");
        }
      });
    }
    return socket;
  }

  private void handleRequest(HttpServerRequestImpl req, HttpServerResponseImpl resp) {
    this.currentRequest = req;
    pendingResponse = resp;
    requestMetric = metrics.requestBegin(metric, req);
    if (requestHandler != null) {
      requestHandler.handle(req);
    }
  }

  private void handleChunk(Buffer chunk) {
    if (metrics.isEnabled()) {
      bytesRead += chunk.length();
    }
    currentRequest.handleData(chunk);
  }

  private void handleEnd() {
    currentRequest.handleEnd();
    reportBytesRead(bytesRead);
    currentRequest = null;
    bytesRead = 0;
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
      handshaker.close(channel, new CloseWebSocketFrame(1000, null));
    }
  }


  synchronized void handleWebsocketConnect(ServerWebSocketImpl ws) {
    if (wsHandler != null) {
      wsHandler.handle(ws);
      this.ws = ws;
    }
  }

  synchronized private void handleWsFrame(WebSocketFrameInternal frame) {
    if (ws != null) {
      ws.handleFrame(frame);
    }
  }

  synchronized protected void handleClosed() {
    if (ws != null) {
      metrics.disconnected(ws.metric);
      ws.metric = null;
    }
    super.handleClosed();
    if (ws != null) {
      ws.handleClosed();
    }
    if (pendingResponse != null) {
      pendingResponse.handleClosed();
    }
  }

  protected ContextImpl getContext() {
    return super.getContext();
  }

  @Override
  protected synchronized void handleException(Throwable t) {
    super.handleException(t);
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
    return super.supportsFileRegion() && channel.pipeline().get(HttpChunkContentCompressor.class) == null;
  }

  protected ChannelFuture sendFile(RandomAccessFile file, long fileLength) throws IOException {
    return super.sendFile(file, fileLength);
  }

  private void processMessage(Object msg) {
    if (msg instanceof HttpRequest) {
      HttpRequest request = (HttpRequest) msg;
      HttpServerResponseImpl resp = new HttpServerResponseImpl(vertx, this, request);
      HttpServerRequestImpl req = new HttpServerRequestImpl(this, request, resp);
      handleRequest(req, resp);
    }
    if (msg instanceof HttpContent) {
        HttpContent chunk = (HttpContent) msg;
      if (chunk.content().isReadable()) {
        Buffer buff = Buffer.buffer(chunk.content());
        handleChunk(buff);
      }

      //TODO chunk trailers
      if (msg instanceof LastHttpContent) {
        if (!paused) {
          handleEnd();
        } else {
          // Requeue
          pending.add(LastHttpContent.EMPTY_LAST_CONTENT);
        }
      }
    } else if (msg instanceof WebSocketFrameInternal) {
      WebSocketFrameInternal frame = (WebSocketFrameInternal) msg;
      handleWsFrame(frame);
    }

    checkNextTick();
  }

  private void checkNextTick() {
    // Check if there are more pending messages in the queue that can be processed next time around
    if (!pending.isEmpty() && !sentCheck && !paused && (pendingResponse == null || pending.peek() instanceof HttpContent)) {
      sentCheck = true;
      vertx.runOnContext(new VoidHandler() {
        public void handle() {
          sentCheck = false;
          if (!paused) {
            Object msg = pending.poll();
            if (msg != null) {
              processMessage(msg);
            }
            if (channelPaused && pending.isEmpty()) {
              //Resume the actual channel
              ServerConnection.super.doResume();
              channelPaused = false;
            }
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
      return ((WebSocketFrame) obj).binaryData().length();
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
