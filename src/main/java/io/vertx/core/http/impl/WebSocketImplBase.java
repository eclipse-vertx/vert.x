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
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.http.WebSocketFrameType;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.impl.InboundBuffer;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.security.cert.Certificate;
import java.util.List;
import java.util.UUID;

import static io.vertx.core.net.impl.VertxHandler.*;

/**
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 * <p>
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @param <S> self return type
 */
public abstract class WebSocketImplBase<S extends WebSocketBase> implements WebSocketInternal {

  private final boolean supportsContinuation;
  private final String textHandlerID;
  private final String binaryHandlerID;
  private final int maxWebSocketFrameSize;
  private final int maxWebSocketMessageSize;
  private final InboundBuffer<WebSocketFrameInternal> pending;
  private ChannelHandlerContext chctx;
  protected final ContextInternal context;
  private MessageConsumer binaryHandlerRegistration;
  private MessageConsumer textHandlerRegistration;
  private String subProtocol;
  private Object metric;
  private Handler<Buffer> handler;
  private Handler<WebSocketFrameInternal> frameHandler;
  private Handler<Buffer> pongHandler;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  protected final Http1xConnectionBase conn;
  private boolean writable;
  private boolean closed;
  private Short closeStatusCode;
  private String closeReason;
  private long closeTimeoutID = -1L;
  private MultiMap headers;

  WebSocketImplBase(ContextInternal context, Http1xConnectionBase conn, boolean supportsContinuation,
                    int maxWebSocketFrameSize, int maxWebSocketMessageSize, boolean registerWebSocketWriteHandlers) {
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
    this.pending = new InboundBuffer<>(context);
    this.writable = !conn.isNotWritable();
    this.chctx = conn.channelHandlerContext();

    pending.handler(this::receiveFrame);
    pending.drainHandler(v -> conn.doResume());
  }

  void registerHandler(EventBus eventBus) {
    if (binaryHandlerID != null) {
      Handler<Message<Buffer>> binaryHandler = msg -> writeBinaryFrameInternal(msg.body());
      Handler<Message<String>> textHandler = msg -> writeTextFrameInternal(msg.body());
      binaryHandlerRegistration = eventBus.<Buffer>localConsumer(binaryHandlerID).handler(binaryHandler);
      textHandlerRegistration = eventBus.<String>localConsumer(textHandlerID).handler(textHandler);
    }
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return chctx;
  }

  @Override
  public HttpConnection connection() {
    return conn;
  }

  public String binaryHandlerID() {
    return binaryHandlerID;
  }

  public String textHandlerID() {
    return textHandlerID;
  }

  public boolean writeQueueFull() {
    synchronized (conn) {
      checkClosed();
      return conn.isNotWritable();
    }
  }

  @Override
  public Future<Void> close() {
    return close((short) 1000, (String) null);
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    Future<Void> future = close();
    if (handler != null) {
      future.onComplete(handler);
    }
  }

  @Override
  public Future<Void> close(short statusCode) {
    return close(statusCode, (String) null);
  }

  @Override
  public void close(short statusCode, Handler<AsyncResult<Void>> handler) {
    Future<Void> future = close(statusCode, (String) null);
    if (handler != null) {
      future.onComplete(handler);
    }
  }

