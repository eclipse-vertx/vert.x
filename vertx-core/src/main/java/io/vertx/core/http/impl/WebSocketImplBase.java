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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.http.WebSocketInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.internal.concurrent.InboundMessageChannel;
import io.vertx.core.net.impl.VertxConnection;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.net.impl.VertxHandler.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @param <S> self return type
 */
public abstract class WebSocketImplBase<S extends WebSocket> implements WebSocketInternal {

  private final boolean supportsContinuation;
  private final String textHandlerID;
  private final String binaryHandlerID;
  private final int maxWebSocketFrameSize;
  private final int maxWebSocketMessageSize;
  private final VertxConnection conn;
  private ChannelHandlerContext chctx;
  protected final ContextInternal context;
  private final InboundMessageChannel<WebSocketFrameInternal> pending;
  private MessageConsumer binaryHandlerRegistration;
  private MessageConsumer textHandlerRegistration;
  private String subProtocol;
  private Object metric;
  private Handler<Buffer> handler;
  private Handler<WebSocketFrameInternal> frameHandler;
  private FrameAggregator frameAggregator;
  private Handler<Buffer> pongHandler;
  private Handler<Void> drainHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean closing;
  private boolean closed;
  private Short closeStatusCode;
  private String closeReason;
  private MultiMap headers;

  WebSocketImplBase(ContextInternal context,
                    VertxConnection conn,
                    MultiMap headers,
                    boolean supportsContinuation,
                    int maxWebSocketFrameSize,
                    int maxWebSocketMessageSize,
                    boolean registerWebSocketWriteHandlers) {
    this.supportsContinuation = supportsContinuation;
    if (registerWebSocketWriteHandlers) {
      textHandlerID = "__vertx.ws." + UUID.randomUUID();
      binaryHandlerID = "__vertx.ws." + UUID.randomUUID();
    } else {
      textHandlerID = binaryHandlerID = null;
    }
    this.conn = conn;
    this.context = context;
    this.maxWebSocketFrameSize = maxWebSocketFrameSize;
    this.maxWebSocketMessageSize = maxWebSocketMessageSize;
    this.pending = new InboundMessageChannel<>(context.eventLoop(), context.executor()) {
      @Override
      protected void handleResume() {
        conn.doResume();
      }
      @Override
      protected void handlePause() {
        conn.doPause();
      }
      @Override
      protected void handleMessage(WebSocketFrameInternal msg) {
        receiveFrame(msg);
      }
    };
    this.chctx = conn.channelHandlerContext();
    this.headers = headers;
  }

  void registerHandler(EventBus eventBus) {
    if (binaryHandlerID != null) {
      Handler<Message<Buffer>> binaryHandler = msg -> writeBinaryFrameInternal(msg.body());
      Handler<Message<String>> textHandler = msg -> writeTextFrameInternal(msg.body());
      binaryHandlerRegistration = eventBus.<Buffer>localConsumer(binaryHandlerID).handler(binaryHandler);
      textHandlerRegistration = eventBus.<String>localConsumer(textHandlerID).handler(textHandler);
    }
  }

  final ContextInternal context() {
    return context;
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return chctx;
  }

  public String binaryHandlerID() {
    return binaryHandlerID;
  }

  public String textHandlerID() {
    return textHandlerID;
  }

  public boolean writeQueueFull() {
    synchronized (this) {
      checkClosed();
      return conn.writeQueueFull();
    }
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit, short statusCode, @Nullable String reason) {
    // Close the WebSocket by sending a close frame with specified payload
    ByteBuf byteBuf = HttpUtils.generateWSCloseFrameByteBuf(statusCode, reason);
    CloseWebSocketFrame frame = new CloseWebSocketFrame(true, 0, byteBuf);
    return conn.shutdown(frame, timeout, unit);
  }

  @Override
  public boolean isSsl() {
    return conn.isSsl();
  }

