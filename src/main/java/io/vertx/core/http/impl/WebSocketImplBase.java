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
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.impl.InboundBuffer;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.util.UUID;

/**
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 * <p>
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @param <S> self return type
 */
public abstract class WebSocketImplBase<S extends WebSocketBase> implements WebSocketBase {

  private final boolean supportsContinuation;
  private final String textHandlerID;
  private final String binaryHandlerID;
  private final int maxWebSocketFrameSize;
  private final int maxWebSocketMessageSize;
  private final InboundBuffer<WebSocketFrameInternal> pending;
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
  protected boolean closed;
  private Short closeStatusCode;
  private String closeReason;
  private MultiMap headers;

  WebSocketImplBase(ContextInternal context, Http1xConnectionBase conn, boolean supportsContinuation,
                              int maxWebSocketFrameSize, int maxWebSocketMessageSize) {
    this.supportsContinuation = supportsContinuation;
    this.textHandlerID = "__vertx.ws." + UUID.randomUUID().toString();
    this.binaryHandlerID = "__vertx.ws." + UUID.randomUUID().toString();
    this.conn = conn;
    this.context = context;
    this.maxWebSocketFrameSize = maxWebSocketFrameSize;
    this.maxWebSocketMessageSize = maxWebSocketMessageSize;
    this.pending = new InboundBuffer<>(context);
    this.writable = !conn.isNotWritable();

    pending.handler(this::receiveFrame);
    pending.drainHandler(v -> conn.doResume());
  }

  void registerHandler(EventBus eventBus) {
    Handler<Message<Buffer>> binaryHandler = msg -> writeBinaryFrameInternal(msg.body());
    Handler<Message<String>> textHandler = msg -> writeTextFrameInternal(msg.body());
    binaryHandlerRegistration = eventBus.<Buffer>localConsumer(binaryHandlerID).handler(binaryHandler);
    textHandlerRegistration = eventBus.<String>localConsumer(textHandlerID).handler(textHandler);
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
    Promise<Void> promise = context.promise();
    close(promise);
    return promise.future();
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    close((short) 1000, null, handler);
  }

  @Override
  public Future<Void> close(short statusCode) {
    Promise<Void> promise = context.promise();
    close(statusCode, promise);
    return promise.future();
  }

  @Override
  public void close(short statusCode, Handler<AsyncResult<Void>> handler) {
    this.close(statusCode, null, handler);
  }

  @Override
  public Future<Void> close(short statusCode, String reason) {
    synchronized (conn) {
      if (closed) {
        return context.succeededFuture();
      }
      closed = true;
    }
    unregisterHandlers();
    return conn.closeWithPayload(statusCode, reason);
  }

  @Override
  public void close(short statusCode, @Nullable String reason, Handler<AsyncResult<Void>> handler) {
    close(statusCode, reason).onComplete(handler);
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
    Promise<Void> promise = context.promise();
    writeBinaryMessage(data, promise);
    return promise.future();
  }

  @Override
  public S writeBinaryMessage(Buffer data, Handler<AsyncResult<Void>> handler) {
    synchronized (conn) {
      checkClosed();
      writePartialMessage(FrameType.BINARY, data, 0, handler);
      return (S) this;
    }
  }

  @Override
  public Future<Void> writeTextMessage(String text) {
    Promise<Void> promise = context.promise();
    writeTextMessage(text, promise);
    return promise.future();
  }

  @Override
  public S writeTextMessage(String text, @Nullable Handler<AsyncResult<Void>> handler) {
    synchronized (conn) {
      checkClosed();
      Buffer data = Buffer.buffer(text);
      writePartialMessage(FrameType.TEXT, data, 0, handler);
      return (S) this;
    }
  }

  @Override
  public Future<Void> write(Buffer data) {
    Promise<Void> promise = context.promise();
    write(data, promise);
    return promise.future();
  }