  @Override
  public void close(short statusCode, @Nullable String reason, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = close(statusCode, reason);
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  @Override
  public Future<Void> close(short statusCode, String reason) {
    boolean sendCloseFrame;
    synchronized (conn) {
      if (sendCloseFrame = closeStatusCode == null) {
        closeStatusCode = statusCode;
        closeReason = reason;
      }
    }
    if (sendCloseFrame) {
      // Close the WebSocket by sending a close frame with specified payload
      ByteBuf byteBuf = HttpUtils.generateWSCloseFrameByteBuf(statusCode, reason);
      CloseWebSocketFrame frame = new CloseWebSocketFrame(true, 0, byteBuf);
      PromiseInternal<Void> promise = context.promise();
      conn.writeToChannel(frame, promise);
      return promise;
    } else {
      return context.succeededFuture();
    }
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
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.peerCertificateChain();
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
    Promise<Void> promise = context.promise();
    writeFinalTextFrame(text, promise);
    return promise.future();
  }

  @Override
  public S writeFinalTextFrame(String text, Handler<AsyncResult<Void>> handler) {
    return writeFrame(WebSocketFrame.textFrame(text, true), handler);
  }

  @Override
  public Future<Void> writeFinalBinaryFrame(Buffer data) {
    Promise<Void> promise = context.promise();
    writeFinalBinaryFrame(data, promise);
    return promise.future();
  }

  @Override
  public S writeFinalBinaryFrame(Buffer data, Handler<AsyncResult<Void>> handler) {
    return writeFrame(WebSocketFrame.binaryFrame(data, true), handler);
  }

  @Override
  public String subProtocol() {
    synchronized(conn) {
      return subProtocol;
    }
  }

  void subProtocol(String subProtocol) {
    synchronized (conn) {
      this.subProtocol = subProtocol;
    }
  }

  @Override
  public Short closeStatusCode() {
    synchronized (conn) {
      return closeStatusCode;
    }
  }

  @Override
  public String closeReason() {
    synchronized (conn) {
      return closeReason;
    }
  }

  @Override
  public MultiMap headers() {
    synchronized(conn) {
      return headers;
    }
  }

  void headers(MultiMap responseHeaders) {
    synchronized(conn) {
      this.headers = responseHeaders;
    }
  }

  @Override
  public Future<Void> writeBinaryMessage(Buffer data) {
    return writePartialMessage(WebSocketFrameType.BINARY, data, 0);
  }

  @Override
  public final S writeBinaryMessage(Buffer data, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = writeBinaryMessage(data);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return (S) this;
  }

  @Override
  public Future<Void> writeTextMessage(String text) {
    return writePartialMessage(WebSocketFrameType.TEXT, Buffer.buffer(text), 0);
  }

  @Override
  public final S writeTextMessage(String text, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = writeTextMessage(text);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return (S) this;
  }

  @Override
  public Future<Void> write(Buffer data) {
    return writeFrame(WebSocketFrame.binaryFrame(data, true));
  }

  @Override
  public final void write(Buffer data, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = write(data);
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  @Override
  public Future<Void> writePing(Buffer data) {
    if (data.length() > maxWebSocketFrameSize || data.length() > 125) {
      return context.failedFuture("Ping cannot exceed maxWebSocketFrameSize or 125 bytes");
    }
    return writeFrame(WebSocketFrame.pingFrame(data));
  }

  @Override
  public final WebSocketBase writePing(Buffer data, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = writePing(data);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return (S) this;
  }

  @Override
  public Future<Void> writePong(Buffer data) {
    if (data.length() > maxWebSocketFrameSize || data.length() > 125) {
      return context.failedFuture("Pong cannot exceed maxWebSocketFrameSize or 125 bytes");
    }
    return writeFrame(WebSocketFrame.pongFrame(data));
  }

  @Override
  public final WebSocketBase writePong(Buffer data, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = writePong(data);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return (S) this;
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
      frame = new WebSocketFrameImpl(frameType, slice.getByteBuf(), isFinal);
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
    synchronized (conn) {
      if (isClosed()) {
        return context.failedFuture("WebSocket is closed");
      }
      PromiseInternal<Void> promise = context.promise();
      conn.writeToChannel(encodeFrame((WebSocketFrameImpl) frame), promise);
      return promise.future();
    }
  }

  public final S writeFrame(WebSocketFrame frame, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = writeFrame(frame);
    if (handler != null) {
      fut.onComplete(handler);
    }
    return (S) this;
  }

  private void writeBinaryFrameInternal(Buffer data) {
    writeFrame(new WebSocketFrameImpl(WebSocketFrameType.BINARY, data.getByteBuf()));
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
    synchronized (conn) {
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
          context.dispatch(frame.binaryData(), pongHandler);
        }
        break;
      case CLOSE:
        handleCloseFrame(frame);
        break;
    }
    if (!pending.write(frame)) {
      conn.doPause();
    }
  }

  private void handleCloseFrame(WebSocketFrameInternal closeFrame) {
    boolean echo;
    synchronized (conn) {
      echo = closeStatusCode == null;
      closed = true;
      closeStatusCode = closeFrame.closeStatusCode();
      closeReason = closeFrame.closeReason();
    }
    handleClose(true);
    if (echo) {
      ChannelPromise fut = conn.channelFuture();
      conn.writeToChannel(new CloseWebSocketFrame(closeStatusCode, closeReason), fut);
      fut.addListener(v -> handleCloseConnection());
    } else {
      handleCloseConnection();
    }
  }

  protected void handleClose(boolean graceful) {
    MessageConsumer<?> binaryConsumer;
    MessageConsumer<?> textConsumer;
    Handler<Void> closeHandler;
    Handler<Throwable> exceptionHandler;
    synchronized (conn) {
      closeHandler = this.closeHandler;
      exceptionHandler = this.exceptionHandler;
      binaryConsumer = this.binaryHandlerRegistration;
      textConsumer = this.textHandlerRegistration;
      this.binaryHandlerRegistration = null;
      this.textHandlerRegistration = null;
      this.closeHandler = null;
      this.exceptionHandler = null;
    }
    if (binaryConsumer != null) {
      binaryConsumer.unregister();
    }
    if (textConsumer != null) {
      textConsumer.unregister();
    }
    if (exceptionHandler != null && !graceful) {
      context.dispatch(HttpUtils.CONNECTION_CLOSED_EXCEPTION, exceptionHandler);
    }
    if (closeHandler != null) {
      context.dispatch(null, closeHandler);
    }
  }

  private void receiveFrame(WebSocketFrameInternal frame) {
    Handler<WebSocketFrameInternal> frameHandler;
    synchronized (conn) {
      frameHandler = this.frameHandler;
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
    }
  }

  /**
   * Called when a close frame is received and the WebSocket.
   */
  protected abstract void handleCloseConnection();

  /**
   * Close the connection.
   */
  void closeConnection() {
    conn.channelHandlerContext().close();
  }

  /**
   * Initiate a timeout that will close the TCP connection.
   *
   * @param timeoutMillis the timeout in milliseconds
   */
  void initiateConnectionCloseTimeout(long timeoutMillis) {
    synchronized (conn) {
      closeTimeoutID = context.setTimer(timeoutMillis, id -> {
        synchronized (conn) {
          closeTimeoutID = -1L;
        }
        closeConnection();
      });
    }
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
      Buffer frameBuffer = Buffer.buffer(frame.getBinaryData());
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
      Buffer frameBuffer = Buffer.buffer(frame.getBinaryData());
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
    synchronized (conn) {
      checkClosed();
      this.frameHandler = (Handler)handler;
      return (S) this;
    }
  }

  @Override
  public WebSocketBase textMessageHandler(Handler<String> handler) {
    synchronized (conn) {
      checkClosed();
      if (frameHandler == null || frameHandler.getClass() != FrameAggregator.class) {
        frameHandler = new FrameAggregator();
      }
      ((FrameAggregator) frameHandler).textMessageHandler = handler;
      return this;
    }
  }

  @Override
  public S binaryMessageHandler(Handler<Buffer> handler) {
    synchronized (conn) {
      checkClosed();
      if (frameHandler == null || frameHandler.getClass() != FrameAggregator.class) {
        frameHandler = new FrameAggregator();
      }
      ((FrameAggregator) frameHandler).binaryMessageHandler = handler;
      return (S) this;
    }
  }

  @Override
  public WebSocketBase pongHandler(Handler<Buffer> handler) {
    synchronized (conn) {
      checkClosed();
      this.pongHandler = handler;
      return (S) this;
    }
  }

  private Handler<Buffer> pongHandler() {
    synchronized (conn) {
      return pongHandler;
    }
  }

  void handleWritabilityChanged(boolean writable) {
    Handler<Void> handler;
    synchronized (conn) {
      boolean skip = this.writable && !writable;
      this.writable = writable;
      handler = drainHandler;
      if (handler == null || skip) {
        return;
      }
    }
    context.dispatch(null, handler);
  }

  void handleException(Throwable t) {
    Handler<Throwable> handler;
    synchronized (conn) {
      handler = this.exceptionHandler;
      if (handler == null) {
        return;
      }
    }
    context.dispatch(t, handler);
  }

  void handleConnectionClosed() {
    synchronized (conn) {
      if (closeTimeoutID != -1L) {
        context.owner().cancelTimer(closeTimeoutID);
      }
      if (closed) {
        return;
      }
      closed = true;
    }
    handleClose(false);
  }

  synchronized void setMetric(Object metric) {
    this.metric = metric;
  }

  synchronized Object getMetric() {
    return metric;
  }

  @Override
  public S handler(Handler<Buffer> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkClosed();
      }
      this.handler = handler;
      return (S) this;
    }
  }

  private Handler<Buffer> handler() {
    synchronized (conn) {
      return handler;
    }
  }

  @Override
  public S endHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkClosed();
      }
      this.endHandler = handler;
      return (S) this;
    }
  }

  private Handler<Void> endHandler() {
    synchronized (conn) {
      return endHandler;
    }
  }

  @Override
  public S exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkClosed();
      }
      this.exceptionHandler = handler;
      return (S) this;
    }
  }

  @Override
  public S closeHandler(Handler<Void> handler) {
    synchronized (conn) {
      checkClosed();
      this.closeHandler = handler;
      return (S) this;
    }
  }

  @Override
  public S drainHandler(Handler<Void> handler) {
    synchronized (conn) {
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
  public S resume() {
    pending.resume();
    return (S) this;
  }

  @Override
  public S fetch(long amount) {
    pending.fetch(amount);
    return (S) this;
  }

  @Override
  public S setWriteQueueMaxSize(int maxSize) {
    synchronized (conn) {
      checkClosed();
      conn.doSetWriteQueueMaxSize(maxSize);
      return (S) this;
    }
  }

  @Override
  public Future<Void> end() {
    return close();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    close(handler);
  }
}