  @Override
  public SSLSession sslSession() {
    return conn.sslSession();
  }

  @Override
  public List<Certificate> peerCertificates() throws SSLPeerUnverifiedException {
    return conn.peerCertificates();
  }

  @Override
  public SocketAddress localAddress() {
    return conn.localAddress();
  }

  @Override
  public SocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  @Override
  public Future<Void> writeFinalTextFrame(String text) {
    return writeFrame(WebSocketFrame.textFrame(text, true));
  }

  @Override
  public Future<Void> writeFinalBinaryFrame(Buffer data) {
    return writeFrame(WebSocketFrame.binaryFrame(data, true));
  }

  @Override
  public String subProtocol() {
    synchronized(conn) {
      return subProtocol;
    }
  }

  void subProtocol(String subProtocol) {
    synchronized (this) {
      this.subProtocol = subProtocol;
    }
  }

  @Override
  public Short closeStatusCode() {
    synchronized (this) {
      return closeStatusCode;
    }
  }

  @Override
  public String closeReason() {
    synchronized (this) {
      return closeReason;
    }
  }

  @Override
  public MultiMap headers() {
    synchronized (this) {
      return headers;
    }
  }

  void headers(MultiMap headers) {
    synchronized (this) {
      this.headers = headers;
    }
  }

  @Override
  public Future<Void> writeBinaryMessage(Buffer data) {
    return writePartialMessage(WebSocketFrameType.BINARY, data, 0);
  }

  @Override
  public Future<Void> writeTextMessage(String text) {
    byte[] utf8Bytes = text.getBytes(StandardCharsets.UTF_8);
    boolean isFinal = utf8Bytes.length <= maxWebSocketFrameSize;
    if (isFinal) {
      return writeFrame(new WebSocketFrameImpl(WebSocketFrameType.TEXT, utf8Bytes, true));
    }
    // we could save to copy the byte[] if the unsafe heap version of Netty ByteBuf could expose wrapping byte[] directly
    return writePartialMessage(WebSocketFrameType.TEXT, Buffer.buffer(utf8Bytes), 0);
  }

  @Override
  public Future<Void> write(Buffer data) {
    return writeFrame(WebSocketFrame.binaryFrame(data, true));
  }

  @Override
  public Future<Void> writePing(Buffer data) {
    if (data.length() > maxWebSocketFrameSize || data.length() > 125) {
      return context.failedFuture("Ping cannot exceed maxWebSocketFrameSize or 125 bytes");
    }
    return writeFrame(WebSocketFrame.pingFrame(data));
  }

  @Override
  public Future<Void> writePong(Buffer data) {
    if (data.length() > maxWebSocketFrameSize || data.length() > 125) {
      return context.failedFuture("Pong cannot exceed maxWebSocketFrameSize or 125 bytes");
    }
    return writeFrame(WebSocketFrame.pongFrame(data));
  }

  /**
   * Splits the provided buffer into multiple frames (which do not exceed the maximum web socket frame size)
   * and writes them in order to the socket.
   */
  private Future<Void> writePartialMessage(WebSocketFrameType frameType, Buffer data, int offset) {
    int end = offset + maxWebSocketFrameSize;
    boolean isFinal;
    if (end >= data.length()) {
      end  = data.length();
      isFinal = true;
    } else {
      isFinal = false;
    }
    Buffer slice = data.slice(offset, end);
    WebSocketFrame frame;
    if (offset == 0 || !supportsContinuation) {
      frame = new WebSocketFrameImpl(frameType, ((BufferInternal)slice).getByteBuf(), isFinal);
    } else {
      frame = WebSocketFrame.continuationFrame(slice, isFinal);
    }
    int newOffset = offset + maxWebSocketFrameSize;
    if (isFinal) {
      return writeFrame(frame);
    } else {
      writeFrame(frame);
      return writePartialMessage(frameType, data, newOffset);
    }
  }