  @Override
  public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
    writeFrame(WebSocketFrame.binaryFrame(data, true), handler);
  }

  @Override
  public Future<Void> writePing(Buffer data) {
    Promise<Void> promise = context.promise();
    writePing(data, promise);
    return promise.future();
  }

  @Override
  public WebSocketBase writePing(Buffer data, Handler<AsyncResult<Void>> handler) {
    if(data.length() > maxWebSocketFrameSize || data.length() > 125) {
      throw new IllegalStateException("Ping cannot exceed maxWebSocketFrameSize or 125 bytes");
    }
    return writeFrame(WebSocketFrame.pingFrame(data), handler);
  }

  @Override
  public Future<Void> writePong(Buffer data) {
    Promise<Void> promise = context.promise();
    writePong(data, promise);
    return promise.future();
  }

  @Override
  public WebSocketBase writePong(Buffer data, Handler<AsyncResult<Void>> handler) {
    if(data.length() > maxWebSocketFrameSize || data.length() > 125) {
      throw new IllegalStateException("Pong cannot exceed maxWebSocketFrameSize or 125 bytes");
    }
    return writeFrame(WebSocketFrame.pongFrame(data), handler);
  }

  /**
   * Splits the provided buffer into multiple frames (which do not exceed the maximum web socket frame size)
   * and writes them in order to the socket.
   */
  private void writePartialMessage(FrameType frameType, Buffer data, int offset, Handler<AsyncResult<Void>> handler) {
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
      writeFrame(frame, handler);
    } else {
      writeFrame(frame);
      writePartialMessage(frameType, data, newOffset, handler);
    }
  }

  private void writeBinaryFrameInternal(Buffer data) {
    ByteBuf buf = data.getByteBuf();
    WebSocketFrame frame = new WebSocketFrameImpl(FrameType.BINARY, buf);
    writeFrame(frame);
  }

  private void writeTextFrameInternal(String str) {
    WebSocketFrame frame = new WebSocketFrameImpl(str);
    writeFrame(frame);
  }

  @Override
  public Future<Void> writeFrame(WebSocketFrame frame) {
    Promise<Void> promise = context.promise();
    writeFrame(frame, promise);
    return promise.future();
  }

  public S writeFrame(WebSocketFrame frame, Handler<AsyncResult<Void>> handler) {
    synchronized (conn) {
      checkClosed();
      conn.writeToChannel(conn.encodeFrame((WebSocketFrameImpl) frame), handler == null ? null : context.promise(handler));
    }
    return (S) this;
  }

  void checkClosed() {
    synchronized (conn) {
      if (closed) {
        throw new IllegalStateException("WebSocket is closed");
      }
    }
  }

  public boolean isClosed() {
    synchronized (conn) {
      return closed;
    }
  }

  void handleFrame(WebSocketFrameInternal frame) {
    if (frame.type() == FrameType.CLOSE) {
      synchronized (conn) {
        closeStatusCode = frame.closeStatusCode();
        closeReason = frame.closeReason();
      }
    }
    if (!pending.write(frame)) {
      conn.doPause();
    }
  }


  private void receiveFrame(WebSocketFrameInternal frame) {
    Handler<WebSocketFrameInternal> frameHandler;
    synchronized (conn) {
      frameHandler = this.frameHandler;
    }
    if (frameHandler != null) {
      context.emit(frame, frameHandler);
    }
    switch(frame.type()) {
      case PONG:
        Handler<Buffer> pongHandler = pongHandler();
        if (pongHandler != null) {
          context.emit(frame.binaryData(), pongHandler);
        }
        break;
      case CLOSE:
        Handler<Void> endHandler = endHandler();
        if (endHandler != null) {
          context.emit(endHandler);
        }
        break;
      case TEXT:
      case BINARY:
      case CONTINUATION:
        Handler<Buffer> handler = handler();
        if (handler != null) {
          context.emit(frame.binaryData(), handler);
        }
        break;
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
    context.emit(null, handler);
  }

  void handleException(Throwable t) {
    Handler<Throwable> handler;
    synchronized (conn) {
      handler = this.exceptionHandler;
      if (handler == null) {
        return;
      }
    }
    context.emit(t, handler);
  }

  void handleClosed() {
    unregisterHandlers();
    Handler<Void> closeHandler;
    synchronized (conn) {
      closeHandler = this.closeHandler;
      closed = true;
      binaryHandlerRegistration = null;
      textHandlerRegistration = null;
    }
    if (closeHandler != null) {
      context.emit(null, closeHandler);
    }
  }

  /**
   * Unregister handlers if they when they are present
   */
  private void unregisterHandlers() {
    MessageConsumer binaryConsumer;
    MessageConsumer textConsumer;
    synchronized (conn) {
      binaryConsumer = this.binaryHandlerRegistration;
      textConsumer = this.textHandlerRegistration;
      binaryHandlerRegistration = null;
      textHandlerRegistration = null;
    }
    if (binaryConsumer != null) {
      binaryConsumer.unregister();
    }
    if (textConsumer != null) {
      textConsumer.unregister();
    }
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