  @Override
  public Future<Void> writeFrame(WebSocketFrame frame) {
    synchronized (this) {
      if (isClosed()) {
        return context.failedFuture("WebSocket is closed");
      }
      PromiseInternal<Void> promise = context.promise();
      conn.writeToChannel(encodeFrame((WebSocketFrameImpl) frame), promise);
      return promise.future();
    }
  }

  private void writeBinaryFrameInternal(Buffer data) {
    writeFrame(new WebSocketFrameImpl(WebSocketFrameType.BINARY, ((BufferInternal)data).getByteBuf()));
  }

  private void writeTextFrameInternal(String str) {
    writeFrame(new WebSocketFrameImpl(str));
  }

  private io.netty.handler.codec.http.websocketx.WebSocketFrame encodeFrame(WebSocketFrameImpl frame) {
    ByteBuf buf = safeBuffer(frame.getBinaryData());
    switch (frame.type()) {
      case BINARY:
        return new BinaryWebSocketFrame(frame.isFinal(), 0, buf);
      case TEXT:
        return new TextWebSocketFrame(frame.isFinal(), 0, buf);
      case CLOSE:
        return new CloseWebSocketFrame(true, 0, buf);
      case CONTINUATION:
        return new ContinuationWebSocketFrame(frame.isFinal(), 0, buf);
      case PONG:
        return new PongWebSocketFrame(buf);
      case PING:
        return new PingWebSocketFrame(buf);
      default:
        throw new IllegalStateException("Unsupported WebSocket msg " + frame);
    }
  }

  void checkClosed() {
    if (isClosed()) {
      throw new IllegalStateException("WebSocket is closed");
    }
  }

  public boolean isClosed() {
    synchronized (this) {
      return closed || closeStatusCode != null;
    }
  }

  void handleFrame(WebSocketFrameInternal frame) {
    switch (frame.type()) {
      case PING:
        // Echo back the content of the PING frame as PONG frame as specified in RFC 6455 Section 5.5.2
        conn.writeToChannel(new PongWebSocketFrame(frame.getBinaryData().copy()));
        break;
      case PONG:
        Handler<Buffer> pongHandler = pongHandler();
        if (pongHandler != null) {
          context.emit(frame.binaryData(), pongHandler);
        }
        break;
      case CLOSE:
        handleCloseFrame(frame);
        break;
    }
    pending.write(frame);
  }

  private void handleCloseFrame(WebSocketFrameInternal closeFrame) {
    synchronized (this) {
      closeStatusCode = closeFrame.closeStatusCode();
      closeReason = closeFrame.closeReason();
    }
  }

  private void handleClose() {
    MessageConsumer<?> binaryConsumer;
    MessageConsumer<?> textConsumer;
    Handler<Void> closeHandler;
    Handler<Throwable> exceptionHandler;
    boolean graceful;
    synchronized (this) {
      graceful = this.closeStatusCode != null;
      closeHandler = this.closeHandler;
      exceptionHandler = this.exceptionHandler;
      binaryConsumer = this.binaryHandlerRegistration;
      textConsumer = this.textHandlerRegistration;
      this.binaryHandlerRegistration = null;
      this.textHandlerRegistration = null;
      this.closeHandler = null;
      this.closed = true;
    }
    if (binaryConsumer != null) {
      binaryConsumer.unregister();
    }
    if (textConsumer != null) {
      textConsumer.unregister();
    }
    if (exceptionHandler != null && !graceful) {
      context.emit(HttpUtils.CONNECTION_CLOSED_EXCEPTION, exceptionHandler);
    }
    if (closeHandler != null) {
      context.emit(null, closeHandler);
    }
  }

  private void receiveFrame(WebSocketFrameInternal frame) {
    Handler<WebSocketFrameInternal> frameAggregator;
    Handler<WebSocketFrameInternal> frameHandler;
    synchronized (this) {
      frameHandler = this.frameHandler;
      frameAggregator = this.frameAggregator;
    }
    if (frameAggregator != null) {
      context.dispatch(frame, frameAggregator);
    }
    if (frameHandler != null) {
      context.dispatch(frame, frameHandler);
    }
    switch(frame.type()) {
      case CLOSE:
        Handler<Void> endHandler = endHandler();
        if (endHandler != null) {
          context.dispatch(endHandler);
        }
        break;
      case TEXT:
      case BINARY:
      case CONTINUATION:
        Handler<Buffer> handler = handler();
        if (handler != null) {
          context.dispatch(frame.binaryData(), handler);
        }
        break;
      case PING:
      case PONG:
        fetch(1);
        break;
    }
  }

  /**
   * Close the connection.
   */
  void closeConnection() {
    chctx.close();
  }

  private class FrameAggregator implements Handler<WebSocketFrameInternal> {
    private Handler<String> textMessageHandler;
    private Handler<Buffer> binaryMessageHandler;

    private Buffer textMessageBuffer;
    private Buffer binaryMessageBuffer;

    @Override
    public void handle(WebSocketFrameInternal frame) {
      switch (frame.type()) {
        case TEXT:
          handleTextFrame(frame);
          break;
        case BINARY:
          handleBinaryFrame(frame);
          break;
        case CONTINUATION:
          if (textMessageBuffer != null && textMessageBuffer.length() > 0) {
            handleTextFrame(frame);
          } else if (binaryMessageBuffer != null && binaryMessageBuffer.length() > 0) {
            handleBinaryFrame(frame);
          }
          break;
      }
    }

    private void handleTextFrame(WebSocketFrameInternal frame) {
      Buffer frameBuffer = BufferInternal.buffer(frame.getBinaryData());
      if (textMessageBuffer == null) {
        textMessageBuffer = frameBuffer;
      } else {
        textMessageBuffer.appendBuffer(frameBuffer);
      }
      if (textMessageBuffer.length() > maxWebSocketMessageSize) {
        int len = textMessageBuffer.length() - frameBuffer.length();
        textMessageBuffer = null;
        String msg = "Cannot process text frame of size " + frameBuffer.length() + ", it would cause message buffer (size " +
            len + ") to overflow max message size of " + maxWebSocketMessageSize;
        handleException(new IllegalStateException(msg));
        return;
      }
      if (frame.isFinal()) {
        String fullMessage = textMessageBuffer.toString();
        textMessageBuffer = null;
        if (textMessageHandler != null) {
          textMessageHandler.handle(fullMessage);
        }
      }
    }

    private void handleBinaryFrame(WebSocketFrameInternal frame) {
      Buffer frameBuffer = BufferInternal.buffer(frame.getBinaryData());
      if (binaryMessageBuffer == null) {
        binaryMessageBuffer = frameBuffer;
      } else {
        binaryMessageBuffer.appendBuffer(frameBuffer);
      }
      if (binaryMessageBuffer.length() > maxWebSocketMessageSize) {
        int len = binaryMessageBuffer.length() - frameBuffer.length();
        binaryMessageBuffer = null;
        String msg = "Cannot process binary frame of size " + frameBuffer.length() + ", it would cause message buffer (size " +
            len + ") to overflow max message size of " + maxWebSocketMessageSize;
        handleException(new IllegalStateException(msg));
        return;
      }
      if (frame.isFinal()) {
        Buffer fullMessage = binaryMessageBuffer.copy();
        binaryMessageBuffer = null;
        if (binaryMessageHandler != null) {
          binaryMessageHandler.handle(fullMessage);
        }
      }
    }
  }

  @Override
  public S frameHandler(Handler<WebSocketFrame> handler) {
    synchronized (this) {
      checkClosed();
      this.frameHandler = (Handler)handler;
      return (S) this;
    }
  }

  @Override
  public WebSocket textMessageHandler(Handler<String> handler) {
    synchronized (this) {
      checkClosed();
      if (handler != null) {
        if (frameAggregator == null) {
          frameAggregator = new FrameAggregator();
        }
        frameAggregator.textMessageHandler = handler;
      } else {
        if (frameAggregator != null) {
          if (frameAggregator.binaryMessageHandler == null) {
            frameAggregator = null;
          } else {
            frameAggregator.textMessageHandler = null;
            frameAggregator.textMessageBuffer = null;
          }
        }
      }
      return this;
    }
  }

  @Override
  public S binaryMessageHandler(Handler<Buffer> handler) {
    synchronized (this) {
      checkClosed();
      if (handler != null) {
        if (frameAggregator == null) {
          frameAggregator = new FrameAggregator();
        }
        frameAggregator.binaryMessageHandler = handler;
      } else {
        if (frameAggregator != null) {
          if (frameAggregator.textMessageHandler == null) {
            frameAggregator = null;
          } else {
            frameAggregator.binaryMessageHandler = null;
            frameAggregator.binaryMessageBuffer = null;
          }
        }
      }
      return (S) this;
    }
  }

  @Override
  public WebSocket pongHandler(Handler<Buffer> handler) {
    synchronized (this) {
      checkClosed();
      this.pongHandler = handler;
      return (S) this;
    }
  }

  private Handler<Buffer> pongHandler() {
    synchronized (this) {
      return pongHandler;
    }
  }

  void handleWriteQueueDrained(Void v) {
    Handler<Void> handler;
    synchronized (this) {
      handler = drainHandler;
    }
    if (handler != null) {
      context.dispatch(handler);
    }
  }

  void handleException(Throwable t) {
    Handler<Throwable> handler;
    synchronized (this) {
      handler = exceptionHandler;
    }
    if (handler != null) {
      context.dispatch(t, handler);
    }
  }

  void handleConnectionClosed() {
    handleClose();
  }

  synchronized void setMetric(Object metric) {
    this.metric = metric;
  }

  synchronized Object getMetric() {
    return metric;
  }

  @Override
  public S handler(Handler<Buffer> handler) {
    synchronized (this) {
      if (handler != null) {
        checkClosed();
      }
      this.handler = handler;
      return (S) this;
    }
  }

  private Handler<Buffer> handler() {
    synchronized (this) {
      return handler;
    }
  }

  @Override
  public S endHandler(Handler<Void> handler) {
    synchronized (this) {
      if (handler != null) {
        checkClosed();
      }
      this.endHandler = handler;
      return (S) this;
    }
  }

  private Handler<Void> endHandler() {
    synchronized (this) {
      return endHandler;
    }
  }

  @Override
  public S exceptionHandler(Handler<Throwable> handler) {
    synchronized (this) {
      if (handler != null) {
        checkClosed();
      }
      this.exceptionHandler = handler;
      return (S) this;
    }
  }

  @Override
  public S closeHandler(Handler<Void> handler) {
    synchronized (this) {
      checkClosed();
      this.closeHandler = handler;
      return (S) this;
    }
  }

  @Override
  public S shutdownHandler(Handler<Void> handler) {
    synchronized (this) {
      checkClosed();
    }
    conn.shutdownHandler(handler);
    return (S) this;
  }

  @Override
  public S drainHandler(Handler<Void> handler) {
    synchronized (this) {
      checkClosed();
      this.drainHandler = handler;
      return (S) this;
    }
  }

  @Override
  public S pause() {
    pending.pause();
    return (S) this;
  }

  @Override
  public S fetch(long amount) {
    pending.fetch(amount);
    return (S) this;
  }

  @Override
  public S setWriteQueueMaxSize(int maxSize) {
    synchronized (this) {
      checkClosed();
      conn.doSetWriteQueueMaxSize(maxSize);
      return (S) this;
    }
  }

  @Override
  public Future<Void> end() {
    return close();
  }
}
